package com.efprojects.vombatsu_client.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.post.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val bugsEnabled by settings.bugsEnabled.collectAsStateWithLifecycle()
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
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_bugs)) },
                    supportingContent = { Text(stringResource(R.string.settings_bugs_hint)) },
                    trailingContent = { Switch(checked = bugsEnabled, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = bugsEnabled,
                        role = Role.Switch,
                        onValueChange = settings::setBugsEnabled,
                    ),
                )
            }
        }
    }
}
