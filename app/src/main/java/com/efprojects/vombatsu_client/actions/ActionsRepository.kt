package com.efprojects.vombatsu_client.actions

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.graphql.DeleteCommentMutation
import com.efprojects.vombatsu_client.graphql.FavoriteMutation
import com.efprojects.vombatsu_client.graphql.MarkViewedMutation
import com.efprojects.vombatsu_client.graphql.ReactMutation
import com.efprojects.vombatsu_client.graphql.SendCommentMutation
import com.efprojects.vombatsu_client.graphql.SubscribeMutation
import com.efprojects.vombatsu_client.graphql.TagIgnoreActionMutation
import com.efprojects.vombatsu_client.graphql.TagSubscribeMutation
import com.efprojects.vombatsu_client.graphql.UserIgnoreActionMutation
import com.efprojects.vombatsu_client.graphql.VoteMutation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG_IGNORE_ADD = 0
private const val TAG_IGNORE_REMOVE = 1
private const val VIEWS_BATCH_DELAY_MS = 700L

enum class IgnoreType(val value: String) {
    Posts("posts"),
    Comments("comments"),
}

enum class ActionTarget(val type: String) {
    Post("post"),
    Comment("comment"),
}

sealed interface SendCommentResult {
    data object Success : SendCommentResult
    data object NetworkError : SendCommentResult
    data class Rejected(val message: String?) : SendCommentResult
}

class ActionsRepository(private val apollo: ApolloClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val viewedIds = mutableSetOf<Int>()
    private val pendingViews = mutableListOf<Int>()
    private var viewsJob: Job? = null

    private fun send(onFailure: () -> Unit, request: suspend () -> Boolean) {
        scope.launch {
            if (!request()) onFailure()
        }
    }

    fun vote(target: ActionTarget, id: Int, action: Int, onFailure: () -> Unit) = send(onFailure) {
        val response = apollo.mutation(VoteMutation(id.toString(), target.type, action.toString())).execute()
        response.exception == null && response.errors.isNullOrEmpty()
    }

    fun react(target: ActionTarget, id: Int, code: Int, onFailure: () -> Unit) = send(onFailure) {
        val response = apollo.mutation(ReactMutation(id, target.type, Optional.present(code))).execute()
        response.data?.Reaction == "ok"
    }

    fun favorite(postId: Int, add: Boolean, onFailure: () -> Unit) = send(onFailure) {
        val response = apollo
            .mutation(FavoriteMutation(postId, Optional.present(if (add) 1 else 0)))
            .execute()
        response.data?.postFavorite?.response_status == "ok"
    }

    fun subscribeTag(tag: String, add: Boolean, onFailure: () -> Unit) = send(onFailure) {
        val response = apollo
            .mutation(TagSubscribeMutation(Optional.present(tag), Optional.present(if (add) 1 else 0)))
            .execute()
        response.data?.tagSubscription?.response_status == "ok"
    }

    fun ignoreTag(tag: String, add: Boolean, onFailure: () -> Unit) = send(onFailure) {
        val action = if (add) TAG_IGNORE_ADD else TAG_IGNORE_REMOVE
        val response = apollo.mutation(TagIgnoreActionMutation(Optional.present(tag), action)).execute()
        response.data?.tagIgnore?.response_status == "ok"
    }

    fun ignoreUser(userId: Int, type: IgnoreType, add: Boolean, onFailure: () -> Unit) = send(onFailure) {
        val response = apollo
            .mutation(UserIgnoreActionMutation(userId, if (add) 1 else 0, type.value))
            .execute()
        response.data?.userIgnore?.response_status == "ok"
    }

    fun markViewed(ids: List<Int>) {
        pendingViews += ids.filter { viewedIds.add(it) }
        if (pendingViews.isEmpty() || viewsJob?.isActive == true) return
        viewsJob = scope.launch {
            while (pendingViews.isNotEmpty()) {
                delay(VIEWS_BATCH_DELAY_MS)
                val batch = pendingViews.toList()
                pendingViews.clear()
                apollo.mutation(MarkViewedMutation(Optional.present(batch))).execute()
            }
        }
    }

    fun subscribe(userId: Int, add: Boolean, onFailure: () -> Unit) = send(onFailure) {
        val response = apollo.mutation(SubscribeMutation(userId, if (add) 1 else 0)).execute()
        response.data?.userSubscription?.response_status == "ok"
    }

    suspend fun sendComment(
        postId: Int,
        commentId: Int?,
        parentId: Int?,
        text: String,
        attachments: List<String>,
    ): SendCommentResult {
        val response = apollo.mutation(
            SendCommentMutation(
                commentId = Optional.presentIfNotNull(commentId),
                postId = Optional.present(postId),
                parentId = Optional.presentIfNotNull(parentId),
                content = Optional.present(commentTextToHtml(text)),
                media = Optional.present(attachments.joinToString(",", "[", "]")),
            ),
        ).execute()
        if (response.exception != null) return SendCommentResult.NetworkError
        val result = response.data?.comment
            ?: return SendCommentResult.Rejected(response.errors?.firstOrNull()?.message)
        return commentResult(result.response_status, result.error_message)
    }

    suspend fun deleteComment(postId: Int, commentId: Int): SendCommentResult {
        val response = apollo.mutation(
            DeleteCommentMutation(Optional.present(commentId), Optional.present(postId)),
        ).execute()
        if (response.exception != null) return SendCommentResult.NetworkError
        val result = response.data?.comment
            ?: return SendCommentResult.Rejected(response.errors?.firstOrNull()?.message)
        return commentResult(result.response_status, result.error_message)
    }

    private fun commentResult(status: String?, message: String?) =
        if (status == "ok") SendCommentResult.Success else SendCommentResult.Rejected(message)
}
