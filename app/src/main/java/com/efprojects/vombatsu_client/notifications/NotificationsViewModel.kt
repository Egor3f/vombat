package com.efprojects.vombatsu_client.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.content.parseInlineHtml
import com.efprojects.vombatsu_client.feed.parseServerDate
import com.efprojects.vombatsu_client.graphql.NotificationsQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

data class NotificationItem(
    val id: Int,
    val text: String,
    val preview: String,
    val author: String?,
    val postId: Int?,
    val commentId: Int?,
    val postTitle: String?,
    val createdAt: Instant?,
    val isViewed: Boolean,
)

private fun plainText(html: String?) = parseInlineHtml(html.orEmpty()).joinToString("") { it.text }.trim()

private fun NotificationsQuery.Data1.toItem(): NotificationItem? {
    val notificationId = id ?: return null
    return NotificationItem(
        id = notificationId,
        text = plainText(text),
        preview = plainText(comment?.content),
        author = comment?.user?.login ?: comment?.login,
        postId = post?.id ?: comment?.post_id,
        commentId = comment?.id,
        postTitle = post?.title,
        createdAt = parseServerDate(created_at),
        isViewed = is_viewed == true,
    )
}

private class NotificationsPagingSource(private val apollo: ApolloClient) : PagingSource<Int, NotificationItem>() {
    private val seenIds = mutableSetOf<Int>()

    override fun getRefreshKey(state: PagingState<Int, NotificationItem>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, NotificationItem> {
        val page = params.key ?: 1
        val response = apollo.query(NotificationsQuery(Optional.present(page))).execute()
        response.exception?.let { return LoadResult.Error(it) }
        val result = response.data?.notifications
            ?: return LoadResult.Error(IOException(response.errors?.firstOrNull()?.message))
        return LoadResult.Page(
            data = result.data.mapNotNull { it.toItem() }.filter { seenIds.add(it.id) },
            prevKey = null,
            nextKey = if (result.has_more_pages) page + 1 else null,
        )
    }
}

class NotificationsViewModel(
    apollo: ApolloClient,
    private val repository: NotificationsRepository,
) : ViewModel() {
    val items: Flow<PagingData<NotificationItem>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) { NotificationsPagingSource(apollo) }
            .flow
            .cachedIn(viewModelScope)

    private val _readIds = MutableStateFlow(emptySet<Int>())
    val readIds: StateFlow<Set<Int>> = _readIds.asStateFlow()

    fun markRead(item: NotificationItem) {
        if (item.isViewed || item.id in _readIds.value) return
        _readIds.update { it + item.id }
        viewModelScope.launch {
            if (!repository.markRead(item.id)) _readIds.update { it - item.id }
        }
    }

    fun markAllRead(onDone: () -> Unit) {
        viewModelScope.launch {
            if (repository.markAllRead()) onDone()
        }
    }
}
