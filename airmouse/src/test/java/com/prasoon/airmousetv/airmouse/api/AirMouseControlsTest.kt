package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.AirMouseSettings
import com.prasoon.airmousetv.airmouse.model.VolumeButtons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirMouseControlsTest {

    // ----- Sliders -----

    @Test
    fun `the middle of the sensitivity slider is the default speed`() {
        assertEquals(1f, AirMouseSettings(sensitivity = 0.5f).toPointerSettings().sensitivity, 1e-4f)
    }

    @Test
    fun `the ends of the sensitivity slider are half and double speed`() {
        assertEquals(LOWEST_SENSITIVITY, AirMouseSettings(sensitivity = 0f).toPointerSettings().sensitivity, 1e-4f)
        assertEquals(HIGHEST_SENSITIVITY, AirMouseSettings(sensitivity = 1f).toPointerSettings().sensitivity, 1e-4f)
    }

    @Test
    fun `each quarter of the slider is the same factor faster`() {
        val speeds = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { AirMouseSettings(sensitivity = it).toPointerSettings().sensitivity }
        val steps = speeds.zipWithNext { a, b -> b / a }
        steps.forEach { assertEquals(steps.first(), it, 1e-3f) }
    }

    @Test
    fun `a slider position from outside its range is brought back in`() {
        assertEquals(HIGHEST_SENSITIVITY, AirMouseSettings(sensitivity = 7f).toPointerSettings().sensitivity, 1e-4f)
        assertEquals(0f, AirMouseSettings(smoothAir = -1f).toPointerSettings().smoothing, 0f)
    }

    @Test
    fun `Smooth Air is passed on as it is`() {
        assertEquals(0.8f, AirMouseSettings(smoothAir = 0.8f).toPointerSettings().smoothing, 0f)
    }

    // ----- Volume buttons -----

    private val defaults = AirMouseSettings()

    @Test
    fun `while pointing, volume up clicks and volume down is Back`() {
        assertEquals(VolumeAction.CLICK, volumeAction(volumeUp = true, defaults, pointing = true))
        assertEquals(VolumeAction.BACK, volumeAction(volumeUp = false, defaults, pointing = true))
    }

    @Test
    fun `swapped, volume down clicks and volume up is Back`() {
        val swapped = defaults.copy(volumeButtons = VolumeButtons.SWAPPED)
        assertEquals(VolumeAction.CLICK, volumeAction(volumeUp = false, swapped, pointing = true))
        assertEquals(VolumeAction.BACK, volumeAction(volumeUp = true, swapped, pointing = true))
    }

    @Test
    fun `the buttons can be left to the TV's volume`() {
        val forTv = defaults.copy(volumeButtons = VolumeButtons.TV_VOLUME)
        assertEquals(VolumeAction.VOLUME, volumeAction(volumeUp = true, forTv, pointing = true))
        assertEquals(VolumeAction.VOLUME, volumeAction(volumeUp = false, forTv, pointing = true))
    }

    @Test
    fun `away from the pad the buttons are always the TV's volume`() {
        for (choice in VolumeButtons.entries) {
            val settings = defaults.copy(volumeButtons = choice)
            assertEquals(VolumeAction.VOLUME, volumeAction(volumeUp = true, settings, pointing = false))
            assertEquals(VolumeAction.VOLUME, volumeAction(volumeUp = false, settings, pointing = false))
        }
    }

    @Test
    fun `with the pad used as a mouse pad the buttons are the TV's volume`() {
        val mousePad = defaults.copy(airPointing = false)
        assertEquals(VolumeAction.VOLUME, volumeAction(volumeUp = true, mousePad, pointing = true))
        assertEquals(VolumeAction.VOLUME, volumeAction(volumeUp = false, mousePad, pointing = true))
    }

    // ----- Recenter and wake -----

    @Test
    fun `recentering first pushes the pointer further than any screen is wide`() {
        val sweep = recenterMoves().takeWhile { it.dx < 0f }
        assertTrue(-sweep.sumOf { it.dx.toDouble() } >= 3840)
        assertTrue(-sweep.sumOf { it.dy.toDouble() } >= 2160)
    }

    @Test
    fun `then walks it back by the distance that is the middle of the screen`() {
        val back = recenterMoves().dropWhile { it.dx < 0f }
        assertEquals(RECENTER_TO_MIDDLE_X, back.sumOf { it.dx.toDouble() }.toFloat(), 0.5f)
        assertEquals(RECENTER_TO_MIDDLE_Y, back.sumOf { it.dy.toDouble() }.toFloat(), 0.5f)
    }

    @Test
    fun `the way back is in small steps, so the TV doesn't speed the pointer up`() {
        val back = recenterMoves().dropWhile { it.dx < 0f }
        assertTrue(back.all { it.dx <= RECENTER_RETURN_STEP + 0.01f && it.dy <= RECENTER_RETURN_STEP })
    }

    @Test
    fun `every recenter movement fits in one mouse report`() {
        assertTrue(recenterMoves().all { HidMouse().move(it.dx, it.dy).size <= 1 })
    }

    @Test
    fun `waking the pointer leaves it where it was`() {
        val wake = wakeMoves()
        assertTrue(wake.isNotEmpty())
        assertEquals(0f, wake.sumOf { it.dx.toDouble() }.toFloat(), 0f)
        assertEquals(0f, wake.sumOf { it.dy.toDouble() }.toFloat(), 0f)
    }

    // ----- Calibration -----

    private fun calibrationOf(vararg readings: Vec3, times: Int = 200): Vec3? {
        val calibration = GyroCalibration()
        repeat(times) { i -> calibration.add(readings[i % readings.size]) }
        return calibration.result()
    }

    @Test
    fun `a still phone's readings average to its drift`() {
        // Noise around a constant offset
        val bias = calibrationOf(Vec3(0.011f, -0.02f, 0.004f), Vec3(0.009f, -0.02f, 0.006f))
        assertNotNull(bias)
        assertEquals(0.01f, bias!!.x, 1e-4f)
        assertEquals(-0.02f, bias.y, 1e-4f)
        assertEquals(0.005f, bias.z, 1e-4f)
    }

    @Test
    fun `a phone that was moved gives no calibration`() {
        assertNull(calibrationOf(Vec3(0f, 0f, 0f), Vec3(0f, 0f, CALIBRATION_STILL_RAD_S * 2f)))
    }

    @Test
    fun `one bump is enough to spoil it`() {
        val calibration = GyroCalibration()
        repeat(150) { calibration.add(Vec3(0.01f, 0f, 0f)) }
        calibration.add(Vec3(0.5f, 0f, 0f))
        repeat(150) { calibration.add(Vec3(0.01f, 0f, 0f)) }
        assertNull(calibration.result())
    }

    @Test
    fun `too few readings give no calibration`() {
        assertNull(calibrationOf(Vec3(0.01f, 0f, 0f), times = CALIBRATION_MIN_SAMPLES - 1))
        assertNotNull(calibrationOf(Vec3(0.01f, 0f, 0f), times = CALIBRATION_MIN_SAMPLES))
    }

    @Test
    fun `no readings at all give no calibration`() {
        assertNull(GyroCalibration().result())
    }
}
