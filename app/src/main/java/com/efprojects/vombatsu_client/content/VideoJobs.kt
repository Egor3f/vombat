package com.efprojects.vombatsu_client.content

import androidx.compose.runtime.staticCompositionLocalOf
import com.efprojects.vombatsu_client.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val STATUS_URL = "https://api.vombat.su/api/video-jobs/status"
private const val STATUS_COMPLETED = "completed"
private const val STATUS_FAILED = "failed"

data class VideoJob(val status: String?, val playlistKey: String?, val thumbnailKey: String?) {
    val finished get() = status == STATUS_COMPLETED || status == STATUS_FAILED
}

fun parseVideoJob(body: String, id: String): VideoJob? {
    fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.content != "null" }?.content
    val root = runCatching { Json.parseToJsonElement(body) }
        .onFailure { ErrorLog.write("Video job status parse failed: $body", it) }
        .getOrNull() as? JsonObject ?: return null
    val job = (root["jobs"] as? JsonArray)?.filterIsInstance<JsonObject>()?.find { it.text("id") == id } ?: return null
    return VideoJob(job.text("status"), job.text("playlist_key"), job.text("thumbnail_key"))
}

class VideoJobs(private val client: OkHttpClient) {
    suspend fun status(id: String): VideoJob? = withContext(Dispatchers.IO) {
        val url = STATUS_URL.toHttpUrl().newBuilder().addQueryParameter("ids", id).build()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) parseVideoJob(response.body.string(), id) else null
            }
        } catch (_: IOException) {
            null
        }
    }
}

val LocalVideoJobs = staticCompositionLocalOf<VideoJobs> { error("VideoJobs is not provided") }
