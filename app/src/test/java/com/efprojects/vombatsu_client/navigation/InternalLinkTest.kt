package com.efprojects.vombatsu_client.navigation

import com.efprojects.vombatsu_client.content.Inline
import com.efprojects.vombatsu_client.content.linkifyPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternalLinkTest {
    @Test
    fun `site links map to screens`() {
        assertEquals(InternalLink.Post(109602), parseInternalLink("https://vombat.su/post/109602-intervyu-s-glavnim"))
        assertEquals(InternalLink.Post(5), parseInternalLink("https://www.vombat.su/post/5?x=1#c"))
        assertEquals(InternalLink.User("GGDR"), parseInternalLink("https://vombat.su/user/GGDR"))
        assertEquals(InternalLink.Tag("Юмор"), parseInternalLink("https://vombat.su/tag/%D0%AE%D0%BC%D0%BE%D1%80"))
        assertEquals(InternalLink.Tag("Юмор"), parseInternalLink("https://vombat.su/tag/Юмор/new"))
    }

    @Test
    fun `other links stay external`() {
        assertNull(parseInternalLink("https://rutube.ru/video/abc/"))
        assertNull(parseInternalLink("https://vombat.su/hot/week"))
        assertNull(parseInternalLink("https://vombat.su/post/abc"))
        assertNull(parseInternalLink("https://notvombat.su/post/1"))
    }

    @Test
    fun `plain text links`() {
        assertEquals(
            listOf(Inline("ТГ: "), Inline("https://t.me/a", link = "https://t.me/a"), Inline(".\nконец")),
            linkifyPlainText("ТГ: https://t.me/a.\nконец"),
        )
        assertEquals(listOf(Inline("нет ссылок")), linkifyPlainText("нет ссылок"))
    }
}
