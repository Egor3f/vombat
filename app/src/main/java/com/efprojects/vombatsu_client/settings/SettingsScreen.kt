package com.efprojects.vombatsu_client.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.battle.AttackAlerts
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.post.BackButton

private val SETTINGS_PADDING = 16.dp
private val SETTINGS_ROW_PADDING = 12.dp
// Material buttons are 40dp tall inside a 48dp touch target, so each has an invisible 4dp margin.
private val BUTTON_TOUCH_INSET = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, attackAlerts: AttackAlerts, onBack: () -> Unit) {
    val bugsEnabled by settings.bugsEnabled.collectAsStateWithLifecycle()
    val alertsEnabled by settings.attackAlerts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            settings.setAttackAlerts(true)
        } else {
            Toast.makeText(context, R.string.notifications_denied, Toast.LENGTH_LONG).show()
        }
    }
    val onAlertsChange = { enable: Boolean ->
        val needsPermission = enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else settings.setAttackAlerts(enable)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()) {
                SettingSwitch(R.string.settings_bugs, R.string.settings_bugs_hint, bugsEnabled, settings::setBugsEnabled)
                SettingSwitch(
                    R.string.settings_attack_alerts,
                    R.string.settings_attack_alerts_hint,
                    bugsEnabled && alertsEnabled,
                    onAlertsChange,
                    enabled = bugsEnabled,
                )
                if (bugsEnabled && alertsEnabled) AttackAlertsExtras(attackAlerts)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    @StringRes title: Int,
    @StringRes hint: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = SETTINGS_PADDING, vertical = SETTINGS_ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = SETTINGS_PADDING)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun AttackAlertsExtras(attackAlerts: AttackAlerts) {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(attackAlerts.batteryUnrestricted) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = attackAlerts.batteryUnrestricted }
    Column(
        modifier = Modifier.padding(start = SETTINGS_PADDING, end = SETTINGS_PADDING, bottom = SETTINGS_ROW_PADDING),
        verticalArrangement = Arrangement.spacedBy(SETTINGS_ROW_PADDING - BUTTON_TOUCH_INSET),
    ) {
        OutlinedButton(onClick = { context.startActivity(attackAlerts.soundSettingsIntent()) }) {
            Text(stringResource(R.string.attack_sound_settings))
        }
        OutlinedButton(
            enabled = !unrestricted,
            onClick = { context.startActivity(attackAlerts.batteryUnrestrictIntent()) },
        ) {
            Text(stringResource(if (unrestricted) R.string.battery_unrestricted else R.string.battery_unrestrict))
        }
        if (!unrestricted) {
            Text(
                stringResource(R.string.battery_unrestrict_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
