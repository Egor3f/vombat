package com.efprojects.vombatsu_client.actions

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.efprojects.vombatsu_client.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val UPLOAD_URL = "https://api.vombat.su/fileUpload"
private const val JSON_TYPE = "application/json"
private const val UPLOAD_READ_TIMEOUT_MINUTES = 5L

enum class UploadField(val value: String) {
    Comment("comment_image[]"),
    Post("image"),
}

sealed interface UploadResult {
    data class Success(val files: List<String>) : UploadResult
    data object NetworkError : UploadResult
    data class Rejected(val message: String?) : UploadResult
}

fun parseUploadResponse(body: String): UploadResult {
    val root = runCatching { Json.parseToJsonElement(body) }
        .onFailure { ErrorLog.write("Upload response is not JSON: $body", it) }
        .getOrNull() as? JsonObject
        ?: return UploadResult.Rejected(null)
    val success = (root["success"] as? JsonPrimitive)?.content
    if (success != "1" && success != "true") {
        val message = root["error"] ?: root["message"]
        return UploadResult.Rejected((message as? JsonPrimitive)?.content)
    }
    val files = (root["files"] as? JsonArray)?.toList() ?: listOfNotNull(root["file"])
    if (files.isEmpty()) return UploadResult.Rejected(null)
    return UploadResult.Success(files.map { it.toString() })
}

private class ContentUriBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mime: String,
) : RequestBody() {
    override fun contentType() = mime.toMediaType()

    override fun contentLength() = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.source().use(sink::writeAll)
    }
}

class MediaUploader(
    client: OkHttpClient,
    private val resolver: ContentResolver,
) {
    private val client = client.newBuilder().readTimeout(UPLOAD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES).build()

    suspend fun upload(uri: Uri, field: UploadField): UploadResult = withContext(Dispatchers.IO) {
        val source = "uri=$uri field=${field.value}"
        try {
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: mime.substringAfter('/')
            val content = ContentUriBody(resolver, uri, mime)
            val details = "$source mime=$mime length=${content.contentLength()}"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(field.value, "media.$extension", content)
                .build()
            val request = Request.Builder().url(UPLOAD_URL).post(body).build()
            execute(request, details)
        } catch (e: IOException) {
            ErrorLog.write(source, e)
            UploadResult.NetworkError
        } catch (e: SecurityException) {
            ErrorLog.write(source, e)
            UploadResult.Rejected(null)
        }
    }

    suspend fun uploadUrl(url: String): UploadResult = withContext(Dispatchers.IO) {
        val source = "url=$url"
        try {
            val body = buildJsonObject { put("url", url) }.toString().toRequestBody(JSON_TYPE.toMediaType())
            execute(Request.Builder().url(UPLOAD_URL).post(body).build(), source)
        } catch (e: IOException) {
            ErrorLog.write(source, e)
            UploadResult.NetworkError
        }
    }

    private fun execute(request: Request, details: String): UploadResult =
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            parseUploadResponse(body).also { result ->
                if (result !is UploadResult.Success) ErrorLog.write("$details\nHTTP ${response.code}\n$body", null)
            }
        }
}
