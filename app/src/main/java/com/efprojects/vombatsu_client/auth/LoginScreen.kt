package com.efprojects.vombatsu_client.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.data.PersistentCookieJar
import com.efprojects.vombatsu_client.web.WebPageScreen

private const val SITE_URL = "https://vombat.su/"

@Composable
fun LoginScreen(viewModel: LoginViewModel, cookies: PersistentCookieJar, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    BackHandler(enabled = state.oauth != null, onBack = viewModel::cancelOAuth)
    state.oauth?.let { provider ->
        WebPageScreen(
            url = provider.authorizeUrl,
            title = provider.title,
            cookies = cookies,
            onBack = viewModel::cancelOAuth,
            onNavigate = { url ->
                val params = oauthCallbackParams(provider, url)
                if (params != null) viewModel.finishOAuth(provider, params)
                params != null
            },
        )
        return
    }
    var askRegistration by remember { mutableStateOf(false) }
    if (askRegistration) {
        RegistrationDialog(onDismiss = { askRegistration = false })
    }
    Surface(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = state.login,
                    onValueChange = viewModel::onLogin,
                    label = { Text(stringResource(R.string.login_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username },
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                    label = { Text(stringResource(R.string.password_field)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.needsCode) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                )
                if (state.needsCode) {
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = viewModel::onCode,
                        label = { Text(stringResource(R.string.two_factor_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.error?.let { error ->
                    Text(
                        text = when (error) {
                            LoginError.Network -> stringResource(R.string.login_network_error)
                            LoginError.SessionLost -> stringResource(R.string.login_session_lost)
                            is LoginError.Rejected -> error.message ?: stringResource(R.string.login_rejected)
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.loading && state.login.isNotBlank() && state.password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.sign_in))
                    }
                }
                OAuthProvider.entries.forEach { provider ->
                    OutlinedButton(
                        onClick = { viewModel.startOAuth(provider) },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(provider.title)) }
                }
                TextButton(
                    onClick = { askRegistration = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.register)) }
            }
        }
    }
}

@Composable
private fun RegistrationDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.register)) },
        text = { Text(stringResource(R.string.register_unavailable)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    uriHandler.openUri(SITE_URL)
                },
            ) { Text(stringResource(R.string.open_site)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reply_cancel)) }
        },
    )
}
