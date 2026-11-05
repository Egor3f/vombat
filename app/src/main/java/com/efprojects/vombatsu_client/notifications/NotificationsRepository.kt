package com.efprojects.vombatsu_client.notifications

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.graphql.MarkNotificationsMutation
import com.efprojects.vombatsu_client.graphql.NotificationsCountQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ALL_TYPES = -1

class NotificationsRepository(private val apollo: ApolloClient) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    suspend fun refreshCount() {
        val counts = apollo.query(NotificationsCountQuery()).execute().data?.notificationsCount ?: return
        _unread.value = counts.sumOf { it?.count ?: 0 }
    }

    fun clearCount() {
        _unread.value = 0
    }

    suspend fun markRead(id: Int): Boolean = mark(MarkNotificationsMutation(ids = Optional.present(listOf(id))))

    suspend fun markAllRead(): Boolean = mark(MarkNotificationsMutation(allByType = Optional.present(ALL_TYPES)))

    private suspend fun mark(mutation: MarkNotificationsMutation): Boolean {
        val saved = apollo.mutation(mutation).execute().data?.notification?.response_status == "ok"
        if (saved) refreshCount()
        return saved
    }
}
