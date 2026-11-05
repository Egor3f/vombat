package com.efprojects.vombatsu_client.navigation

import com.efprojects.vombatsu_client.ErrorLog
import java.net.URLDecoder

sealed interface InternalLink {
    data class Post(val id: Int) : InternalLink
    data class User(val login: String) : InternalLink
    data class Tag(val name: String) : InternalLink
}

private val SITE_LINK = Regex("""^https?://(?:www\.)?vombat\.su/(post|user|tag)/([^/?#]+)""", RegexOption.IGNORE_CASE)
private val POST_ID = Regex("""^\d+""")

fun parseInternalLink(url: String): InternalLink? {
    val match = SITE_LINK.find(url) ?: return null
    val value = runCatching { URLDecoder.decode(match.groupValues[2], "UTF-8") }
        .onFailure { ErrorLog.write("Internal link decode failed: $url", it) }
        .getOrNull() ?: return null
    return when (match.groupValues[1].lowercase()) {
        "post" -> POST_ID.find(value)?.value?.toIntOrNull()?.let(InternalLink::Post)
        "user" -> InternalLink.User(value)
        else -> InternalLink.Tag(value)
    }
}
