package com.efprojects.vombatsu_client.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.actions.LocalActions
import com.efprojects.vombatsu_client.actions.SubscribeButton
import com.efprojects.vombatsu_client.actions.UserIgnoreChips
import com.efprojects.vombatsu_client.battle.BugFlight
import com.efprojects.vombatsu_client.battle.BugFlightArena
import com.efprojects.vombatsu_client.battle.ThrowResult
import com.efprojects.vombatsu_client.battle.bugLauncher
import com.efprojects.vombatsu_client.battle.bugTarget
import com.efprojects.vombatsu_client.content.Block
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.content.PostBlocks
import com.efprojects.vombatsu_client.content.imageUrl
import com.efprojects.vombatsu_client.content.linkifyPlainText
import com.efprojects.vombatsu_client.feed.PostList
import com.efprojects.vombatsu_client.post.BackButton
import com.efprojects.vombatsu_client.post.Failure
import com.efprojects.vombatsu_client.post.Loadable
import com.efprojects.vombatsu_client.post.Progress
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    title: String,
    ownLogin: String?,
    onOpenPost: (Int) -> Unit,
    onEditProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val ignore by viewModel.ignoreState.collectAsStateWithLifecycle()
    val bugs by viewModel.bugs.collectAsStateWithLifecycle()
    val posts = viewModel.posts.collectAsLazyPagingItems()
    val isOwn = (profile as? Loadable.Loaded)?.value?.login == ownLogin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (isOwn) {
                        TextButton(onClick = onEditProfile) { Text(stringResource(R.string.edit_profile)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val current = profile) {
                Loadable.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Progress() }
                Loadable.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Failure(R.string.profile_error, viewModel::load)
                }
                is Loadable.Loaded -> PostList(
                    posts = posts,
                    onOpenPost = onOpenPost,
                    bottomPadding = padding.calculateBottomPadding(),
                    header = { ProfileHeader(current.value, ignore, isOwn, bugs, viewModel::throwBug) },
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: Profile,
    ignore: ProfileIgnore?,
    isOwn: Boolean,
    bugs: Int?,
    onThrowBug: (Int, (ThrowResult) -> Unit) -> Unit,
) {
    val flight = remember { BugFlight() }
    BugFlightArena(
        flight = flight,
        modifier = Modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .bugTarget(flight)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    profile.avatarPath?.let {
                        AsyncImage(
                            model = imageUrl(it, 192),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(profile.login, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.profile_follow_stats, profile.subscribers, profile.subscriptions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.profile_stats, profile.rating, profile.postsAmount, profile.commentsAmount),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (profile.about.isNotBlank()) {
                PostBlocks(listOf(Block.Paragraph(linkifyPlainText(profile.about.trim()))))
            }
            if (!isOwn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubscribeButton(profile.id, profile.isSubscribed)
                    ThrowBugButton(flight, bugs) { onResult -> onThrowBug(profile.id, onResult) }
                }
                ignore?.let { UserIgnoreChips(profile.id, it.posts, it.comments) }
            }
        }
    }
}

@Composable
private fun ThrowBugButton(flight: BugFlight, bugs: Int?, onThrow: ((ThrowResult) -> Unit) -> Unit) {
    val actions = LocalActions.current
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var throwing by remember { mutableStateOf(false) }
    OutlinedButton(
        enabled = !actions.loggedIn || (!throwing && !flight.flying && (bugs ?: 0) > 0),
        modifier = Modifier.bugLauncher(flight),
        onClick = {
            if (!actions.loggedIn) return@OutlinedButton actions.onRequireLogin()
            throwing = true
            scope.launch { flight.fly() }
            onThrow { result ->
                throwing = false
                val message = when (result) {
                    is ThrowResult.Hit -> result.message ?: context.getString(R.string.bug_thrown)
                    is ThrowResult.Rejected -> result.message ?: context.getString(R.string.action_failed)
                    ThrowResult.NetworkError -> context.getString(R.string.action_failed)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        Text(bugs?.let { stringResource(R.string.throw_bug_count, it) } ?: stringResource(R.string.throw_bug))
    }
}
