package com.efprojects.vombatsu_client.editor

import com.efprojects.vombatsu_client.actions.UploadResult
import com.efprojects.vombatsu_client.actions.parseUploadResponse
import com.efprojects.vombatsu_client.content.Block
import com.efprojects.vombatsu_client.content.Inline
import com.efprojects.vombatsu_client.content.Media
import com.efprojects.vombatsu_client.content.parsePostContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PostDocumentTest {
    private val file =
        """{"url":"https://img.vombat.su/images/post/big/a.jpg","relative":"/images/post/big/a.jpg","info":{"width":700,"height":350,"mime":"image/jpeg"},"type":"image"}"""

    @Test
    fun `document round trips through our own parser`() {
        val json = buildPostDocument(
            listOf(
                EditorBlock.Text("1", "  первый\nвторой\n\n  третий <b> \"x\" "),
                EditorBlock.Media("2", file),
                EditorBlock.Text("3", "   "),
            ),
        )
        assertEquals(
            listOf(
                Block.Paragraph(listOf(Inline("первый"), Inline("\n"), Inline("второй"))),
                Block.Paragraph(listOf(Inline("третий <b> \"x\""))),
                Block.MediaBlock(Media.Image("/images/post/big/a.jpg", 700, 350)),
            ),
            parsePostContent(json),
        )
    }

    @Test
    fun `media node matches site attrs`() {
        val json = buildPostDocument(listOf(EditorBlock.Media("uuid", file)))
        assertTrue(json, json.contains(""""type":"media""""))
        assertTrue(json, json.contains(""""id":"uuid""""))
        assertTrue(json, json.contains(""""src":"https://img.vombat.su/images/post/big/a.jpg""""))
        assertTrue(json, json.contains(""""mime":"image/jpeg""""))
        assertTrue(json, json.contains(""""status":"done""""))
        assertTrue(json, json.contains(""""progress":100"""))
    }

    @Test
    fun `validation matches site limits`() {
        val text = listOf(EditorBlock.Text("1", "текст"))
        val image = listOf(EditorBlock.Media("1", file))
        val tags = listOf("a", "b")
        assertEquals(emptyList<PostProblem>(), validatePost("abc", tags, text))
        assertEquals(emptyList<PostProblem>(), validatePost("abc", tags, image))
        assertEquals(listOf(PostProblem.Title), validatePost(" ab ", tags, text))
        assertEquals(listOf(PostProblem.Title), validatePost("a".repeat(151), tags, text))
        assertEquals(listOf(PostProblem.Tags), validatePost("abc", listOf("a"), text))
        assertEquals(listOf(PostProblem.Tags), validatePost("abc", List(16) { "$it" }, text))
        assertEquals(listOf(PostProblem.Empty), validatePost("abc", tags, listOf(EditorBlock.Text("1", "  "))))
        assertEquals(
            listOf(PostProblem.TooLong),
            validatePost("abc", tags, listOf(EditorBlock.Text("1", "a".repeat(50_001)))),
        )
    }

    @Test
    fun `processing video keeps job through document`() {
        val video =
            """{"relative":"","type":"video","processing":true,"video_job_id":"j1","info":{"width":16,"height":9,"mime":"video/mp4"}}"""
        val json = buildPostDocument(listOf(EditorBlock.Media("1", video)))
        val attrs = Json.parseToJsonElement(json).jsonObject["content"]!!.jsonArray[0].jsonObject["attrs"]!!.jsonObject
        assertEquals("processing", attrs["status"]!!.jsonPrimitive.content)
        assertEquals("j1", attrs["videoJobId"]!!.jsonPrimitive.content)
        val media = parsePostContent(json).filterIsInstance<Block.MediaBlock>().single().media as Media.Video
        assertEquals("j1", media.jobId)
    }

    @Test
    fun `post upload response has single file and message error`() {
        assertEquals(
            UploadResult.Success(listOf(file)),
            parseUploadResponse("""{"success":1,"file":$file}"""),
        )
        assertEquals(UploadResult.Rejected("big"), parseUploadResponse("""{"success":0,"message":"big"}"""))
    }

    @Test
    fun `postponed date is sent like toISOString`() {
        assertEquals("2026-11-07T10:30:00.000Z", postponedDateValue(Instant.parse("2026-11-07T10:30:00Z")))
    }

    @Test
    fun `postponed date limits follow the site`() {
        val now = Instant.parse("2026-11-07T10:00:00Z")
        assertEquals(PostProblem.PostponedTooSoon, validatePostponed(now.plusSeconds(8 * 60), now))
        assertEquals(null, validatePostponed(now.plusSeconds(10 * 60), now))
        assertEquals(null, validatePostponed(now.plusSeconds(7 * 86_400), now))
        assertEquals(PostProblem.PostponedTooLate, validatePostponed(now.plusSeconds(7 * 86_400 + 60), now))
    }

    @Test
    fun `default postponed date is one hour ahead without seconds`() {
        assertEquals(
            Instant.parse("2026-11-07T11:15:00Z"),
            defaultPostponedDate(Instant.parse("2026-11-07T10:15:42Z")),
        )
    }

    @Test
    fun `date and time are picked in the local zone`() {
        val zone = ZoneId.of("Asia/Novosibirsk")
        val at = Instant.parse("2026-11-07T20:30:00Z")
        assertEquals(
            Instant.parse("2026-11-09T20:30:00Z"),
            at.withLocalDate(LocalDate.of(2026, 11, 10), zone),
        )
        assertEquals(Instant.parse("2026-11-08T02:05:00Z"), at.withLocalTime(9, 5, zone))
    }
}
