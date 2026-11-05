package com.efprojects.vombatsu_client.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.feed.relativeAge
import com.efprojects.vombatsu_client.post.BackButton
import com.efprojects.vombatsu_client.post.Failure
import com.efprojects.vombatsu_client.post.Progress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onOpenPost: (Int, Int?) -> Unit, onBack: () -> Unit) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val readIds by viewModel.readIds.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    TextButton(onClick = { viewModel.markAllRead(items::refresh) }) {
                        Text(stringResource(R.string.notifications_mark_all))
                    }
                },
            )
        },
    ) { padding ->
        val refresh = items.loadState.refresh
        when {
            items.itemCount > 0 -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { item ->
                        NotificationRow(item, unread = !item.isViewed && item.id !in readIds) {
                            viewModel.markRead(item)
                            item.postId?.let { onOpenPost(it, item.commentId) }
                        }
                    }
                }
            }
            refresh is LoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Progress() }
            refresh is LoadState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Failure(R.string.notifications_error, items::retry)
            }
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.notifications_empty))
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, unread: Boolean, onClick: () -> Unit) {
    val age = remember(item.createdAt) { item.createdAt?.let(::relativeAge) }
    val background = if (unread) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val heading = listOfNotNull(item.author, age).joinToString(" · ")
        if (heading.isNotEmpty()) {
            Text(
                heading,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.text.isNotEmpty()) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        item.postTitle?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.preview.isNotEmpty()) {
            Text(
                item.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
