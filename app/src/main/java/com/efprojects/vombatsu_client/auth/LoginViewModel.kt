package com.efprojects.vombatsu_client.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginError {
    data object Network : LoginError
    data object SessionLost : LoginError
    data class Rejected(val message: String?) : LoginError
}

data class LoginState(
    val login: String = "",
    val password: String = "",
    val code: String = "",
    val needsCode: Boolean = false,
    val loading: Boolean = false,
    val error: LoginError? = null,
    val done: Boolean = false,
    val oauth: OAuthProvider? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onLogin(value: String) = _state.update { it.copy(login = value) }
    fun onPassword(value: String) = _state.update { it.copy(password = value) }
    fun onCode(value: String) = _state.update { it.copy(code = value) }

    fun startOAuth(provider: OAuthProvider) = _state.update { it.copy(oauth = provider, error = null) }
    fun cancelOAuth() = _state.update { it.copy(oauth = null) }

    fun finishOAuth(provider: OAuthProvider, params: String) {
        _state.update { it.copy(oauth = null) }
        attempt { auth.oauthLogin(provider, params) }
    }

    fun submit() {
        val current = _state.value
        attempt { auth.login(current.login.trim(), current.password, current.code.trim()) }
    }

    private fun attempt(request: suspend () -> LoginResult) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = request()
            _state.update {
                when (result) {
                    LoginResult.Success -> it.copy(loading = false, password = "", done = true)
                    LoginResult.TwoFactorRequired -> it.copy(loading = false, needsCode = true)
                    LoginResult.NetworkError -> it.copy(loading = false, error = LoginError.Network)
                    LoginResult.SessionLost -> it.copy(loading = false, error = LoginError.SessionLost)
                    is LoginResult.Rejected -> it.copy(loading = false, error = LoginError.Rejected(result.message))
                }
            }
        }
    }
}
