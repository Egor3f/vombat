package com.efprojects.vombatsu_client.battle

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.efprojects.vombatsu_client.MainActivity
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.VombatApp
import com.efprojects.vombatsu_client.graphql.IncomingAttacksQuery
import com.efprojects.vombatsu_client.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

const val BATTLE_DEEP_LINK = "vombapp://battle"

private const val POLL_INTERVAL_MS = 2 * 60_000L
// goAsync() gives a receiver about 10 seconds before the system kills it.
private const val CHECK_TIMEOUT_MS = 9_000L
private const val CHANNEL_ID = "attacks"
private const val NOTIFICATION_TAG = "attack"
private const val PREFS_NAME = "attack_alerts"
private const val KEY_NOTIFIED = "notified"

class AttackAlerts(
    private val context: Context,
    private val apollo: ApolloClient,
    private val settings: AppSettings,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val notifications = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val enabled get() = settings.bugsEnabled.value && settings.attackAlerts.value

    fun start() {
        scope.launch {
            combine(settings.bugsEnabled, settings.attackAlerts) { bugs, alerts -> bugs && alerts }
                .distinctUntilChanged()
                .collect { sync() }
        }
    }

    private fun sync() {
        if (enabled) {
            createChannel()
            schedule(0)
        } else {
            alarms.cancel(alarmIntent())
        }
    }

    fun scheduleNext() = schedule(POLL_INTERVAL_MS)

    val batteryUnrestricted
        get() = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun soundSettingsIntent(): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)

    @SuppressLint("BatteryLife")
    fun batteryUnrestrictIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    suspend fun check() {
        val data = apollo.query(IncomingAttacksQuery()).execute().data ?: return
        val now = Instant.now()
        val attacks = data.incomingAttacks(now)
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().mapNotNull(String::toIntOrNull).toSet()
        val current = attacks.map { it.id }.toSet()
        (notified - current).forEach { notifications.cancel(NOTIFICATION_TAG, it) }
        attacks.filter { it.id !in notified }.forEach { show(it, now) }
        prefs.edit { putStringSet(KEY_NOTIFIED, current.map(Int::toString).toSet()) }
    }

    private fun schedule(delayMs: Long) {
        val at = SystemClock.elapsedRealtime() + delayMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent())
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent())
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AttackAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openBattleIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(Intent.ACTION_VIEW, BATTLE_DEEP_LINK.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.attack_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun show(attack: IncomingAttack, now: Instant) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel()
        val text = attack.message ?: context.getString(R.string.attack_fallback, attack.attacker, attack.toolName)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bug)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.bug_large))
            .setContentTitle(attack.toolName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(attack.expiresAt.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(Duration.between(now, attack.expiresAt).toMillis())
            .setAutoCancel(true)
            .setContentIntent(openBattleIntent())
            .build()
        notifications.notify(NOTIFICATION_TAG, attack.id, notification)
    }
}

class AttackAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alerts = (context.applicationContext as VombatApp).graph.attackAlerts
        if (!alerts.enabled) return
        alerts.scheduleNext()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(CHECK_TIMEOUT_MS) { alerts.check() }
            } finally {
                pending.finish()
            }
        }
    }
}

// Exists only to start the process after boot or update: VombatApp.onCreate then reschedules the alarms.
class AttackAlertsRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
