package com.efprojects.vombatsu_client.tag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.efprojects.vombatsu_client.actions.TagControls
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.feed.PostList
import com.efprojects.vombatsu_client.post.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(tag: String, viewModel: TagViewModel, onOpenPost: (Int) -> Unit, onBack: () -> Unit) {
    val posts = viewModel.posts.collectAsLazyPagingItems()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("#$tag") }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PostList(
                posts = posts,
                onOpenPost = onOpenPost,
                bottomPadding = padding.calculateBottomPadding(),
                header = {
                    Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(horizontal = 12.dp)) {
                        state?.let { TagControls(tag, it.subscribed, it.ignored) }
                    }
                },
            )
        }
    }
}
