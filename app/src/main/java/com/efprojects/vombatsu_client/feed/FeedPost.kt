package com.efprojects.vombatsu_client.feed

import com.efprojects.vombatsu_client.ErrorLog
import com.efprojects.vombatsu_client.actions.ReactionCount
import com.efprojects.vombatsu_client.actions.parseReactions
import com.efprojects.vombatsu_client.content.Block
import com.efprojects.vombatsu_client.content.parsePostContent
import com.efprojects.vombatsu_client.graphql.fragment.PostFields
import java.time.Instant
import java.time.OffsetDateTime

data class FeedPost(
    val id: Int,
    val alias: String?,
    val title: String,
    val login: String,
    val avatarPath: String?,
    val rating: Int,
    val myVote: Int,
    val reactions: List<ReactionCount>,
    val myReaction: Int?,
    val isFavorite: Boolean,
    val favoritesAmount: Int,
    val commentsAmount: Int,
    val createdAt: Instant?,
    val tags: List<String>,
    val blocks: List<Block>,
    val isNsfw: Boolean,
    val isCruel: Boolean,
    val isPolitics: Boolean,
    val isPostponed: Boolean,
    val postponedAt: Instant?,
    val isCovered: Boolean,
)

fun parseServerDate(value: String?): Instant? {
    if (value == null) return null
    val iso = if (' ' in value) value.replace(' ', 'T') + "+00:00" else value
    return runCatching { OffsetDateTime.parse(iso).toInstant() }
        .onFailure { ErrorLog.write("Server date parse failed: $value", it) }
        .getOrNull()
}

fun PostFields.toFeedPost(): FeedPost? {
    val postId = id ?: return null
    return FeedPost(
        id = postId,
        alias = alias?.takeIf { it.isNotEmpty() },
        title = title.orEmpty(),
        login = login.orEmpty(),
        avatarPath = avatar?.takeIf { it.isNotEmpty() },
        rating = rating ?: 0,
        myVote = user_rating_value ?: 0,
        reactions = parseReactions(reactions_serialized),
        myReaction = my_reaction,
        isFavorite = is_in_favorites == true,
        favoritesAmount = in_favorites_amount ?: 0,
        commentsAmount = comments_amount ?: 0,
        createdAt = parseServerDate(created_at),
        tags = tags.orEmpty().mapNotNull { it?.tag },
        blocks = parsePostContent(content),
        isNsfw = is_nsfw == true,
        isCruel = is_cruel == true,
        isPostponed = is_postponed == true,
        postponedAt = parseServerDate(postponed_date_utc),
        isPolitics = is_politics == true,
        isCovered = is_sensitive_content_hidden == true || is_blur_sensitive_content == true,
    )
}
