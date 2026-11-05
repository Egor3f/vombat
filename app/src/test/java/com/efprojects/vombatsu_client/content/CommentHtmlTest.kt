package com.efprojects.vombatsu_client.content

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentHtmlTest {
    @Test
    fun `paragraphs breaks and empty ones`() {
        assertEquals(
            listOf(
                Block.Paragraph(listOf(Inline("a"), Inline("\n"), Inline("b"))),
                Block.Paragraph(listOf(Inline("c", bold = true))),
            ),
            parseCommentHtml("<p>a<br>b<br><br></p><p>&nbsp;</p><p><strong>c</strong></p><p> </p>"),
        )
    }

    @Test
    fun `quote with link and list`() {
        assertEquals(
            listOf(
                Block.Quote(listOf(Block.Paragraph(listOf(Inline("q"))))),
                Block.Paragraph(listOf(Inline("x", link = "https://a.b"))),
                Block.Items(false, listOf(listOf(Block.Paragraph(listOf(Inline("i")))))),
            ),
            parseCommentHtml(
                """<blockquote><p>q</p></blockquote><p><a target="_blank" href="https://a.b" rel="noreferrer">x</a></p><ul><li><p>i</p></li></ul>""",
            ),
        )
    }

    @Test
    fun `nested quotes lists and inline tags`() {
        assertEquals(
            listOf(
                Block.Quote(
                    listOf(
                        Block.Quote(listOf(Block.Paragraph(listOf(Inline("inner"))))),
                        Block.Paragraph(
                            listOf(
                                Inline("a ", bold = true),
                                Inline("b", bold = true),
                                Inline(" c", bold = true),
                            ),
                        ),
                    ),
                ),
                Block.Items(
                    ordered = false,
                    items = listOf(
                        listOf(
                            Block.Paragraph(listOf(Inline("parent"))),
                            Block.Items(false, listOf(listOf(Block.Paragraph(listOf(Inline("child")))))),
                        ),
                    ),
                ),
            ),
            parseCommentHtml(
                "<blockquote><blockquote><p>inner</p></blockquote><p><b>a <strong>b</strong> c</b></p></blockquote>" +
                    "<ul><li><p>parent</p><ul><li><p>child</p></li></ul></li></ul>",
            ),
        )
    }

    @Test
    fun `plain text is a paragraph`() {
        assertEquals(
            listOf(Block.Paragraph(listOf(Inline("Комментарий удалён")))),
            parseCommentHtml("Комментарий удалён"),
        )
        assertEquals(emptyList<Block>(), parseCommentHtml(null))
    }

    @Test
    fun `media list reuses post media format`() {
        assertEquals(
            listOf(Media.Image("/images/comment/a.png", 521, 459)),
            parseMediaList("""[{"url":"https://img.vombat.su/images/comment/a.png","relative":"/images/comment/a.png","info":{"width":521,"height":459,"mime":"image/png"},"type":"image"}]"""),
        )
        assertEquals(emptyList<Media>(), parseMediaList("[]"))
        assertEquals(emptyList<Media>(), parseMediaList("broken"))
    }
}
