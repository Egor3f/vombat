package com.efprojects.vombatsu_client.battle

import org.junit.Assert.assertEquals
import org.junit.Test

class BugFlightTest {
    private val minScale = 0.01f

    @Test
    fun `bug reaches the target before the animation ends`() {
        assertEquals(0f, bugTravel(0f), 0f)
        assertEquals(1f, bugTravel(0.9f), 0.0001f)
        assertEquals(1f, bugTravel(1f), 0f)
    }

    @Test
    fun `bug keeps its size until it gets close`() {
        assertEquals(1f, bugScale(300f, 100f, minScale), 0f)
        assertEquals(1f, bugScale(100f, 100f, minScale), 0f)
    }

    @Test
    fun `bug is tiny exactly at the center and not earlier`() {
        assertEquals(0.5f, bugScale(50f, 100f, minScale), 0.0001f)
        assertEquals(minScale, bugScale(0f, 100f, minScale), 0f)
    }
}
