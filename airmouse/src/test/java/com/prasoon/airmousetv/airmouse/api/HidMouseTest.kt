package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.PointerButton
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidMouseTest {

    private fun report(buttons: Int = 0, x: Int = 0, y: Int = 0, wheel: Int = 0, pan: Int = 0) =
        byteArrayOf(buttons.toByte(), x.toByte(), y.toByte(), wheel.toByte(), pan.toByte())

    /** Adds up what a list of reports moves the pointer by. */
    private fun List<ByteArray>.travel() = sumOf { it[1].toInt() } to sumOf { it[2].toInt() }

    @Test
    fun `a small movement is one report`() {
        val reports = HidMouse().move(12f, -3f)
        assertEquals(1, reports.size)
        assertArrayEquals(report(x = 12, y = -3), reports[0])
    }

    @Test
    fun `no movement sends nothing`() {
        assertTrue(HidMouse().move(0f, 0f).isEmpty())
    }

    @Test
    fun `movement under one count is held back until it adds up`() {
        val mouse = HidMouse()
        assertTrue(mouse.move(0.3f, 0f).isEmpty())
        // 0.6 rounds to a whole count, leaving -0.4 owed
        assertArrayEquals(report(x = 1), mouse.move(0.3f, 0f).single())
        assertTrue(mouse.move(0.3f, 0f).isEmpty())
    }

    @Test
    fun `a slow sweep covers the distance the hand moved`() {
        val mouse = HidMouse()
        val moved = (1..100).sumOf { mouse.move(0.25f, -0.25f).travel().first }
        assertEquals(25, moved)
    }

    @Test
    fun `a movement too big for one report is split`() {
        val reports = HidMouse().move(300f, 0f)
        assertEquals(listOf(127, 127, 46), reports.map { it[1].toInt() })
    }

    @Test
    fun `a split diagonal keeps its direction in every report`() {
        val reports = HidMouse().move(300f, -150f)
        assertEquals(300 to -150, reports.travel())
        // Each full report has the same 2:1 slope as the whole movement
        assertArrayEquals(report(x = 127, y = -63), reports[0])
    }

    @Test
    fun `no report goes past what the descriptor allows`() {
        val reports = HidMouse().move(-1000f, 1000f)
        assertTrue(reports.all { it[1] in -HID_AXIS_MAX..HID_AXIS_MAX && it[2] in -HID_AXIS_MAX..HID_AXIS_MAX })
    }

    @Test
    fun `a huge movement is cut off rather than sent late`() {
        val reports = HidMouse().move(100_000f, 0f)
        assertEquals(MAX_REPORTS_PER_MOVE, reports.size)
    }

    @Test
    fun `what was cut off is not sent with the next movement`() {
        val mouse = HidMouse()
        mouse.move(100_000f, 0f)
        assertArrayEquals(report(x = 5), mouse.move(5f, 0f).single())
    }

    @Test
    fun `pressing and releasing a button each send one report`() {
        val mouse = HidMouse()
        assertArrayEquals(report(buttons = 0x01), mouse.button(PointerButton.PRIMARY, down = true))
        assertArrayEquals(report(buttons = 0x00), mouse.button(PointerButton.PRIMARY, down = false))
    }

    @Test
    fun `the right button is the second bit`() {
        assertArrayEquals(report(buttons = 0x02), HidMouse().button(PointerButton.SECONDARY, down = true))
    }

    @Test
    fun `a button already in that state sends nothing`() {
        val mouse = HidMouse()
        assertNull(mouse.button(PointerButton.PRIMARY, down = false))
        mouse.button(PointerButton.PRIMARY, down = true)
        assertNull(mouse.button(PointerButton.PRIMARY, down = true))
    }

    @Test
    fun `a held button goes out with every movement, which makes a drag`() {
        val mouse = HidMouse()
        mouse.button(PointerButton.PRIMARY, down = true)
        assertArrayEquals(report(buttons = 0x01, x = 10, y = 4), mouse.move(10f, 4f).single())
    }

    @Test
    fun `both buttons can be held at once and released separately`() {
        val mouse = HidMouse()
        mouse.button(PointerButton.PRIMARY, down = true)
        assertArrayEquals(report(buttons = 0x03), mouse.button(PointerButton.SECONDARY, down = true))
        assertArrayEquals(report(buttons = 0x02), mouse.button(PointerButton.PRIMARY, down = false))
    }

    @Test
    fun `scrolling turns the wheel and moves nothing`() {
        assertArrayEquals(report(wheel = 1), HidMouse().scroll(0f, 1f).single())
        assertArrayEquals(report(wheel = -2), HidMouse().scroll(0f, -2f).single())
    }

    @Test
    fun `scrolling sideways turns the pan wheel only`() {
        assertArrayEquals(report(pan = 1), HidMouse().scroll(1f, 0f).single())
        assertArrayEquals(report(pan = -3), HidMouse().scroll(-3f, 0f).single())
    }

    @Test
    fun `a diagonal scroll is one report with both wheels`() {
        assertArrayEquals(report(wheel = 2, pan = -1), HidMouse().scroll(-1f, 2f).single())
    }

    @Test
    fun `part of a notch is held back until it adds up`() {
        val mouse = HidMouse()
        assertTrue(mouse.scroll(0f, 0.4f).isEmpty())
        assertArrayEquals(report(wheel = 1), mouse.scroll(0f, 0.4f).single())
    }

    @Test
    fun `reset lets go of held buttons`() {
        val mouse = HidMouse()
        mouse.button(PointerButton.PRIMARY, down = true)
        assertArrayEquals(report(buttons = 0x00), mouse.reset())
        // Released for good: the next movement is not a drag
        assertArrayEquals(report(x = 3), mouse.move(3f, 0f).single())
    }

    @Test
    fun `reset with nothing held sends nothing`() {
        assertNull(HidMouse().reset())
    }

    @Test
    fun `reset forgets movement that was still owed`() {
        val mouse = HidMouse()
        mouse.move(0.4f, 0f)
        mouse.reset()
        assertTrue(mouse.move(0.4f, 0f).isEmpty())
    }

    @Test
    fun `the descriptor declares the report the mouse sends`() {
        val descriptor = HID_MOUSE_DESCRIPTOR.map { it.toInt() and 0xFF }
        // Usage Page (Generic Desktop), Usage (Mouse): what makes the TV draw a pointer
        assertEquals(listOf(0x05, 0x01, 0x09, 0x02), descriptor.take(4))
        // Report ID item, followed by the ID the reports are sent under
        assertEquals(HID_MOUSE_REPORT_ID, descriptor[descriptor.indexOf(0x85) + 1])
        // Collections opened are closed again, or the TV rejects the whole table
        assertEquals(descriptor.count { it == 0xA1 }, descriptor.count { it == 0xC0 })
    }
}
