package com.prasoon.airmousetv.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class) // currentTime
class ProbeUntilAnsweredTest {

    @Test
    fun `stops at the first probe that gets an answer`() = runTest {
        var calls = 0
        val result = probeUntilAnswered(18.seconds, 500.milliseconds) {
            calls++
            if (calls < 3) HostProbe.UNREACHABLE else HostProbe.OPEN
        }
        assertEquals(HostProbe.OPEN, result)
        assertEquals(3, calls)
    }

    @Test
    fun `a refusal ends it too, since the host did answer`() = runTest {
        var calls = 0
        val result = probeUntilAnswered(18.seconds, 500.milliseconds) { calls++; HostProbe.REFUSED }
        assertEquals(HostProbe.REFUSED, result)
        assertEquals(1, calls)
    }

    @Test
    fun `gives up as unreachable once the window has passed`() = runTest {
        val result = probeUntilAnswered(18.seconds, 500.milliseconds) {
            delay(3_500) // a probe that times out
            HostProbe.UNREACHABLE
        }
        assertEquals(HostProbe.UNREACHABLE, result)
        assertEquals(18_000, currentTime)
    }

    @Test
    fun `waits between probes that fail at once`() = runTest {
        var calls = 0
        probeUntilAnswered(2.seconds, 500.milliseconds) { calls++; HostProbe.UNREACHABLE }
        // Probes at 0, 500, 1000 and 1500 ms; none at 2000, when the window closes
        assertEquals(4, calls)
    }
}
