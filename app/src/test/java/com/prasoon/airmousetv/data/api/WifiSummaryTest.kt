package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSummaryTest {

    @Test
    fun `names the 2_4 GHz band`() {
        assertEquals("Wi-Fi 2.4 GHz (2452 MHz), -67 dBm, 115 Mbps", wifiSummary(2452, -67, 115))
    }

    @Test
    fun `names the 5 GHz band`() {
        assertEquals("Wi-Fi 5 GHz (5180 MHz), -55 dBm, 433 Mbps", wifiSummary(5180, -55, 433))
    }

    @Test
    fun `names the 6 GHz band`() {
        assertEquals("Wi-Fi 6 GHz (5955 MHz), -60 dBm, 600 Mbps", wifiSummary(5955, -60, 600))
    }

    @Test
    fun `leaves out values the system does not report`() {
        // Android reports -127 dBm, and -1 for frequency and speed, when it has nothing
        assertEquals("Wi-Fi state unknown", wifiSummary(-1, -127, -1))
        assertEquals("Wi-Fi 5 GHz (5180 MHz)", wifiSummary(5180, null, null))
    }
}
