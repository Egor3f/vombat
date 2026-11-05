package com.efprojects.vombatsu_client.content

data class Inline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val superscript: Boolean = false,
    val spoiler: Boolean = false,
    val link: String? = null,
)

sealed interface Media {
    data class Image(val path: String, val width: Int?, val height: Int?) : Media

    data class Video(
        val url: String,
        val thumbnailUrl: String?,
        val aspectRatio: Float?,
        val loop: Boolean = false,
        val jobId: String? = null,
    ) : Media
}

sealed interface Block {
    data class Paragraph(val inlines: List<Inline>) : Block
    data class Heading(val level: Int, val inlines: List<Inline>) : Block
    data class Quote(val blocks: List<Block>) : Block
    data class Items(val ordered: Boolean, val items: List<List<Block>>) : Block
    data object Rule : Block
    data class MediaBlock(val media: Media) : Block
    data class Gallery(val items: List<Media>) : Block
    data class Embed(val provider: String, val url: String, val thumbnailUrl: String?) : Block
    data class Unsupported(val type: String) : Block
}

private const val PREVIEW_MAX_BLOCKS = 6
private const val PREVIEW_MAX_MEDIA = 2

fun previewBlockCount(blocks: List<Block>): Int {
    var media = 0
    blocks.forEachIndexed { index, block ->
        if (block is Block.MediaBlock || block is Block.Gallery || block is Block.Embed) media++
        if (index >= PREVIEW_MAX_BLOCKS || media > PREVIEW_MAX_MEDIA) return index
    }
    return blocks.size
}

fun spoilerGroups(inlines: List<Inline>): List<Int?> {
    var group = -1
    var inSpoiler = false
    return inlines.map { inline ->
        if (inline.spoiler) {
            if (!inSpoiler) {
                group++
                inSpoiler = true
            }
            group
        } else {
            inSpoiler = false
            null
        }
    }
}
