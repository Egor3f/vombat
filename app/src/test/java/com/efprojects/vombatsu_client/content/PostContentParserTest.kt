package com.efprojects.vombatsu_client.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostContentParserTest {
    @Test
    fun `doc paragraph keeps marks mention and break`() {
        val blocks = parsePostContent(
            """{"type":"doc","content":[{"type":"paragraph","content":[
                {"type":"text","text":"a","marks":[{"type":"bold"},{"type":"link","attrs":{"href":"https://x.y"}}]},
                {"type":"hardBreak"},
                {"type":"mention","attrs":{"id":1,"label":"GGDR"}},
                {"type":"text","text":"s","marks":[{"type":"spoiler"}]}
            ]}]}""",
        )
        assertEquals(
            listOf(
                Block.Paragraph(
                    listOf(
                        Inline("a", bold = true, link = "https://x.y"),
                        Inline("\n"),
                        Inline("@GGDR", link = "https://vombat.su/user/GGDR"),
                        Inline("s", spoiler = true),
                    ),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun `doc media image video failed and gallery`() {
        val blocks = parsePostContent(
            """{"type":"doc","content":[
                {"type":"media","attrs":{"mime":"image/jpeg","file":{"relative":"/images/post/big/a.jpg","info":{"width":700,"height":350,"mime":"image/jpeg"},"type":"image"}}},
                {"type":"media","attrs":{"mime":"video/mp4","file":{"relative":"videos/post/x/hls/playlist.m3u8","info":{"width":720,"height":1280,"mime":"video/mp4"},"thumbnail":{"relative":"images/post/x/thumb.jpg"},"type":"video"}}},
                {"type":"media","attrs":{"status":"error","file":null}},
                {"type":"gallery","attrs":{"items":[{"file":{"relative":"/images/b.jpg","type":"image","info":{"width":10,"height":10,"mime":"image/jpeg"}}}]}}
            ]}""",
        )
        assertEquals(Block.MediaBlock(Media.Image("/images/post/big/a.jpg", 700, 350)), blocks[0])
        assertEquals(
            Block.MediaBlock(
                Media.Video(
                    url = "https://video-2.vombat.su/videos/post/x/hls/playlist.m3u8",
                    thumbnailUrl = "https://img-2.vombat.su/images/post/x/thumb.jpg",
                    aspectRatio = 720f / 1280f,
                ),
            ),
            blocks[1],
        )
        assertTrue(blocks.none { it is Block.Unsupported })
        assertEquals(Block.Gallery(listOf(Media.Image("/images/b.jpg", 10, 10))), blocks[2])
    }

    @Test
    fun `schemeless link becomes https and others stay`() {
        assertEquals("https://www.youtube.com/@awildshott", absoluteLink("www.youtube.com/@awildshott"))
        assertEquals("https://vombat.su", absoluteLink("vombat.su"))
        assertEquals("https://x.y/z", absoluteLink("https://x.y/z"))
        assertEquals("mailto:a@b.c", absoluteLink("mailto:a@b.c"))
        assertEquals("/post/1", absoluteLink("/post/1"))
    }

    @Test
    fun `doc gif uses conversion endpoints and loops`() {
        val blocks = parsePostContent(
            """{"type":"doc","content":[{"type":"media","attrs":{"file":{"relative":"/images/a.gif","info":{"width":100,"height":100,"mime":"image/gif"}}}}]}""",
        )
        assertEquals(
            Block.MediaBlock(
                Media.Video(
                    url = "https://api.vombat.su/convert-gif/mp4/images/a.gif?class=max",
                    thumbnailUrl = "https://api.vombat.su/convert-gif/jpg/images/a.gif?class=max",
                    aspectRatio = 1f,
                    loop = true,
                ),
            ),
            blocks.single(),
        )
    }

    @Test
    fun `doc video embed`() {
        val blocks = parsePostContent(
            """{"type":"doc","content":[{"type":"videoEmbed","attrs":{"provider":"rutube","videoId":"abc","originalUrl":"https://rutube.ru/video/abc/"}}]}""",
        )
        assertEquals(
            Block.Embed("rutube", "https://rutube.ru/video/abc/", "https://rutube.ru/api/video/abc/thumbnail/?redirect=1"),
            blocks.single(),
        )
    }

    @Test
    fun `editorjs paragraph html`() {
        val blocks = parsePostContent(
            """{"time":1712434347,"blocks":[{"type":"paragraph","data":{"text":"a&nbsp;<b>b</b><br><a href=\"https://x.y\">c</a> <span class=\"z\">d</span>"}},{"type":"paragraph","data":{"text":" "}}]}""",
        )
        assertEquals(
            listOf(
                Block.Paragraph(
                    listOf(
                        Inline("a "),
                        Inline("b", bold = true),
                        Inline("\n"),
                        Inline("c", link = "https://x.y"),
                        Inline(" "),
                        Inline("d"),
                    ),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun `editorjs media embed list and video link`() {
        val blocks = parsePostContent(
            """{"blocks":[
                {"type":"image","data":{"file":{"url":"https://video2.vombat.su/videos/a.mp4","relative":"/videos/a.mp4","info":{"width":608,"height":1081,"mime":"video/mp4","duration":"14.5","thumbnail_path":"/images/video/a.jpg"}}}},
                {"type":"embed","data":{"service":"youtube","source":"https://www.youtube.com/watch?v=zHOhOKJudV8","embed":"https://www.youtube.com/embed/zHOhOKJudV8/"}},
                {"type":"embed","data":{"service":"vk","source":"https://vk.com/clip-1_2"}},
                {"type":"list","data":{"style":"ordered","items":["one"]}},
                {"type":"video-link","data":{"source":"https://x.y/v.webm","ratio":"0.5625","cover":""}}
            ]}""",
        )
        assertEquals(
            Media.Video("https://video-2.vombat.su/videos/a.mp4", "https://img-2.vombat.su/images/video/a.jpg", 608f / 1081f),
            (blocks[0] as Block.MediaBlock).media,
        )
        assertEquals(
            Block.Embed("youtube", "https://www.youtube.com/watch?v=zHOhOKJudV8", "https://img.youtube.com/vi/zHOhOKJudV8/hqdefault.jpg"),
            blocks[1],
        )
        assertEquals(Block.Embed("vk", "https://vk.com/clip-1_2", null), blocks[2])
        assertEquals(Block.Items(true, listOf(listOf(Block.Paragraph(listOf(Inline("one")))))), blocks[3])
        assertEquals(Block.MediaBlock(Media.Video("https://x.y/v.webm", null, 0.5625f)), blocks[4])
    }

    @Test
    fun `unknown formats do not crash`() {
        assertEquals(emptyList<Block>(), parsePostContent(null))
        assertEquals(listOf(Block.Unsupported("root")), parsePostContent("""{"foo":1}"""))
        assertEquals(listOf(Block.Paragraph(listOf(Inline("plain")))), parsePostContent("plain"))
        assertEquals(
            listOf(Block.Unsupported("newThing")),
            parsePostContent("""{"type":"doc","content":[{"type":"newThing"}]}"""),
        )
    }

    @Test
    fun `preview stops at third media`() {
        val image = Block.MediaBlock(Media.Image("/a", 1, 1))
        assertEquals(2, previewBlockCount(listOf(image, image, image, image)))
        assertEquals(2, previewBlockCount(listOf(image, image)))
        assertTrue(previewBlockCount(List(20) { Block.Rule }) < 20)
    }

    @Test
    fun `image url picks size and strips media prefix`() {
        assertEquals("https://img-2.vombat.su/md/images/a.jpg", imageUrl("/images/a.jpg", 900))
        assertEquals("https://img-2.vombat.su/xl/images/a.jpg", imageUrl("/media/images/a.jpg", 2000))
    }

    @Test
    fun `spoiler groups merge adjacent spoiler inlines`() {
        val inlines = listOf(
            Inline("plain "),
            Inline("first ", spoiler = true),
            Inline("bold", spoiler = true, bold = true),
            Inline(" between "),
            Inline("second", spoiler = true),
        )
        assertEquals(listOf(null, 0, 0, null, 1), spoilerGroups(inlines))
    }

    @Test
    fun `video job status`() {
        val body = """{"jobs":[{"id":1,"status":"processing"},{"id":2,"status":"completed","playlist_key":"v/a.m3u8","thumbnail_key":null}]}"""
        assertEquals(VideoJob("completed", "v/a.m3u8", null), parseVideoJob(body, "2"))
        assertEquals(false, parseVideoJob(body, "1")?.finished)
        assertEquals(null, parseVideoJob(body, "3"))
        assertEquals(null, parseVideoJob("<html>", "1"))
    }
}
