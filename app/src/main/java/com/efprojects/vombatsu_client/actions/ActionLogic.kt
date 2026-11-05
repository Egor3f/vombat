package com.efprojects.vombatsu_client.actions

import com.efprojects.vombatsu_client.ErrorLog
import com.efprojects.vombatsu_client.content.Block
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class Vote(val rating: Int, val myVote: Int)

fun applyVote(current: Vote, pressed: Int): Vote? {
    if (pressed == current.myVote) return null
    val base = current.rating - current.myVote
    val next = if (current.myVote == -pressed) 0 else pressed
    return Vote(base + next, next)
}

data class ReactionCount(val code: Int, val count: Int)

data class Reactions(val counts: List<ReactionCount>, val mine: Int?)

val REACTION_EMOJI: Map<Int, String> = linkedMapOf(
    1 to "❤️", 2 to "😂", 3 to "🔥", 4 to "💯", 5 to "🤔", 6 to "👀", 7 to "😢",
    8 to "🥺", 9 to "😱", 10 to "🤡", 11 to "🤬", 12 to "🤮", 13 to "💩", 14 to "😈",
)

fun parseReactions(raw: String?): List<ReactionCount> {
    if (raw.isNullOrBlank()) return emptyList()
    val items = runCatching { Json.parseToJsonElement(raw) }
        .onFailure { ErrorLog.write("Reactions parse failed: $raw", it) }
        .getOrNull() as? JsonArray ?: return emptyList()
    return items.mapNotNull { item ->
        val pair = item as? JsonArray ?: return@mapNotNull null
        val code = (pair.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val count = (pair.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: 0
        ReactionCount(code, count).takeIf { code in REACTION_EMOJI && count > 0 }
    }
}

fun applyReaction(current: Reactions, code: Int): Reactions {
    val counts = current.counts.associate { it.code to it.count }.toMutableMap()
    current.mine?.let { mine -> counts[mine] = (counts[mine] ?: 1) - 1 }
    val removing = current.mine == code
    if (!removing) counts[code] = (counts[code] ?: 0) + 1
    return Reactions(
        counts = counts
            .filterValues { it > 0 }
            .map { (reaction, count) -> ReactionCount(reaction, count) }
            .sortedWith(compareBy({ -it.count }, { it.code })),
        mine = if (removing) null else code,
    )
}

private fun escapeHtml(text: String) = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

fun blocksToPlainText(blocks: List<Block>): String = blocks.joinToString("\n\n") { block ->
    when (block) {
        is Block.Paragraph -> block.inlines.joinToString("") { it.text }
        is Block.Heading -> block.inlines.joinToString("") { it.text }
        is Block.Quote -> blocksToPlainText(block.blocks)
        is Block.Items -> block.items.joinToString("\n") { "• " + blocksToPlainText(it) }
        else -> ""
    }
}.trim()

fun commentTextToHtml(text: String): String = text
    .trim()
    .split(Regex("\\n\\s*\\n"))
    .joinToString("") { paragraph -> "<p>" + escapeHtml(paragraph.trim()).replace("\n", "<br>") + "</p>" }
