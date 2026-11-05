package com.efprojects.vombatsu_client.ignore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.actions.UserIgnoreChips
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.feed.tagUrl
import com.efprojects.vombatsu_client.feed.userUrl
import com.efprojects.vombatsu_client.post.BackButton
import com.efprojects.vombatsu_client.post.Failure
import com.efprojects.vombatsu_client.post.Loadable
import com.efprojects.vombatsu_client.post.Progress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoreListScreen(viewModel: IgnoreListViewModel, onBack: () -> Unit) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ignore_list)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            section(R.string.ignore_users, users, viewModel::load) { list ->
                items(list, key = { "user-${it.userId}" }) { user ->
                    Column(rowModifier()) {
                        Text(
                            user.login,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable { uriHandler.openUri(userUrl(user.login)) },
                        )
                        UserIgnoreChips(user.userId, user.posts, user.comments)
                    }
                }
            }
            section(R.string.ignore_tags, tags, viewModel::load) { list ->
                items(list, key = { "tag-$it" }) { tag ->
                    Row(rowModifier(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).clickable { uriHandler.openUri(tagUrl(tag)) },
                        )
                        TextButton(
                            onClick = {
                                viewModel.removeTag(tag) {
                                    Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text(stringResource(R.string.ignore_remove)) }
                    }
                }
            }
        }
    }
}

private fun rowModifier() = Modifier
    .widthIn(max = MAX_CONTENT_WIDTH)
    .fillMaxWidth()
    .padding(horizontal = 12.dp, vertical = 4.dp)

private fun <T> LazyListScope.section(
    title: Int,
    state: Loadable<List<T>>,
    onRetry: () -> Unit,
    content: LazyListScope.(List<T>) -> Unit,
) {
    item(key = "title-$title") {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = rowModifier())
    }
    when (state) {
        Loadable.Loading -> item { Progress() }
        Loadable.Error -> item { Failure(R.string.ignore_error, onRetry) }
        is Loadable.Loaded -> if (state.value.isEmpty()) {
            item(key = "empty-$title") {
                Text(
                    stringResource(R.string.ignore_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = rowModifier(),
                )
            }
        } else {
            content(state.value)
        }
    }
}
