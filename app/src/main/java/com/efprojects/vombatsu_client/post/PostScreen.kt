package com.efprojects.vombatsu_client.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.actions.ActionTarget
import com.efprojects.vombatsu_client.actions.AttachMenu
import com.efprojects.vombatsu_client.actions.ActionText
import com.efprojects.vombatsu_client.actions.LocalActions
import com.efprojects.vombatsu_client.actions.ReactionCounts
import com.efprojects.vombatsu_client.actions.ReactionPicker
import com.efprojects.vombatsu_client.actions.VoteControl
import com.efprojects.vombatsu_client.actions.rememberReactionControl
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.content.Media
import com.efprojects.vombatsu_client.content.PostBlocks
import com.efprojects.vombatsu_client.content.imageUrl
import com.efprojects.vombatsu_client.content.parseMediaList
import com.efprojects.vombatsu_client.feed.PostCard
import com.efprojects.vombatsu_client.feed.relativeAge
import com.efprojects.vombatsu_client.feed.userUrl

private const val MAX_INDENT_LEVELS = 6
private const val MAX_ATTACHMENTS_PER_PICK = 5
private const val COMMENTS_TITLE_INDEX = 1
private val INDENT_STEP = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(viewModel: PostViewModel, loggedIn: Boolean, onSignIn: () -> Unit, onBack: () -> Unit) {
    val post by viewModel.post.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsed.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(loggedIn) {
        if (loggedIn) viewModel.markViewed()
    }
    LaunchedEffect(target, comments, post) {
        val current = target ?: return@LaunchedEffect
        val nodes = (comments as? Loadable.Loaded)?.value ?: return@LaunchedEffect
        if (post == Loadable.Loading) return@LaunchedEffect
        val offset = when (current) {
            PostTarget.Comments -> 0
            is PostTarget.Comment -> 1 + visibleComments(nodes, collapsed).indexOfFirst { it.comment.id == current.id }
        }
        if (offset >= 0) listState.scrollToItem(COMMENTS_TITLE_INDEX + offset)
        viewModel.onTargetReached()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.post_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            if (loggedIn) {
                CommentComposer(composer, viewModel)
            } else {
                Surface(tonalElevation = 2.dp) {
                    TextButton(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    ) { Text(stringResource(R.string.comment_sign_in)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = post) {
                Loadable.Loading -> item { Progress() }
                Loadable.Error -> item { Failure(R.string.post_error, viewModel::loadPost) }
                is Loadable.Loaded -> item(key = "post") { PostCard(current.value, full = true, onOpen = null) }
            }
            commentsSection(comments, collapsed, viewModel)
        }
    }
}

private fun LazyListScope.commentsSection(
    comments: Loadable<List<CommentNode>>,
    collapsed: Set<Int>,
    viewModel: PostViewModel,
) {
    when (comments) {
        Loadable.Loading -> item { Progress() }
        Loadable.Error -> item { Failure(R.string.comments_error, viewModel::loadComments) }
        is Loadable.Loaded -> {
            val nodes = comments.value
            item(key = "comments-title") {
                Text(
                    if (nodes.isEmpty()) {
                        stringResource(R.string.comments_empty)
                    } else {
                        stringResource(R.string.comments_title, nodes.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .widthIn(max = MAX_CONTENT_WIDTH)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            items(visibleComments(nodes, collapsed), key = { it.comment.id }) { node ->
                CommentRow(
                    node = node,
                    collapsed = node.comment.id in collapsed,
                    onToggle = { viewModel.toggle(node.comment.id) },
                    onReply = { viewModel.replyTo(node.comment) },
                    onEdit = { viewModel.startEditing(node.comment) },
                    onDelete = { viewModel.delete(node.comment) },
                )
            }
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

@Composable
fun Progress() {
    CircularProgressIndicator(Modifier.padding(16.dp))
}

@Composable
fun Failure(message: Int, onRetry: () -> Unit) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(message))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun CommentRow(
    node: CommentNode,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val levels = node.depth.coerceAtMost(MAX_INDENT_LEVELS)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .drawBehind {
                repeat(levels) { level ->
                    val x = (12.dp + INDENT_STEP * level).toPx()
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
            }
            .padding(
                start = 12.dp + INDENT_STEP * levels + (if (levels > 0) 6.dp else 0.dp),
                end = 12.dp,
                bottom = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CommentHeader(node, collapsed, onToggle)
        if (!collapsed) {
            PostBlocks(node.comment.blocks)
            if (node.comment.author != null) CommentActions(node.comment, onReply, onEdit, onDelete)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentActions(comment: Comment, onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val reactions = rememberReactionControl(ActionTarget.Comment, comment.id, comment.reactions, comment.myReaction)
    ReactionCounts(reactions)
    FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
        VoteControl(ActionTarget.Comment, comment.id, comment.rating, comment.myVote)
        ReactionPicker(reactions)
        val actions = LocalActions.current
        ActionText(stringResource(R.string.reply)) {
            if (actions.loggedIn) onReply() else actions.onRequireLogin()
        }
        if (comment.isEditable) {
            ActionText(stringResource(R.string.comment_edit), onClick = onEdit)
            ActionText(stringResource(R.string.comment_delete)) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.comment_delete_title)) },
            text = { Text(stringResource(R.string.comment_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.comment_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.reply_cancel)) }
            },
        )
    }
}

@Composable
private fun CommentComposer(composer: Composer, viewModel: PostViewModel) {
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            composer.replyTo?.let { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.reply_to, target.author.orEmpty()),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.replyTo(null) }) {
                        Text(stringResource(R.string.reply_cancel))
                    }
                }
            }
            composer.editing?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.comment_editing),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::cancelEditing) {
                        Text(stringResource(R.string.reply_cancel))
                    }
                }
            }
            if (composer.attachments.isNotEmpty() || composer.uploading > 0) {
                ComposerAttachments(composer, viewModel::removeAttachment)
            }
            composer.error?.let { error ->
                Text(
                    when (error) {
                        ComposerError.Network -> stringResource(R.string.comment_send_network_error)
                        ComposerError.UploadNetwork -> stringResource(R.string.upload_network_error)
                        is ComposerError.Upload -> error.message ?: stringResource(R.string.upload_failed)
                        is ComposerError.Rejected -> error.message ?: stringResource(R.string.comment_send_failed)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttachMenu(MAX_ATTACHMENTS_PER_PICK, viewModel::addMedia, viewModel::addImageUrl) { open ->
                    IconButton(onClick = open) {
                        Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.comment_attach))
                    }
                }
                OutlinedTextField(
                    value = composer.text,
                    onValueChange = viewModel::onComposerText,
                    placeholder = { Text(stringResource(R.string.comment_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    maxLines = 5,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = viewModel::sendComment,
                    enabled = composer.canSend,
                ) {
                    Text(stringResource(if (composer.editing != null) R.string.comment_save else R.string.comment_send))
                }
            }
        }
    }
}

@Composable
private fun ComposerAttachments(composer: Composer, onRemove: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        composer.attachments.forEachIndexed { index, raw ->
            val media = remember(raw) { parseMediaList("[$raw]").firstOrNull() }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val preview = when (media) {
                    is Media.Image -> imageUrl(media.path, 192)
                    is Media.Video -> media.thumbnailUrl
                    null -> null
                }
                if (preview != null) {
                    AsyncImage(
                        model = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    stringResource(R.string.comment_attachment_remove),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemove(index) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (composer.uploading > 0) {
            Text(
                stringResource(R.string.comment_uploading, composer.uploading),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CommentHeader(node: CommentNode, collapsed: Boolean, onToggle: () -> Unit) {
    val comment = node.comment
    val age = remember(comment.createdAt) { comment.createdAt?.let(::relativeAge) }
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = comment.author != null) {
                    comment.author?.let { uriHandler.openUri(userUrl(it)) }
                },
        ) {
            comment.avatarPath?.let {
                AsyncImage(model = imageUrl(it, 72), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            comment.author ?: stringResource(R.string.comment_removed_author),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
        )
        Text(
            age?.let { " · $it" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (collapsed) {
            Text(
                " · " + stringResource(R.string.comment_collapsed, node.descendants + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}
