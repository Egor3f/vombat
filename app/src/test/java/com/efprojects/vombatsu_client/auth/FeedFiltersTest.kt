package com.efprojects.vombatsu_client.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedFiltersTest {
    @Test
    fun `parses known flags and ignores the rest`() {
        assertEquals(
            FeedFilters(showNsfw = true, hideViewed = true),
            parseFeedFilters("""{"show_nsfw":true,"show_cruel":false,"hide_viewed":true,"font_size":3}"""),
        )
    }

    @Test
    fun `bad settings give defaults`() {
        assertEquals(FeedFilters(), parseFeedFilters(null))
        assertEquals(FeedFilters(), parseFeedFilters("not json"))
    }

    @Test
    fun `diff has only changed keys`() {
        assertEquals(
            """{"hide_viewed":false}""",
            feedFiltersDiff(FeedFilters(hideViewed = true, showNsfw = true), FeedFilters(showNsfw = true)),
        )
    }
}
