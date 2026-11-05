package com.efprojects.vombatsu_client.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.actions.MediaUploader
import com.efprojects.vombatsu_client.actions.UploadField
import com.efprojects.vombatsu_client.actions.UploadResult
import com.efprojects.vombatsu_client.graphql.AddPostPreflightQuery
import com.efprojects.vombatsu_client.graphql.PublishPostMutation
import com.efprojects.vombatsu_client.graphql.TagSuggestionsQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private const val SUGGESTIONS_MIN_LENGTH = 2
private const val SUGGESTIONS_DEBOUNCE_MS = 300L

sealed interface EditorError {
    data object Network : EditorError
    data object UploadNetwork : EditorError
    data class Upload(val message: String?) : EditorError
    data class Rejected(val message: String?) : EditorError
}

data class EditorState(
    val title: String = "",
    val blocks: List<EditorBlock> = listOf(EditorBlock.Text(UUID.randomUUID().toString(), "")),
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val suggestions: List<String> = emptyList(),
    val isOwn: Boolean = false,
    val isNsfw: Boolean = false,
    val isCruel: Boolean = false,
    val isPolitics: Boolean = false,
    val postponedAt: Instant? = null,
    val uploading: Int = 0,
    val sending: Boolean = false,
    val problems: List<PostProblem> = emptyList(),
    val error: EditorError? = null,
    val preflightMessage: String? = null,
    val publishedPostId: Int? = null,
    val published: Boolean = false,
) {
    val hasContent
        get() = title.isNotBlank() || tags.isNotEmpty() ||
            blocks.any { it is EditorBlock.Media || (it is EditorBlock.Text && it.text.isNotBlank()) }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val apollo: ApolloClient,
    private val uploader: MediaUploader,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val preflight = apollo.query(AddPostPreflightQuery()).execute().data?.addPostPreflight
            if (preflight != null && preflight.response_status != "ok") {
                _state.update { it.copy(preflightMessage = preflight.error_message) }
            }
        }
        _state.map { it.tagInput.trim() }
            .distinctUntilChanged()
            .debounce(SUGGESTIONS_DEBOUNCE_MS)
            .mapLatest { part -> if (part.length < SUGGESTIONS_MIN_LENGTH) emptyList() else suggestions(part) }
            .onEach { found -> _state.update { it.copy(suggestions = found) } }
            .launchIn(viewModelScope)
    }

    private suspend fun suggestions(part: String): List<String> =
        apollo.query(TagSuggestionsQuery(part)).execute().data?.tags.orEmpty()
            .mapNotNull { it?.tag }
            .take(8)

    fun onTitle(value: String) = _state.update { it.copy(title = value, problems = emptyList()) }

    fun onText(id: String, value: String) = _state.update { current ->
        current.copy(
            blocks = current.blocks.map { if (it.id == id && it is EditorBlock.Text) it.copy(text = value) else it },
            problems = emptyList(),
        )
    }

    fun removeBlock(id: String) = _state.update { current ->
        val left = current.blocks.filterNot { it.id == id }
        current.copy(blocks = left.ifEmpty { listOf(newTextBlock()) })
    }

    fun onTagInput(value: String) {
        if (value.endsWith(",")) addTag(value.dropLast(1)) else _state.update { it.copy(tagInput = value) }
    }

    fun addTag(value: String) = _state.update { current ->
        val tag = value.trim()
        val canAdd = tag.isNotEmpty() && tag !in current.tags && current.tags.size < TAGS_MAX
        current.copy(
            tags = if (canAdd) current.tags + tag else current.tags,
            tagInput = "",
            suggestions = emptyList(),
            problems = emptyList(),
        )
    }

    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }

    fun onOwn(value: Boolean) = _state.update { it.copy(isOwn = value) }
    fun onNsfw(value: Boolean) = _state.update { it.copy(isNsfw = value) }
    fun onCruel(value: Boolean) = _state.update { it.copy(isCruel = value) }
    fun onPolitics(value: Boolean) = _state.update { it.copy(isPolitics = value) }

    fun onPostponed(value: Boolean) = _state.update {
        it.copy(postponedAt = if (value) defaultPostponedDate(Instant.now()) else null, problems = emptyList())
    }

    fun onPostponedAt(value: Instant) = _state.update { it.copy(postponedAt = value, problems = emptyList()) }

    fun addMedia(uris: List<Uri>) = uris.forEach { uri -> upload { uploader.upload(uri, UploadField.Post) } }

    fun addImageUrl(url: String) = upload { uploader.uploadUrl(url) }

    private fun upload(request: suspend () -> UploadResult) {
        _state.update { it.copy(uploading = it.uploading + 1, error = null) }
        viewModelScope.launch {
            val result = request()
            _state.update { current ->
                val done = current.copy(uploading = current.uploading - 1)
                when (result) {
                    is UploadResult.Success -> done.withMedia(result.files)
                    UploadResult.NetworkError -> done.copy(error = EditorError.UploadNetwork)
                    is UploadResult.Rejected -> done.copy(error = EditorError.Upload(result.message))
                }
            }
        }
    }

    private fun EditorState.withMedia(files: List<String>): EditorState {
        val trailingEmpty = (blocks.lastOrNull() as? EditorBlock.Text)?.takeIf { it.text.isBlank() }
        val kept = if (trailingEmpty != null) blocks.dropLast(1) else blocks
        val media = files.map { EditorBlock.Media(UUID.randomUUID().toString(), it) }
        return copy(blocks = kept + media + newTextBlock(), problems = emptyList())
    }

    private fun newTextBlock() = EditorBlock.Text(UUID.randomUUID().toString(), "")

    fun publish() {
        val current = _state.value
        if (current.sending || current.uploading > 0) return
        val problems = validatePost(current.title, current.tags, current.blocks) +
            listOfNotNull(current.postponedAt?.let { validatePostponed(it, Instant.now()) })
        if (problems.isNotEmpty()) {
            _state.update { it.copy(problems = problems, error = null) }
            return
        }
        _state.update { it.copy(sending = true, problems = emptyList(), error = null) }
        viewModelScope.launch {
            val response = apollo.mutation(
                PublishPostMutation(
                    title = current.title.trim(),
                    content = buildPostDocument(current.blocks),
                    tags = current.tags,
                    isOwn = Optional.present(current.isOwn),
                    isNsfw = Optional.present(current.isNsfw),
                    isCruel = Optional.present(current.isCruel),
                    isPolitics = Optional.present(current.isPolitics),
                    isPostponed = Optional.present(current.postponedAt != null),
                    postponedDate = Optional.presentIfNotNull(current.postponedAt?.let(::postponedDateValue)),
                ),
            ).execute()
            val post = response.data?.post
            _state.update { state ->
                when {
                    response.exception != null -> state.copy(sending = false, error = EditorError.Network)
                    post?.response_status == "ok" -> state.copy(
                        sending = false,
                        published = true,
                        publishedPostId = post.post_id,
                    )
                    else -> state.copy(
                        sending = false,
                        error = EditorError.Rejected(post?.error_message ?: response.errors?.firstOrNull()?.message),
                    )
                }
            }
        }
    }
}
