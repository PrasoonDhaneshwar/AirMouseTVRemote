package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSilenceTest {

    @Test
    fun `a pinging TV heard from recently needs no check`() {
        assertFalse(needsAwakeCheck(now = 100_000, lastHeardAt = 95_000, tvPings = true, lastPowerAt = null))
    }

    @Test
    fun `a pinging TV that went silent needs a check`() {
        assertTrue(needsAwakeCheck(now = 100_000, lastHeardAt = 100_000 - SILENCE_SUSPECT_MS - 1, tvPings = true, lastPowerAt = null))
    }

    @Test
    fun `silence from a TV that never pings is not suspect by itself`() {
        assertFalse(needsAwakeCheck(now = 100_000, lastHeardAt = 10_000, tvPings = false, lastPowerAt = null))
    }

    @Test
    fun `keys right after a Power tap need a check even if the TV spoke since`() {
        // The TV can send a last ping or a screen-off notice while it is switching off
        assertTrue(needsAwakeCheck(now = 102_000, lastHeardAt = 101_000, tvPings = true, lastPowerAt = 100_000))
    }

    @Test
    fun `a TV still talking well after a Power tap stayed on the network`() {
        val power = 100_000L
        val heard = power + STILL_TALKING_AFTER_POWER_MS
        assertFalse(mayBeSwitchingOff(lastHeardAt = heard, lastPowerAt = power))
        assertFalse(needsAwakeCheck(now = heard + 1_000, lastHeardAt = heard, tvPings = true, lastPowerAt = power))
    }

    @Test
    fun `a connection lost soon after Power, with the TV quiet since, is a power-off`() {
        assertTrue(lostAfterPowerOff(now = 160_000, lastHeardAt = 101_000, lastPowerAt = 100_000))
    }

    @Test
    fun `a connection lost long after Power is not a power-off`() {
        assertFalse(lostAfterPowerOff(now = 100_000 + POWER_OFF_DROP_WINDOW_MS + 1, lastHeardAt = 101_000, lastPowerAt = 100_000))
    }

    @Test
    fun `a connection lost while the TV kept talking after Power is not a power-off`() {
        assertFalse(lostAfterPowerOff(now = 160_000, lastHeardAt = 150_000, lastPowerAt = 100_000))
        assertFalse(lostAfterPowerOff(now = 160_000, lastHeardAt = 150_000, lastPowerAt = null))
    }

    @Test
    fun `a Power tap soon after the last one is a repeat`() {
        assertTrue(isRepeatPowerTap(now = 100_000 + POWER_REPEAT_GUARD_MS - 1, lastPowerAt = 100_000))
    }

    @Test
    fun `the first Power tap and a later one are not repeats`() {
        assertFalse(isRepeatPowerTap(now = 100_000, lastPowerAt = null))
        assertFalse(isRepeatPowerTap(now = 100_000 + POWER_REPEAT_GUARD_MS, lastPowerAt = 100_000))
    }
}
