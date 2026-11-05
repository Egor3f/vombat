package com.efprojects.vombatsu_client.content

import com.efprojects.vombatsu_client.ErrorLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun JsonElement?.obj() = this as? JsonObject
private fun JsonElement?.arr() = (this as? JsonArray).orEmpty()
private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
private fun JsonElement?.num() = str()?.toFloatOrNull()

private val YOUTUBE_ID = Regex("""(?:embed/|v=|youtu\.be/)([\w-]+)""")
private val RUTUBE_ID = Regex("""(?:embed|video)/([^/?]+)""")

fun parsePostContent(raw: String?): List<Block> {
    if (raw.isNullOrBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(raw) }
        .onFailure { ErrorLog.write("Post content parse failed: $raw", it) }
        .getOrNull().obj()
        ?: return listOf(Block.Paragraph(parseInlineHtml(raw)))
    return when {
        root["type"].str() == "doc" -> parseDocNodes(root["content"].arr())
        "blocks" in root -> root["blocks"].arr().mapNotNull { parseEditorBlock(it.obj()) }
        else -> listOf(Block.Unsupported("root"))
    }
}

fun parseMediaList(raw: String?): List<Media> {
    if (raw.isNullOrBlank()) return emptyList()
    val items = runCatching { Json.parseToJsonElement(raw) }
        .onFailure { ErrorLog.write("Media list parse failed: $raw", it) }
        .getOrNull().arr()
    return items.mapNotNull { parseMedia(it.obj()) }
}

fun rawAttachments(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { Json.parseToJsonElement(raw) }
        .onFailure { ErrorLog.write("Attachments parse failed: $raw", it) }
        .getOrNull().arr().map { it.toString() }
}

private fun parseDocNodes(nodes: List<JsonElement>) = nodes.mapNotNull { parseDocNode(it.obj()) }

private fun parseDocNode(node: JsonObject?): Block? {
    if (node == null) return null
    val type = node["type"].str() ?: return null
    val attrs = node["attrs"].obj()
    val children = node["content"].arr()
    return when (type) {
        "paragraph" -> parseDocInlines(children).takeIf { it.isNotEmpty() }?.let(Block::Paragraph)
        "heading" -> Block.Heading(attrs?.get("level").num()?.toInt() ?: 3, parseDocInlines(children))
        "blockquote" -> Block.Quote(parseDocNodes(children))
        "bulletList", "orderedList" -> Block.Items(
            ordered = type == "orderedList",
            items = children.map { parseDocNodes(it.obj()?.get("content").arr()) },
        )
        "horizontalRule" -> Block.Rule
        "media" -> parseMedia(attrs?.get("file").obj())?.let(Block::MediaBlock)
        "gallery" -> Block.Gallery(
            attrs?.get("items").arr().mapNotNull { parseMedia(it.obj()?.get("file").obj()) },
        )
        "videoEmbed" -> embed(
            provider = attrs?.get("provider").str(),
            url = attrs?.get("originalUrl").str(),
            videoId = attrs?.get("videoId").str(),
        )
        else -> Block.Unsupported(type)
    }
}

private fun parseDocInlines(nodes: List<JsonElement>): List<Inline> = nodes.mapNotNull { element ->
    val node = element.obj() ?: return@mapNotNull null
    when (node["type"].str()) {
        "text" -> applyMarks(Inline(node["text"].str().orEmpty()), node["marks"].arr())
        "hardBreak" -> Inline("\n")
        "mention" -> {
            val label = node["attrs"].obj()?.get("label").str().orEmpty()
            Inline("@$label", link = "https://vombat.su/user/$label")
        }
        else -> null
    }
}

private fun applyMarks(inline: Inline, marks: List<JsonElement>) = marks.fold(inline) { acc, element ->
    val mark = element.obj()
    when (mark?.get("type").str()) {
        "bold" -> acc.copy(bold = true)
        "italic" -> acc.copy(italic = true)
        "underline" -> acc.copy(underline = true)
        "strike" -> acc.copy(strike = true)
        "superscript" -> acc.copy(superscript = true)
        "spoiler" -> acc.copy(spoiler = true)
        "link" -> acc.copy(link = mark?.get("attrs").obj()?.get("href").str()?.let(::absoluteLink))
        else -> acc
    }
}

private fun parseEditorBlock(block: JsonObject?): Block? {
    if (block == null) return null
    val type = block["type"].str() ?: return null
    val data = block["data"].obj()
    return when (type) {
        "paragraph" -> parseInlineHtml(data?.get("text").str().orEmpty())
            .takeIf { inlines -> inlines.any { it.text.isNotBlank() } }
            ?.let(Block::Paragraph)
        "quote" -> Block.Quote(listOf(Block.Paragraph(parseInlineHtml(data?.get("text").str().orEmpty()))))
        "list" -> Block.Items(
            ordered = data?.get("style").str() == "ordered",
            items = data?.get("items").arr().map { item ->
                listOf(Block.Paragraph(parseInlineHtml(item.str().orEmpty())))
            },
        )
        "image" -> parseMedia(data?.get("file").obj())?.let(Block::MediaBlock) ?: Block.Unsupported(type)
        "embed" -> embed(
            provider = data?.get("service").str(),
            url = data?.get("source").str(),
            videoId = null,
        )
        "video-link" -> data?.get("source").str()?.let { source ->
            Block.MediaBlock(
                Media.Video(
                    url = source,
                    thumbnailUrl = data?.get("cover").str()?.takeIf { it.isNotEmpty() },
                    aspectRatio = data?.get("ratio").num(),
                ),
            )
        } ?: Block.Unsupported(type)
        else -> Block.Unsupported(type)
    }
}

private fun parseMedia(file: JsonObject?): Media? {
    if (file == null) return null
    val path = file["relative"].str() ?: return null
    val info = file["info"].obj()
    val mime = info?.get("mime").str().orEmpty()
    val width = info?.get("width").num()
    val height = info?.get("height").num()
    val aspectRatio = if (width != null && height != null && height > 0f) width / height else null
    return when {
        mime == "image/gif" -> {
            val video = file["video"].obj()
            Media.Video(
                url = video?.get("relative").str()?.let(::videoUrl) ?: gifVideoUrl(path),
                thumbnailUrl = video?.get("thumbnail").obj()?.get("relative").str()?.let(::rawImageUrl)
                    ?: gifThumbnailUrl(path),
                aspectRatio = aspectRatio,
                loop = true,
            )
        }
        mime.startsWith("video/") || file["type"].str() == "video" -> Media.Video(
            url = videoUrl(path),
            thumbnailUrl = (
                file["thumbnail"].obj()?.get("relative").str()
                    ?: info?.get("thumbnail_path").str()
                    ?: info?.get("thumbnailPath").str()
                )?.let(::rawImageUrl),
            aspectRatio = aspectRatio,
            jobId = file["video_job_id"].str()?.takeIf { file["processing"].str() == "true" },
        )
        else -> Media.Image(path, width?.toInt(), height?.toInt())
    }
}

private fun embed(provider: String?, url: String?, videoId: String?): Block {
    if (url == null) return Block.Unsupported("embed")
    val thumbnail = when (provider) {
        "youtube" -> (videoId ?: YOUTUBE_ID.find(url)?.groupValues?.get(1))
            ?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
        "rutube" -> (videoId ?: RUTUBE_ID.find(url)?.groupValues?.get(1))
            ?.let { "https://rutube.ru/api/video/$it/thumbnail/?redirect=1" }
        else -> null
    }
    return Block.Embed(provider ?: "video", url, thumbnail)
}
