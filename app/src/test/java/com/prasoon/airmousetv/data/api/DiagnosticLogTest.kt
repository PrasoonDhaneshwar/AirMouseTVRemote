package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticLogTest {

    @Test
    fun `keeps events in order`() {
        val log = DiagnosticLog(10)
        log.add("14:02:11", "TV pings")
        log.add("14:02:12", "sent HOME TAP")
        assertEquals("14:02:11 TV pings\n14:02:12 sent HOME TAP", log.report())
    }

    @Test
    fun `merges an event repeated back to back`() {
        val log = DiagnosticLog(10)
        log.add("14:03:52", "sent DOWN TAP")
        log.add("14:03:53", "sent DOWN TAP")
        log.add("14:03:55", "sent DOWN TAP")
        log.add("14:03:56", "sent OK TAP")
        log.add("14:03:57", "sent DOWN TAP")
        assertEquals(
            "14:03:52 sent DOWN TAP ×3, last at 14:03:55\n14:03:56 sent OK TAP\n14:03:57 sent DOWN TAP",
            log.report()
        )
        assertEquals(3, log.size)
    }

    @Test
    fun `drops the oldest line when full`() {
        val log = DiagnosticLog(2)
        log.add("1", "a")
        log.add("2", "b")
        log.add("3", "c")
        assertEquals("2 b\n3 c", log.report())
    }

    @Test
    fun `a merged repeat does not take a new line`() {
        val log = DiagnosticLog(2)
        log.add("1", "a")
        log.add("2", "b")
        repeat(5) { log.add("3", "b") }
        assertEquals("1 a\n2 b ×6, last at 3", log.report())
    }
}
