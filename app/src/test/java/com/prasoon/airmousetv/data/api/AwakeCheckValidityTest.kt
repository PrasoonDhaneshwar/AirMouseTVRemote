package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwakeCheckValidityTest {

    @Test
    fun `a passed awake check holds for a few seconds`() {
        assertTrue(awakeCheckStillValid(now = 10_000, confirmedAt = 8_000, lastPowerAt = null))
        assertFalse(awakeCheckStillValid(now = 8_000 + AWAKE_CHECK_VALID_MS, confirmedAt = 8_000, lastPowerAt = null))
    }

    @Test
    fun `no awake check passed yet means it must run`() {
        assertFalse(awakeCheckStillValid(now = 10_000, confirmedAt = 0, lastPowerAt = null))
    }

    @Test
    fun `a Power tap after the check voids it`() {
        assertFalse(awakeCheckStillValid(now = 10_000, confirmedAt = 8_000, lastPowerAt = 9_000))
        assertTrue(awakeCheckStillValid(now = 10_000, confirmedAt = 8_000, lastPowerAt = 7_000))
    }
}
