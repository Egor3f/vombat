package com.efprojects.vombatsu_client.actions

import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.efprojects.vombatsu_client.R

private val ACTION_HEIGHT = 36.dp
private val ACTION_PADDING = 10.dp

class ActionContext(
    val repository: ActionsRepository,
    val loggedIn: Boolean,
    val onRequireLogin: () -> Unit,
    val onOpenComments: (Int) -> Unit,
)

val LocalActions = staticCompositionLocalOf<ActionContext> { error("ActionContext is not provided") }

private val VoteSaver: Saver<Vote, Any> = listSaver(
    save = { listOf(it.rating, it.myVote) },
    restore = { Vote(it[0], it[1]) },
)

private val ReactionsSaver: Saver<Reactions, Any> = listSaver(
    save = { state -> listOf(state.mine ?: 0) + state.counts.flatMap { listOf(it.code, it.count) } },
    restore = { saved ->
        Reactions(
            counts = saved.drop(1).chunked(2).map { ReactionCount(it[0], it[1]) },
            mine = saved[0].takeIf { it != 0 },
        )
    },
)

@Composable
private fun rememberFailureToast(): () -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        { Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun VoteControl(target: ActionTarget, id: Int, rating: Int, myVote: Int) {
    val actions = LocalActions.current
    val toast = rememberFailureToast()
    var vote by rememberSaveable(id, rating, myVote, stateSaver = VoteSaver) {
        mutableStateOf(Vote(rating, myVote))
    }
    fun press(direction: Int) {
        if (!actions.loggedIn) return actions.onRequireLogin()
        val previous = vote
        val next = applyVote(previous, direction) ?: return
        vote = next
        actions.repository.vote(target, id, next.myVote) {
            vote = previous
            toast()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        VoteArrow(Icons.Filled.ArrowDropUp, R.string.vote_up, active = vote.myVote > 0) { press(1) }
        Text(vote.rating.toString(), style = MaterialTheme.typography.labelLarge)
        VoteArrow(Icons.Filled.ArrowDropDown, R.string.vote_down, active = vote.myVote < 0) { press(-1) }
    }
}

@Composable
private fun VoteArrow(icon: ImageVector, @StringRes description: Int, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Icon(
        icon,
        contentDescription = stringResource(description),
        tint = if (active) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier
            .size(ACTION_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
fun FavoriteControl(postId: Int, isFavorite: Boolean, amount: Int) {
    val actions = LocalActions.current
    val toast = rememberFailureToast()
    var favorite by rememberSaveable(postId, isFavorite) { mutableStateOf(isFavorite) }
    val shownAmount = amount + (if (favorite) 1 else 0) - (if (isFavorite) 1 else 0)
    ActionIcon(
        icon = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
        description = stringResource(R.string.favorites),
        label = shownAmount.takeIf { it > 0 }?.toString(),
        active = favorite,
    ) {
        if (!actions.loggedIn) return@ActionIcon actions.onRequireLogin()
        val add = !favorite
        favorite = add
        actions.repository.favorite(postId, add) {
            favorite = !add
            toast()
        }
    }
}

@Composable
fun ShareControl(url: String) {
    val context = LocalContext.current
    ActionIcon(icon = Icons.Outlined.Share, description = stringResource(R.string.share)) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        context.startActivity(Intent.createChooser(send, null))
    }
}

@Composable
fun SubscribeButton(userId: Int, isSubscribed: Boolean) {
    val actions = LocalActions.current
    val toast = rememberFailureToast()
    var subscribed by rememberSaveable(userId, isSubscribed) { mutableStateOf(isSubscribed) }
    val onClick = {
        if (actions.loggedIn) {
            val add = !subscribed
            subscribed = add
            actions.repository.subscribe(userId, add) {
                subscribed = !add
                toast()
            }
        } else {
            actions.onRequireLogin()
        }
    }
    if (subscribed) {
        OutlinedButton(onClick = onClick) { Text(stringResource(R.string.unsubscribe)) }
    } else {
        Button(onClick = onClick) { Text(stringResource(R.string.subscribe)) }
    }
}

@Composable
fun TagControls(tag: String, isSubscribed: Boolean, isIgnored: Boolean) {
    val actions = LocalActions.current
    val toast = rememberFailureToast()
    var subscribed by rememberSaveable(tag, isSubscribed) { mutableStateOf(isSubscribed) }
    var ignored by rememberSaveable(tag, isIgnored) { mutableStateOf(isIgnored) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = subscribed,
            onClick = {
                if (!actions.loggedIn) return@FilterChip actions.onRequireLogin()
                val add = !subscribed
                subscribed = add
                actions.repository.subscribeTag(tag, add) {
                    subscribed = !add
                    toast()
                }
            },
            label = { Text(stringResource(if (subscribed) R.string.tag_subscribed else R.string.subscribe)) },
        )
        FilterChip(
            selected = ignored,
            onClick = {
                if (!actions.loggedIn) return@FilterChip actions.onRequireLogin()
                val add = !ignored
                ignored = add
                actions.repository.ignoreTag(tag, add) {
                    ignored = !add
                    toast()
                }
            },
            label = { Text(stringResource(if (ignored) R.string.tag_unignore else R.string.tag_ignore)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserIgnoreChips(userId: Int, ignoresPosts: Boolean, ignoresComments: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserIgnoreChip(userId, IgnoreType.Posts, ignoresPosts, R.string.ignore_posts)
        UserIgnoreChip(userId, IgnoreType.Comments, ignoresComments, R.string.ignore_comments)
    }
}

@Composable
private fun UserIgnoreChip(userId: Int, type: IgnoreType, isIgnored: Boolean, @StringRes label: Int) {
    val actions = LocalActions.current
    val toast = rememberFailureToast()
    var ignored by rememberSaveable(userId, type, isIgnored) { mutableStateOf(isIgnored) }
    FilterChip(
        selected = ignored,
        onClick = {
            val add = !ignored
            ignored = add
            actions.repository.ignoreUser(userId, type, add) {
                ignored = !add
                toast()
            }
        },
        label = { Text(stringResource(label)) },
    )
}

class ReactionControl(val reactions: Reactions, val press: (Int) -> Unit)

@Composable
fun rememberReactionControl(target: ActionTarget, id: Int, counts: List<ReactionCount>, mine: Int?): ReactionControl {
    val actions = LocalActions.current
    val toast = rememberFailureToast()
    var state by rememberSaveable(id, counts, mine, stateSaver = ReactionsSaver) {
        mutableStateOf(Reactions(counts, mine))
    }
    fun press(code: Int) {
        if (!actions.loggedIn) return actions.onRequireLogin()
        val previous = state
        state = applyReaction(previous, code)
        actions.repository.react(target, id, code) {
            state = previous
            toast()
        }
    }
    return ReactionControl(state, ::press)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionCounts(control: ReactionControl) {
    if (control.reactions.counts.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        control.reactions.counts.forEach { reaction ->
            ReactionChip(
                label = "${REACTION_EMOJI[reaction.code]} ${reaction.count}",
                active = reaction.code == control.reactions.mine,
            ) { control.press(reaction.code) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPicker(control: ReactionControl) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = ACTION_PADDING)) {
        ReactionChip(label = "☺+", active = false) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowRow(Modifier.widthIn(max = 280.dp).padding(horizontal = 8.dp)) {
                REACTION_EMOJI.forEach { (code, emoji) ->
                    Text(
                        emoji,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                open = false
                                control.press(code)
                            }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) colors.primaryContainer else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun ActionText(label: String, active: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .height(ACTION_HEIGHT)
            .wrapContentHeight()
            .padding(horizontal = ACTION_PADDING),
    )
}

@Composable
fun ActionIcon(
    icon: ImageVector,
    description: String,
    label: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val color = if (active) colors.primary else colors.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .height(ACTION_HEIGHT)
            .padding(horizontal = ACTION_PADDING),
    ) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(18.dp))
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
