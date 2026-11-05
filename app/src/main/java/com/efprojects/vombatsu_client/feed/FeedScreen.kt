package com.efprojects.vombatsu_client.feed

import android.net.Uri
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.actions.ActionTarget
import com.efprojects.vombatsu_client.actions.ActionIcon
import com.efprojects.vombatsu_client.actions.FavoriteControl
import com.efprojects.vombatsu_client.actions.LocalActions
import com.efprojects.vombatsu_client.actions.ReactionCounts
import com.efprojects.vombatsu_client.actions.ReactionPicker
import com.efprojects.vombatsu_client.actions.ShareControl
import com.efprojects.vombatsu_client.actions.VoteControl
import com.efprojects.vombatsu_client.actions.rememberReactionControl
import com.efprojects.vombatsu_client.auth.FeedFilters
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.content.PostBlocks
import com.efprojects.vombatsu_client.content.imageUrl
import com.efprojects.vombatsu_client.content.previewBlockCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val POST_VIEW_DELAY_MS = 300L
private val POSTPONED_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy в HH:mm")
private val FEED_TAB_HEIGHT = 40.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    posts: LazyPagingItems<FeedPost>,
    loggedIn: Boolean,
    onOpenPost: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val tabs = FeedType.entries.filter { it != FeedType.Subscriptions || loggedIn }

    LaunchedEffect(loggedIn) {
        if (!loggedIn && selected == FeedType.Subscriptions) viewModel.select(FeedType.Hot)
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
            edgePadding = 8.dp,
        ) {
            tabs.forEach { feed ->
                Tab(
                    selected = feed == selected,
                    onClick = { viewModel.select(feed) },
                    text = { Text(stringResource(feed.title)) },
                    modifier = Modifier.height(FEED_TAB_HEIGHT),
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = posts.loadState.refresh is LoadState.Loading && posts.itemCount > 0,
            onRefresh = posts::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            key(selected) {
                PostList(posts, onOpenPost, bottomPadding = contentPadding.calculateBottomPadding())
            }
        }
    }
}

@Composable
fun FeedFilterMenu(filters: FeedFilters, enabled: Boolean, onChange: (FeedFilters) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.filters))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FilterItem(R.string.filter_nsfw, filters.showNsfw, enabled) {
                onChange(filters.copy(showNsfw = it))
            }
            FilterItem(R.string.filter_politics, filters.showPolitics, enabled) {
                onChange(filters.copy(showPolitics = it))
            }
            FilterItem(R.string.filter_cruel, filters.showCruel, enabled) {
                onChange(filters.copy(showCruel = it))
            }
            FilterItem(R.string.filter_viewed, !filters.hideViewed, enabled) {
                onChange(filters.copy(hideViewed = !it))
            }
        }
    }
}

@Composable
private fun FilterItem(@StringRes label: Int, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        enabled = enabled,
        onClick = { onToggle(!checked) },
    )
}

@Composable
fun PostList(
    posts: LazyPagingItems<FeedPost>,
    onOpenPost: (Int) -> Unit,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)? = null,
) {
    val refresh = posts.loadState.refresh
    if (header == null) {
        when {
            posts.itemCount > 0 -> Unit
            refresh is LoadState.Loading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                return
            }
            refresh is LoadState.Error -> {
                ErrorRetry(posts::retry, Modifier.fillMaxSize())
                return
            }
        }
    }
    val listState = rememberLazyListState()
    TrackViewedPosts(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        header?.let { content -> item(key = "header") { content() } }
        if (posts.itemCount == 0) {
            item {
                when (refresh) {
                    is LoadState.Loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                    is LoadState.Error -> ErrorRetry(posts::retry, Modifier.padding(16.dp))
                    is LoadState.NotLoading -> Text(stringResource(R.string.feed_empty), Modifier.padding(24.dp))
                }
            }
        }
        items(posts.itemCount, key = posts.itemKey { it.id }) { index ->
            posts[index]?.let { post -> PostCard(post, full = false, onOpen = { onOpenPost(post.id) }) }
        }
        when (posts.loadState.append) {
            is LoadState.Loading -> item {
                CircularProgressIndicator(Modifier.padding(16.dp))
            }
            is LoadState.Error -> item {
                ErrorRetry(posts::retry, Modifier.padding(16.dp))
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun TrackViewedPosts(listState: LazyListState) {
    val actions = LocalActions.current
    if (!actions.loggedIn) return
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo
                .filter { item ->
                    val end = item.offset + item.size
                    end > info.viewportStartOffset && end <= info.viewportEndOffset
                }
                .mapNotNull { it.key as? Int }
        }.distinctUntilChanged().collectLatest { ids ->
            if (ids.isNotEmpty()) {
                delay(POST_VIEW_DELAY_MS)
                actions.repository.markViewed(ids)
            }
        }
    }
}

@Composable
private fun ErrorRetry(onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.feed_error))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun PostCard(post: FeedPost, full: Boolean, onOpen: (() -> Unit)?) {
    val actions = LocalActions.current
    val openModifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
    Card(
        Modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PostHeader(post)
            if (post.title.isNotEmpty()) {
                Text(post.title, style = MaterialTheme.typography.titleLarge, modifier = openModifier.fillMaxWidth())
            }
            PostBadges(post)
            PostBody(post, full)
            if (post.tags.isNotEmpty()) PostTags(post.tags)
            val reactions = rememberReactionControl(ActionTarget.Post, post.id, post.reactions, post.myReaction)
            ReactionCounts(reactions)
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoteControl(ActionTarget.Post, post.id, post.rating, post.myVote)
                ReactionPicker(reactions)
                ActionIcon(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    description = stringResource(R.string.comments),
                    label = post.commentsAmount.toString(),
                    onClick = { if (onOpen != null) actions.onOpenComments(post.id) },
                )
                FavoriteControl(post.id, post.isFavorite, post.favoritesAmount)
                post.alias?.let { ShareControl(postUrl(it)) }
            }
        }
    }
}

fun relativeAge(instant: Instant): String = DateUtils.getRelativeTimeSpanString(
    instant.toEpochMilli(),
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()

@Composable
private fun PostHeader(post: FeedPost) {
    val age = remember(post.createdAt) { post.createdAt?.let(::relativeAge) }
    val uriHandler = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { uriHandler.openUri(userUrl(post.login)) },
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            post.avatarPath?.let {
                AsyncImage(model = imageUrl(it, 96), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            post.login,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
        )
        age?.let {
            Text(
                " · $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostBadges(post: FeedPost) {
    val postponed = post.postponedAt?.let { at ->
        stringResource(R.string.badge_postponed_at, POSTPONED_FORMAT.format(at.atZone(ZoneId.systemDefault())))
    } ?: stringResource(R.string.badge_postponed)
    val badges = listOfNotNull(
        postponed.takeIf { post.isPostponed },
        stringResource(R.string.badge_nsfw).takeIf { post.isNsfw },
        stringResource(R.string.badge_cruel).takeIf { post.isCruel },
        stringResource(R.string.badge_politics).takeIf { post.isPolitics },
    )
    if (badges.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.forEach { badge ->
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PostBody(post: FeedPost, full: Boolean) {
    var uncovered by rememberSaveable(post.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(post.id) { mutableStateOf(false) }
    if (post.isCovered && !uncovered) {
        OutlinedButton(onClick = { uncovered = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.show_sensitive))
        }
        return
    }
    val previewCount = remember(post.blocks) { previewBlockCount(post.blocks) }
    val truncated = !full && !expanded && previewCount < post.blocks.size
    PostBlocks(if (truncated) post.blocks.take(previewCount) else post.blocks)
    if (truncated) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.show_full_post), modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostTags(tags: List<String>) {
    val uriHandler = LocalUriHandler.current
    FlowRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            Text(
                "#$tag",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri(tagUrl(tag)) },
            )
        }
    }
}

fun postUrl(alias: String) = "https://vombat.su/post/" + Uri.encode(alias)

fun userUrl(login: String) = "https://vombat.su/user/" + Uri.encode(login)

fun tagUrl(tag: String) = "https://vombat.su/tag/" + Uri.encode(tag)
