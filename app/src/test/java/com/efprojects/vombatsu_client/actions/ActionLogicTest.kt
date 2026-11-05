package com.efprojects.vombatsu_client.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionLogicTest {
    @Test
    fun `vote follows site rules`() {
        assertEquals(Vote(11, 1), applyVote(Vote(10, 0), 1))
        assertEquals(Vote(9, -1), applyVote(Vote(10, 0), -1))
        assertEquals(Vote(10, 0), applyVote(Vote(11, 1), -1))
        assertEquals(Vote(10, 0), applyVote(Vote(9, -1), 1))
        assertNull(applyVote(Vote(11, 1), 1))
    }

    @Test
    fun `reactions parse and drop unknown`() {
        assertEquals(
            listOf(ReactionCount(2, 1), ReactionCount(4, 2)),
            parseReactions("[[2,1],[4,2],[99,5],[3,0]]"),
        )
        assertEquals(emptyList<ReactionCount>(), parseReactions("[]"))
        assertEquals(emptyList<ReactionCount>(), parseReactions(null))
        assertEquals(emptyList<ReactionCount>(), parseReactions("broken"))
    }

    @Test
    fun `reaction add switch and remove`() {
        val added = applyReaction(Reactions(listOf(ReactionCount(2, 1)), null), 2)
        assertEquals(Reactions(listOf(ReactionCount(2, 2)), 2), added)
        val switched = applyReaction(added, 4)
        assertEquals(Reactions(listOf(ReactionCount(2, 1), ReactionCount(4, 1)), 4), switched)
        assertEquals(Reactions(listOf(ReactionCount(2, 1)), null), applyReaction(switched, 4))
    }

    @Test
    fun `comment text is escaped and split`() {
        assertEquals(
            "<p>a &lt;b&gt; &amp; c<br>d</p><p>e</p>",
            commentTextToHtml("  a <b> & c\nd\n\n  e \n"),
        )
    }
}

class UploadAndEditTest {
    @Test
    fun `upload response keeps file json for media field`() {
        val result = parseUploadResponse(
            """{"success":1,"files":[{"relative":"/images/comment/a.png","info":{"width":1,"height":2,"mime":"image/png"},"type":"image"}]}""",
        )
        assertEquals(
            UploadResult.Success(
                listOf("""{"relative":"/images/comment/a.png","info":{"width":1,"height":2,"mime":"image/png"},"type":"image"}"""),
            ),
            result,
        )
        assertEquals(UploadResult.Rejected("too big"), parseUploadResponse("""{"success":0,"error":"too big"}"""))
        assertEquals(UploadResult.Rejected(null), parseUploadResponse("<html>"))
    }

    @Test
    fun `editing text round trips paragraphs and breaks`() {
        val html = "<p>a &lt;b&gt;<br>c</p><p>d</p>"
        val text = blocksToPlainText(com.efprojects.vombatsu_client.content.parseCommentHtml(html))
        assertEquals("a <b>\nc\n\nd", text)
        assertEquals(html, commentTextToHtml(text))
    }
}
