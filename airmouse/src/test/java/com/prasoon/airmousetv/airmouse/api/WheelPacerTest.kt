package com.prasoon.airmousetv.airmouse.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelPacerTest {

    @Test
    fun `a single notch goes out at once`() {
        val pacer = WheelPacer()
        assertEquals(-1f, pacer.add(-1f, nowMs = 1_000)!!, 0f)
        assertNull(pacer.nextDueMs)
    }

    @Test
    fun `notches spaced further apart than the gap each go out at once`() {
        val pacer = WheelPacer()
        assertEquals(1f, pacer.add(1f, 1_000)!!, 0f)
        assertEquals(1f, pacer.add(1f, 1_000 + WHEEL_GAP_MS)!!, 0f)
        assertEquals(1f, pacer.add(1f, 1_000 + 2 * WHEEL_GAP_MS)!!, 0f)
    }

    @Test
    fun `a quick drag is sent as one bigger turn when the gap is up`() {
        // The burst seen on the TV: seven notches within half a second, which SmartTube cut down to two
        val pacer = WheelPacer()
        assertEquals(-1f, pacer.add(-1f, 1_000)!!, 0f)
        for (t in listOf(1_060L, 1_120L, 1_180L)) assertNull(pacer.add(-1f, t))
        assertEquals(1_000 + WHEEL_GAP_MS, pacer.nextDueMs)
        assertEquals(-3f, pacer.flush(1_000 + WHEEL_GAP_MS)!!, 0f)
    }

    @Test
    fun `nothing held back goes out before its time`() {
        val pacer = WheelPacer()
        pacer.add(1f, 1_000)
        pacer.add(1f, 1_050)
        assertNull(pacer.flush(1_000 + WHEEL_GAP_MS - 1))
        assertEquals(1f, pacer.flush(1_000 + WHEEL_GAP_MS)!!, 0f)
    }

    @Test
    fun `notches keep being held while a long drag goes on, and every one of them arrives`() {
        val pacer = WheelPacer()
        var sent = 0f
        var t = 1_000L
        // A notch every 50 ms for two seconds, flushing whenever the pacer is due
        repeat(40) {
            pacer.add(-1f, t)?.let { sent += it }
            pacer.nextDueMs?.takeIf { it <= t }?.let { pacer.flush(t)?.let { sent += it } }
            t += 50
        }
        pacer.nextDueMs?.let { pacer.flush(it)?.let { sent += it } }
        assertEquals(-40f, sent, 0f)
    }

    @Test
    fun `turns are never sent closer together than the gap`() {
        val pacer = WheelPacer()
        val sentAt = mutableListOf<Long>()
        var t = 1_000L
        repeat(40) {
            if (pacer.add(1f, t) != null) sentAt += t
            pacer.nextDueMs?.takeIf { it <= t }?.let { if (pacer.flush(t) != null) sentAt += t }
            t += 30
        }
        sentAt.zipWithNext { a, b -> assertTrue("turns at $a and $b are too close", b - a >= WHEEL_GAP_MS) }
    }

    @Test
    fun `parts of a notch are added up, not lost`() {
        // The two-finger scroll sends a fraction of a notch on every touch frame
        val pacer = WheelPacer()
        assertEquals(0.2f, pacer.add(0.2f, 1_000)!!, 1e-6f)
        repeat(5) { i -> assertNull(pacer.add(0.2f, 1_010L + i * 16)) }
        assertEquals(1f, pacer.flush(1_000 + WHEEL_GAP_MS)!!, 1e-5f)
    }

    @Test
    fun `turns that cancel out send nothing`() {
        val pacer = WheelPacer()
        pacer.add(1f, 1_000)
        pacer.add(1f, 1_050)
        pacer.add(-1f, 1_100)
        assertNull(pacer.flush(1_000 + WHEEL_GAP_MS))
        assertNull(pacer.nextDueMs)
    }

    @Test
    fun `flushing with nothing held back sends nothing`() {
        assertNull(WheelPacer().flush(5_000))
    }

    @Test
    fun `reset forgets what was held back and the time of the last turn`() {
        val pacer = WheelPacer()
        pacer.add(1f, 1_000)
        pacer.add(1f, 1_050)
        pacer.reset()
        assertNull(pacer.flush(1_000 + WHEEL_GAP_MS))
        // The next turn goes out at once, however soon
        assertEquals(1f, pacer.add(1f, 1_060)!!, 0f)
    }
}
