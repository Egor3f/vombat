package com.efprojects.vombatsu_client.battle

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.graphql.BattleStateQuery
import com.efprojects.vombatsu_client.graphql.UseToolMutation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val BUG_TOOL = "bug"

sealed interface ThrowResult {
    data class Hit(val message: String?) : ThrowResult
    data class Rejected(val message: String?) : ThrowResult
    data object NetworkError : ThrowResult
}

class BattleRepository(private val apollo: ApolloClient) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _bugs = MutableStateFlow<Int?>(null)
    val bugs: StateFlow<Int?> = _bugs.asStateFlow()

    suspend fun refresh() {
        val state = apollo.query(BattleStateQuery()).execute().data?.gameState ?: return
        _unread.value = state.notifications_count ?: 0
        _bugs.value = state.tools?.attack.orEmpty()
            .firstOrNull { it?.tool?.identifier == BUG_TOOL }
            ?.total_available_count
    }

    fun clear() {
        _unread.value = 0
        _bugs.value = null
    }

    suspend fun throwBug(userId: Int): ThrowResult {
        val response = apollo.mutation(UseToolMutation(BUG_TOOL, Optional.present(userId))).execute()
        val result = response.data?.useTool
        return when {
            result == null && response.exception != null -> ThrowResult.NetworkError
            result == null -> ThrowResult.Rejected(response.errors?.firstOrNull()?.message)
            result.interactions.isNullOrEmpty() -> ThrowResult.Rejected(result.message)
            else -> {
                refresh()
                ThrowResult.Hit(result.message)
            }
        }
    }
}
