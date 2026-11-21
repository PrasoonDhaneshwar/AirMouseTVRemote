package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.model.TvKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollKeysTest {

    @Test
    fun `a drag scrolls along the axis it began on and drops the other`() {
        val lock = ScrollAxisLock(decideAfter = 10f)
        // Nothing until the finger has travelled far enough to tell
        assertEquals(0f to 0f, lock.add(2f, 1f))
        // Then everything dragged so far on the chosen axis, and nothing sideways
        assertEquals(12f to 0f, lock.add(10f, 6f))
        assertEquals(5f to 0f, lock.add(5f, 20f))
    }

    @Test
    fun `a vertical drag keeps only its vertical part`() {
        val lock = ScrollAxisLock(decideAfter = 10f)
        assertEquals(0f to 12f, lock.add(3f, 12f))
        assertEquals(0f to -4f, lock.add(2f, -4f))
    }

    @Test
    fun `the next drag may choose the other axis`() {
        val lock = ScrollAxisLock(decideAfter = 10f)
        lock.add(20f, 0f)
        lock.reset()
        assertEquals(0f to 15f, lock.add(0f, 15f))
    }

    @Test
    fun `scrolling up sends Up and scrolling down sends Down`() {
        assertEquals(listOf(TvKey.UP), ScrollKeys().add(0f, 1f))
        assertEquals(listOf(TvKey.DOWN, TvKey.DOWN), ScrollKeys().add(0f, -2f))
    }

    @Test
    fun `scrolling sideways sends Left and Right`() {
        assertEquals(listOf(TvKey.RIGHT), ScrollKeys().add(1f, 0f))
        assertEquals(listOf(TvKey.LEFT), ScrollKeys().add(-1f, 0f))
    }

    @Test
    fun `part of a notch is held back until it adds up`() {
        val keys = ScrollKeys()
        assertTrue(keys.add(0f, 0.4f).isEmpty())
        assertEquals(listOf(TvKey.UP), keys.add(0f, 0.4f))
    }

    @Test
    fun `a fast flick is capped`() {
        assertEquals(MAX_SCROLL_KEYS_PER_MOVE, ScrollKeys().add(0f, 50f).size)
    }

    @Test
    fun `reset forgets what was carried`() {
        val keys = ScrollKeys()
        keys.add(0f, 0.4f)
        keys.reset()
        assertTrue(keys.add(0f, 0.4f).isEmpty())
    }
}
