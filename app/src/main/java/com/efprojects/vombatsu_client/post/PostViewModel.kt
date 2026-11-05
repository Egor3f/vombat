package com.efprojects.vombatsu_client.post

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.ApolloClient
import com.efprojects.vombatsu_client.actions.ActionsRepository
import com.efprojects.vombatsu_client.actions.MediaUploader
import com.efprojects.vombatsu_client.actions.SendCommentResult
import com.efprojects.vombatsu_client.actions.UploadField
import com.efprojects.vombatsu_client.actions.UploadResult
import com.efprojects.vombatsu_client.actions.blocksToPlainText
import com.efprojects.vombatsu_client.actions.parseReactions
import com.efprojects.vombatsu_client.content.Block
import com.efprojects.vombatsu_client.content.parseCommentHtml
import com.efprojects.vombatsu_client.content.parseMediaList
import com.efprojects.vombatsu_client.content.rawAttachments
import com.efprojects.vombatsu_client.feed.FeedPost
import com.efprojects.vombatsu_client.feed.parseServerDate
import com.efprojects.vombatsu_client.feed.toFeedPost
import com.efprojects.vombatsu_client.graphql.CommentsQuery
import com.efprojects.vombatsu_client.graphql.PostQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data object Error : Loadable<Nothing>
    data class Loaded<T>(val value: T) : Loadable<T>
}

sealed interface PostTarget {
    data object Comments : PostTarget
    data class Comment(val id: Int) : PostTarget
}

sealed interface ComposerError {
    data object Network : ComposerError
    data object UploadNetwork : ComposerError
    data class Upload(val message: String?) : ComposerError
    data class Rejected(val message: String?) : ComposerError
}

data class Composer(
    val text: String = "",
    val replyTo: Comment? = null,
    val editing: Comment? = null,
    val attachments: List<String> = emptyList(),
    val uploading: Int = 0,
    val sending: Boolean = false,
    val error: ComposerError? = null,
) {
    val canSend get() = !sending && uploading == 0 && (text.isNotBlank() || attachments.isNotEmpty())
}

class PostViewModel(
    private val apollo: ApolloClient,
    private val actions: ActionsRepository,
    private val uploader: MediaUploader,
    private val postId: Int,
    target: PostTarget?,
) : ViewModel() {
    private val _post = MutableStateFlow<Loadable<FeedPost>>(Loadable.Loading)
    val post: StateFlow<Loadable<FeedPost>> = _post.asStateFlow()

    private val _comments = MutableStateFlow<Loadable<List<CommentNode>>>(Loadable.Loading)
    val comments: StateFlow<Loadable<List<CommentNode>>> = _comments.asStateFlow()

    private val _collapsed = MutableStateFlow(emptySet<Int>())
    val collapsed: StateFlow<Set<Int>> = _collapsed.asStateFlow()

    private val _target = MutableStateFlow(target)
    val target: StateFlow<PostTarget?> = _target.asStateFlow()

    private val _composer = MutableStateFlow(Composer())
    val composer: StateFlow<Composer> = _composer.asStateFlow()

    init {
        loadPost()
        loadComments()
    }

    fun loadPost() {
        _post.value = Loadable.Loading
        viewModelScope.launch {
            val response = apollo.query(PostQuery(postId)).execute()
            val post = withContext(Dispatchers.Default) {
                response.data?.post?.postFields?.toFeedPost()
            }
            _post.value = post?.let { Loadable.Loaded(it) } ?: Loadable.Error
        }
    }

    fun loadComments() {
        _comments.value = Loadable.Loading
        viewModelScope.launch { fetchComments() }
    }

    private suspend fun fetchComments() {
        val response = apollo.query(CommentsQuery(postId)).execute()
        val data = response.data?.comments
        if (data == null) {
            if (_comments.value !is Loadable.Loaded) _comments.value = Loadable.Error
            return
        }
        val nodes = withContext(Dispatchers.Default) {
            buildCommentTree(data.sorting, data.list.orEmpty().mapNotNull { it?.toComment() })
        }
        val revealed = (_target.value as? PostTarget.Comment)?.let { commentPath(nodes, it.id) }.orEmpty()
        _collapsed.update { current -> current + nodes.filter { it.comment.isIgnored }.map { it.comment.id } - revealed }
        _comments.value = Loadable.Loaded(nodes)
    }

    fun onTargetReached() {
        _target.value = null
    }

    fun onComposerText(text: String) = _composer.update { it.copy(text = text, error = null) }

    fun replyTo(comment: Comment?) = _composer.update { it.copy(replyTo = comment, editing = null, error = null) }

    fun startEditing(comment: Comment) {
        _composer.value = Composer(
            text = blocksToPlainText(comment.textBlocks),
            editing = comment,
            attachments = comment.attachments,
        )
    }

    fun cancelEditing() {
        _composer.value = Composer()
    }

    fun addMedia(uris: List<Uri>) = uris.forEach { uri -> upload { uploader.upload(uri, UploadField.Comment) } }

    fun addImageUrl(url: String) = upload { uploader.uploadUrl(url) }

    private fun upload(request: suspend () -> UploadResult) {
        _composer.update { it.copy(uploading = it.uploading + 1, error = null) }
        viewModelScope.launch {
            val result = request()
            _composer.update { current ->
                val done = current.copy(uploading = current.uploading - 1)
                when (result) {
                    is UploadResult.Success -> done.copy(attachments = done.attachments + result.files)
                    UploadResult.NetworkError -> done.copy(error = ComposerError.UploadNetwork)
                    is UploadResult.Rejected -> done.copy(error = ComposerError.Upload(result.message))
                }
            }
        }
    }

    fun removeAttachment(index: Int) = _composer.update { current ->
        current.copy(attachments = current.attachments.filterIndexed { position, _ -> position != index })
    }

    fun delete(comment: Comment) {
        viewModelScope.launch {
            when (val result = actions.deleteComment(postId, comment.id)) {
                SendCommentResult.Success -> fetchComments()
                SendCommentResult.NetworkError -> _composer.update { it.copy(error = ComposerError.Network) }
                is SendCommentResult.Rejected ->
                    _composer.update { it.copy(error = ComposerError.Rejected(result.message)) }
            }
        }
    }

    fun markViewed() = actions.markViewed(listOf(postId))

    fun sendComment() {
        val current = _composer.value
        if (!current.canSend) return
        _composer.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val parentId = current.editing?.parentId ?: current.replyTo?.id
            val result = actions.sendComment(postId, current.editing?.id, parentId, current.text, current.attachments)
            when (result) {
                SendCommentResult.Success -> {
                    _composer.value = Composer()
                    fetchComments()
                }
                SendCommentResult.NetworkError ->
                    _composer.update { it.copy(sending = false, error = ComposerError.Network) }
                is SendCommentResult.Rejected ->
                    _composer.update { it.copy(sending = false, error = ComposerError.Rejected(result.message)) }
            }
        }
    }

    fun toggle(commentId: Int) {
        _collapsed.update { if (commentId in it) it - commentId else it + commentId }
    }
}

private fun CommentsQuery.List.toComment(): Comment? {
    val commentId = id ?: return null
    val text = parseCommentHtml(content)
    val mediaBlocks = parseMediaList(media).map { Block.MediaBlock(it) }
    return Comment(
        id = commentId,
        author = user?.login,
        avatarPath = user?.avatar?.takeIf { it.isNotEmpty() },
        rating = rating ?: 0,
        myVote = user_rating_value ?: 0,
        reactions = parseReactions(reactions),
        myReaction = my_reaction,
        createdAt = parseServerDate(created_at),
        blocks = text + mediaBlocks,
        isIgnored = is_ignoring == true,
        parentId = parent_id,
        isEditable = is_editable == true,
        textBlocks = text,
        attachments = rawAttachments(media),
    )
}
