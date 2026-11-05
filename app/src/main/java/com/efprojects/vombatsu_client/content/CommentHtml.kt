package com.efprojects.vombatsu_client.content

private val BLOCK_TAG = Regex(
    "<(/?)(blockquote|ul|ol|p)\\b[^>]*>",
    RegexOption.IGNORE_CASE,
)
private val LIST_ITEM_TAG = Regex(
    "<(/?)li\\b[^>]*>",
    RegexOption.IGNORE_CASE,
)

fun parseCommentHtml(html: String?): List<Block> {
    if (html.isNullOrBlank()) return emptyList()
    val blocks = mutableListOf<Block>()
    var position = 0
    var activeTag: String? = null
    var bodyStart = 0
    var depth = 0

    fun addParagraph(text: String) {
        val inlines = parseInlineHtml(text)
        if (inlines.any { it.text.isNotBlank() }) blocks += Block.Paragraph(inlines)
    }

    fun addBlock(tag: String, body: String) {
        when (tag) {
            "p" -> addParagraph(body)
            "blockquote" -> blocks += Block.Quote(parseCommentHtml(body))
            else -> blocks += Block.Items(
                ordered = tag == "ol",
                items = parseListItems(body),
            )
        }
    }

    BLOCK_TAG.findAll(html).forEach { match ->
        val closing = match.groupValues[1].isNotEmpty()
        val tag = match.groupValues[2].lowercase()
        if (depth == 0) {
            if (!closing) {
                addParagraph(html.substring(position, match.range.first))
                activeTag = tag
                bodyStart = match.range.last + 1
                depth = 1
            }
        } else if (tag == activeTag) {
            depth += if (closing) -1 else 1
            if (depth == 0) {
                addBlock(tag, html.substring(bodyStart, match.range.first))
                position = match.range.last + 1
                activeTag = null
            }
        }
    }
    val unclosed = activeTag
    if (unclosed != null) {
        addBlock(unclosed, html.substring(bodyStart))
    } else {
        addParagraph(html.substring(position))
    }
    return blocks
}

private fun parseListItems(html: String): List<List<Block>> {
    val items = mutableListOf<List<Block>>()
    var bodyStart = 0
    var depth = 0
    LIST_ITEM_TAG.findAll(html).forEach { match ->
        val closing = match.groupValues[1].isNotEmpty()
        if (depth == 0) {
            if (!closing) {
                bodyStart = match.range.last + 1
                depth = 1
            }
        } else {
            depth += if (closing) -1 else 1
            if (depth == 0) {
                items += parseCommentHtml(html.substring(bodyStart, match.range.first))
            }
        }
    }
    if (depth > 0) items += parseCommentHtml(html.substring(bodyStart))
    return items
}
