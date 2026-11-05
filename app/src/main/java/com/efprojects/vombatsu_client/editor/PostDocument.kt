package com.efprojects.vombatsu_client.editor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val TITLE_MIN = 3
private const val TITLE_MAX = 150
private const val TAGS_MIN = 2
const val TAGS_MAX = 15
private const val CONTENT_MAX = 50_000
private const val POSTPONE_MIN_MINUTES = 9L
private const val POSTPONE_DEFAULT_HOURS = 1L
const val POSTPONE_MAX_DAYS = 7L
private val POSTPONED_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

sealed interface EditorBlock {
    val id: String

    data class Text(override val id: String, val text: String) : EditorBlock
    data class Media(override val id: String, val file: String) : EditorBlock
}

enum class PostProblem {
    Title,
    Tags,
    Empty,
    TooLong,
    PostponedTooSoon,
    PostponedTooLate,
}

fun defaultPostponedDate(now: Instant): Instant =
    now.plus(POSTPONE_DEFAULT_HOURS, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)

fun validatePostponed(at: Instant, now: Instant): PostProblem? = when {
    at < now.plus(POSTPONE_MIN_MINUTES, ChronoUnit.MINUTES) -> PostProblem.PostponedTooSoon
    at > now.plus(POSTPONE_MAX_DAYS, ChronoUnit.DAYS) -> PostProblem.PostponedTooLate
    else -> null
}

fun postponedDateValue(at: Instant): String = POSTPONED_DATE_FORMAT.format(at)

fun Instant.withLocalDate(date: LocalDate, zone: ZoneId): Instant = atZone(zone).with(date).toInstant()

fun Instant.withLocalTime(hour: Int, minute: Int, zone: ZoneId): Instant =
    atZone(zone).withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant()

fun validatePost(title: String, tags: List<String>, blocks: List<EditorBlock>): List<PostProblem> {
    val textLength = blocks.filterIsInstance<EditorBlock.Text>().sumOf { it.text.trim().length }
    val hasContent = textLength > 0 || blocks.any { it is EditorBlock.Media }
    return buildList {
        if (title.trim().length !in TITLE_MIN..TITLE_MAX) add(PostProblem.Title)
        if (tags.size !in TAGS_MIN..TAGS_MAX) add(PostProblem.Tags)
        if (!hasContent) add(PostProblem.Empty)
        if (textLength > CONTENT_MAX) add(PostProblem.TooLong)
    }
}

private fun paragraphNode(text: String): JsonObject = buildJsonObject {
    put("type", "paragraph")
    putJsonArray("content") {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) addJsonObject { put("type", "hardBreak") }
            if (line.isNotEmpty()) {
                addJsonObject {
                    put("type", "text")
                    put("text", line)
                }
            }
        }
    }
}

private fun mediaNode(block: EditorBlock.Media): JsonObject {
    val file = Json.parseToJsonElement(block.file).jsonObject
    val url = file["url"] ?: JsonNull
    val mime = file["info"]?.jsonObject?.get("mime") ?: JsonNull
    val jobId = file["video_job_id"].takeIf { file["processing"]?.jsonPrimitive?.booleanOrNull == true }
    return buildJsonObject {
        put("type", "media")
        putJsonObject("attrs") {
            put("id", block.id)
            put("src", url)
            put("mime", mime)
            put("status", if (jobId != null) "processing" else "done")
            put("progress", 100)
            put("error", JsonNull)
            put("file", file)
            put("originalUrl", JsonNull)
            if (jobId != null) put("videoJobId", jobId)
        }
    }
}

fun buildPostDocument(blocks: List<EditorBlock>): String = buildJsonObject {
    put("type", "doc")
    put(
        "content",
        buildJsonArray {
            blocks.forEach { block ->
                when (block) {
                    is EditorBlock.Media -> add(mediaNode(block))
                    is EditorBlock.Text -> block.text.trim()
                        .split(Regex("\\n\\s*\\n"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { add(paragraphNode(it)) }
                }
            }
        },
    )
}.toString()
