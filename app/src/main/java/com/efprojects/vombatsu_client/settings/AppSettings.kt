package com.efprojects.vombatsu_client.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "settings"
private const val KEY_BUGS = "bugs_enabled"
private const val KEY_ATTACK_ALERTS = "attack_alerts_enabled"

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _bugsEnabled = MutableStateFlow(prefs.getBoolean(KEY_BUGS, true))
    val bugsEnabled: StateFlow<Boolean> = _bugsEnabled.asStateFlow()

    fun setBugsEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_BUGS, value) }
        _bugsEnabled.value = value
    }

    private val _attackAlerts = MutableStateFlow(prefs.getBoolean(KEY_ATTACK_ALERTS, false))
    val attackAlerts: StateFlow<Boolean> = _attackAlerts.asStateFlow()

    fun setAttackAlerts(value: Boolean) {
        prefs.edit { putBoolean(KEY_ATTACK_ALERTS, value) }
        _attackAlerts.value = value
    }
}
