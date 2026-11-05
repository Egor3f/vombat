package com.efprojects.vombatsu_client.content

private val TAG = Regex("<(/?)([a-zA-Z0-9]+)([^>]*)>")
private val HREF = Regex("""href\s*=\s*["']([^"']*)["']""")
private val SCHEMELESS_HOST = Regex("""^[\w-]+(\.[\w-]+)+([/?#:]|$)""")
private val ENTITY = Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "laquo" to "«", "raquo" to "»", "mdash" to "—", "ndash" to "–",
)

private fun decodeEntities(text: String) = ENTITY.replace(text) { match ->
    val name = match.groupValues[1]
    val code = when {
        name.startsWith("#x") -> name.drop(2).toIntOrNull(16)
        name.startsWith("#") -> name.drop(1).toIntOrNull()
        else -> null
    }
    when {
        code != null && Character.isValidCodePoint(code) -> String(Character.toChars(code))
        else -> NAMED_ENTITIES[name] ?: match.value
    }
}

fun absoluteLink(href: String) = if (SCHEMELESS_HOST.containsMatchIn(href)) "https://$href" else href

fun parseInlineHtml(html: String): List<Inline> {
    val result = mutableListOf<Inline>()
    var bold = 0
    var italic = 0
    var underline = 0
    var strike = 0
    var superscript = 0
    val links = ArrayDeque<String?>()
    var position = 0

    fun emit(text: String) {
        if (text.isNotEmpty()) {
            result += Inline(
                text = text,
                bold = bold > 0,
                italic = italic > 0,
                underline = underline > 0,
                strike = strike > 0,
                superscript = superscript > 0,
                link = links.lastOrNull(),
            )
        }
    }

    fun step(count: Int, open: Boolean) = if (open) count + 1 else (count - 1).coerceAtLeast(0)

    TAG.findAll(html).forEach { match ->
        emit(decodeEntities(html.substring(position, match.range.first)))
        position = match.range.last + 1
        val open = match.groupValues[1].isEmpty()
        when (match.groupValues[2].lowercase()) {
            "b", "strong" -> bold = step(bold, open)
            "i", "em" -> italic = step(italic, open)
            "u" -> underline = step(underline, open)
            "s", "strike", "del" -> strike = step(strike, open)
            "sup" -> superscript = step(superscript, open)
            "a" -> if (open) {
                links.addLast(HREF.find(match.groupValues[3])?.groupValues?.get(1)?.let(::decodeEntities)?.let(::absoluteLink))
            } else {
                links.removeLastOrNull()
            }
            "br" -> emit("\n")
            "p" -> if (!open) emit("\n")
        }
    }
    emit(decodeEntities(html.substring(position)))

    while (result.lastOrNull()?.text?.isBlank() == true) result.removeAt(result.lastIndex)
    return result
}

private val PLAIN_URL = Regex("""https?://\S+""")
private const val URL_TRAILING_PUNCTUATION = ".,;:!?)"

fun linkifyPlainText(text: String): List<Inline> {
    val result = mutableListOf<Inline>()
    var position = 0
    PLAIN_URL.findAll(text).forEach { match ->
        val url = match.value.trimEnd { it in URL_TRAILING_PUNCTUATION }
        if (match.range.first > position) result += Inline(text.substring(position, match.range.first))
        result += Inline(url, link = url)
        position = match.range.first + url.length
    }
    if (position < text.length) result += Inline(text.substring(position))
    return result
}
