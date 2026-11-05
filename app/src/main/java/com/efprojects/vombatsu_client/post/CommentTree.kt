package com.efprojects.vombatsu_client.post

import com.efprojects.vombatsu_client.ErrorLog
import com.efprojects.vombatsu_client.actions.ReactionCount
import com.efprojects.vombatsu_client.content.Block
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

data class Comment(
    val id: Int,
    val author: String?,
    val avatarPath: String?,
    val rating: Int,
    val myVote: Int,
    val reactions: List<ReactionCount>,
    val myReaction: Int?,
    val createdAt: Instant?,
    val blocks: List<Block>,
    val isIgnored: Boolean,
    val parentId: Int? = null,
    val isEditable: Boolean = false,
    val textBlocks: List<Block> = emptyList(),
    val attachments: List<String> = emptyList(),
)

data class CommentNode(val comment: Comment, val depth: Int, val descendants: Int)

fun buildCommentTree(sorting: String?, comments: List<Comment>): List<CommentNode> {
    val byId = comments.associateBy { it.id }
    val result = mutableListOf<CommentNode>()
    val placed = mutableSetOf<Int>()

    fun walk(level: JsonObject, depth: Int) {
        level.forEach { (key, children) ->
            val comment = key.toIntOrNull()?.let(byId::get) ?: return@forEach
            val index = result.size
            result += CommentNode(comment, depth, 0)
            placed += comment.id
            (children as? JsonObject)?.let { walk(it, depth + 1) }
            result[index] = result[index].copy(descendants = result.size - index - 1)
        }
    }

    val root = sorting?.let { runCatching { Json.parseToJsonElement(it) }
            .onFailure { error -> ErrorLog.write("Comment sorting parse failed: $it", error) }
            .getOrNull() } as? JsonObject
    root?.let { walk(it, 0) }
    comments.filter { it.id !in placed }.forEach { result += CommentNode(it, 0, 0) }
    return result
}

fun commentPath(nodes: List<CommentNode>, id: Int): Set<Int> {
    val byId = nodes.associate { it.comment.id to it.comment }
    return generateSequence(byId[id]) { byId[it.parentId] }.map { it.id }.toSet()
}

fun visibleComments(nodes: List<CommentNode>, collapsed: Set<Int>): List<CommentNode> {
    val result = mutableListOf<CommentNode>()
    var hiddenBelow: Int? = null
    nodes.forEach { node ->
        val limit = hiddenBelow
        if (limit != null && node.depth > limit) return@forEach
        hiddenBelow = null
        result += node
        if (node.comment.id in collapsed) hiddenBelow = node.depth
    }
    return result
}
