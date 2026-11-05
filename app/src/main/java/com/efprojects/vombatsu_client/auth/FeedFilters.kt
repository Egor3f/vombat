package com.efprojects.vombatsu_client.auth

import com.efprojects.vombatsu_client.ErrorLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val KEY_NSFW = "show_nsfw"
private const val KEY_CRUEL = "show_cruel"
private const val KEY_POLITICS = "show_politics"
private const val KEY_HIDE_VIEWED = "hide_viewed"

data class FeedFilters(
    val showNsfw: Boolean = false,
    val showCruel: Boolean = false,
    val showPolitics: Boolean = false,
    val hideViewed: Boolean = false,
)

fun parseFeedFilters(settings: String?): FeedFilters {
    val root = settings
        ?.let { runCatching { Json.parseToJsonElement(it) }
            .onFailure { error -> ErrorLog.write("Feed settings parse failed: $it", error) }
            .getOrNull() } as? JsonObject
        ?: return FeedFilters()
    fun flag(key: String) = (root[key] as? JsonPrimitive)?.booleanOrNull == true
    return FeedFilters(
        showNsfw = flag(KEY_NSFW),
        showCruel = flag(KEY_CRUEL),
        showPolitics = flag(KEY_POLITICS),
        hideViewed = flag(KEY_HIDE_VIEWED),
    )
}

fun feedFiltersDiff(old: FeedFilters, new: FeedFilters): String = buildJsonObject {
    if (old.showNsfw != new.showNsfw) put(KEY_NSFW, new.showNsfw)
    if (old.showCruel != new.showCruel) put(KEY_CRUEL, new.showCruel)
    if (old.showPolitics != new.showPolitics) put(KEY_POLITICS, new.showPolitics)
    if (old.hideViewed != new.hideViewed) put(KEY_HIDE_VIEWED, new.hideViewed)
}.toString()
