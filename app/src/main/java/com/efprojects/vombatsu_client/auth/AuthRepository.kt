package com.efprojects.vombatsu_client.auth

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.data.PersistentCookieJar
import com.efprojects.vombatsu_client.graphql.LoginMutation
import com.efprojects.vombatsu_client.graphql.LogoutMutation
import com.efprojects.vombatsu_client.graphql.OAuthLoginMutation
import com.efprojects.vombatsu_client.graphql.MeQuery
import com.efprojects.vombatsu_client.graphql.SaveSettingsMutation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Session {
    data object Unknown : Session
    data object Anonymous : Session
    data class LoggedIn(val login: String, val filters: FeedFilters) : Session
}

sealed interface LoginResult {
    data object Success : LoginResult
    data object TwoFactorRequired : LoginResult
    data object NetworkError : LoginResult
    data object SessionLost : LoginResult
    data class Rejected(val message: String?) : LoginResult
}

class AuthRepository(
    private val apollo: ApolloClient,
    private val cookies: PersistentCookieJar,
) {
    private val _session = MutableStateFlow<Session>(Session.Unknown)
    val session: StateFlow<Session> = _session.asStateFlow()

    suspend fun refresh() {
        val response = apollo.query(MeQuery()).execute()
        if (response.exception != null) return
        val me = response.data?.me
        val login = me?.login
        _session.value = if (login != null) {
            Session.LoggedIn(login, parseFeedFilters(me.settings))
        } else {
            Session.Anonymous
        }
    }

    suspend fun login(login: String, password: String, code: String): LoginResult {
        val response = apollo
            .mutation(LoginMutation(login, password, Optional.present(code)))
            .execute()
        if (response.exception != null) return LoginResult.NetworkError
        val auth = response.data?.auth
            ?: return LoginResult.Rejected(response.errors?.firstOrNull()?.message)
        if (auth.response_status != "ok") return LoginResult.Rejected(auth.error_message)
        if (auth.two_factor_auth_is_needed == true) return LoginResult.TwoFactorRequired
        return confirmSession()
    }

    suspend fun oauthLogin(provider: OAuthProvider, params: String): LoginResult {
        val response = apollo
            .mutation(OAuthLoginMutation(Optional.present(provider.value), Optional.present(params)))
            .execute()
        if (response.exception != null) return LoginResult.NetworkError
        val auth = response.data?.oAuth
            ?: return LoginResult.Rejected(response.errors?.firstOrNull()?.message)
        if (auth.response_status != "ok") return LoginResult.Rejected(auth.error_message)
        return confirmSession()
    }

    private suspend fun confirmSession(): LoginResult {
        refresh()
        return if (_session.value is Session.LoggedIn) LoginResult.Success else LoginResult.SessionLost
    }

    suspend fun saveFilters(filters: FeedFilters): Boolean {
        val current = _session.value as? Session.LoggedIn ?: return false
        val changes = feedFiltersDiff(current.filters, filters)
        val response = apollo.mutation(SaveSettingsMutation(Optional.present(changes))).execute()
        val saved = response.data?.userSettings == true
        if (saved) _session.value = current.copy(filters = filters)
        return saved
    }

    suspend fun logout() {
        apollo.mutation(LogoutMutation()).execute()
        cookies.clear()
        _session.value = Session.Anonymous
    }
}
