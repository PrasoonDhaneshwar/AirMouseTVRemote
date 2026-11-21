package com.prasoon.airmousetv.airmouse.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class OneEuroFilterTest {

    /** 100 samples a second, like the gyroscope. */
    private val dt = 0.01f

    @Test
    fun `the first value passes through unchanged`() {
        assertEquals(3f, OneEuroFilter(1f, 0.5f).filter(3f, dt), 0f)
    }

    @Test
    fun `a steady signal comes out as it went in`() {
        val filter = OneEuroFilter(1f, 0.5f)
        val out = (1..200).map { filter.filter(0.8f, dt) }.last()
        assertEquals(0.8f, out, 1e-4f)
    }

    @Test
    fun `tremor around a still hand is mostly removed`() {
        val filter = OneEuroFilter(1f, 0f)
        // 10 Hz, the middle of the range hand tremor sits in
        val out = (0 until 300).map { i -> filter.filter(0.05f * sin(2 * PI * 10 * i * dt).toFloat(), dt) }
        val settled = out.drop(100).maxOf { abs(it) }
        assertTrue("tremor of 0.05 came through as $settled", settled < 0.01f)
    }

    @Test
    fun `a step is followed, not overshot`() {
        val filter = OneEuroFilter(1f, 0.5f)
        filter.filter(0f, dt)
        val out = (1..300).map { filter.filter(2f, dt) }
        assertTrue(out.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(out.all { it <= 2f })
        assertEquals(2f, out.last(), 0.01f)
    }

    @Test
    fun `a fast change lags less with a higher beta`() {
        val steady = OneEuroFilter(1f, 0f)
        val responsive = OneEuroFilter(1f, 1f)
        steady.filter(0f, dt)
        responsive.filter(0f, dt)
        // A flick: the turning speed climbs 0.2 rad/s every sample
        var slow = 0f
        var fast = 0f
        for (i in 1..10) {
            slow = steady.filter(0.2f * i, dt)
            fast = responsive.filter(0.2f * i, dt)
        }
        assertTrue("responsive $fast should be closer to 2.0 than steady $slow", fast > slow + 0.2f)
    }

    @Test
    fun `a lower cutoff smooths more`() {
        val light = OneEuroFilter(4f, 0f)
        val heavy = OneEuroFilter(0.8f, 0f)
        light.filter(0f, dt)
        heavy.filter(0f, dt)
        assertTrue(heavy.filter(1f, dt) < light.filter(1f, dt))
    }

    @Test
    fun `reset forgets the signal so far`() {
        val filter = OneEuroFilter(1f, 0.5f)
        repeat(50) { filter.filter(5f, dt) }
        filter.reset()
        assertEquals(-1f, filter.filter(-1f, dt), 0f)
    }

    @Test
    fun `a sample with no time since the last one restarts the filter`() {
        val filter = OneEuroFilter(1f, 0.5f)
        filter.filter(5f, dt)
        assertEquals(1f, filter.filter(1f, 0f), 0f)
    }
}
