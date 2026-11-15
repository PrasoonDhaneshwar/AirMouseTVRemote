package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.data.model.TvKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollKeysTest {

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
