package com.efprojects.vombatsu_client.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.content.imageUrl
import com.efprojects.vombatsu_client.feed.PostList
import com.efprojects.vombatsu_client.feed.tagUrl
import com.efprojects.vombatsu_client.feed.userUrl
import com.efprojects.vombatsu_client.post.BackButton
import com.efprojects.vombatsu_client.post.Loadable
import com.efprojects.vombatsu_client.post.Progress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel, onOpenPost: (Int) -> Unit, onBack: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (viewModel.query.value.isEmpty()) focus.requestFocus()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                SearchTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { viewModel.select(entry) },
                        text = { Text(stringResource(entry.title)) },
                    )
                }
            }
            when (tab) {
                SearchTab.Posts -> PostList(
                    posts = viewModel.posts.collectAsLazyPagingItems(),
                    onOpenPost = onOpenPost,
                    bottomPadding = padding.calculateBottomPadding(),
                )
                SearchTab.Users -> UsersResult(viewModel, query)
                SearchTab.Tags -> TagsResult(viewModel, query)
            }
        }
    }
}

@Composable
private fun UsersResult(viewModel: SearchViewModel, query: String) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    SearchRows(users, query) { user ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(userUrl(user.login)) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                user.avatarPath?.let {
                    AsyncImage(model = imageUrl(it, 108), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                user.login,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            Text(user.rating, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TagsResult(viewModel: SearchViewModel, query: String) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    SearchRows(tags, query) { tag ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(tagUrl(tag.tag)) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text("#${tag.tag}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(tag.count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun <T> SearchRows(state: Loadable<List<T>>, query: String, row: @Composable (T) -> Unit) {
    when (state) {
        Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Progress() }
        Loadable.Error -> Text(stringResource(R.string.search_error), Modifier.padding(24.dp))
        is Loadable.Loaded -> if (state.value.isEmpty()) {
            if (query.isNotBlank()) Text(stringResource(R.string.search_empty), Modifier.padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(state.value) { row(it) }
            }
        }
    }
}
