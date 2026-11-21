package com.prasoon.airmousetv.airmouse.api

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PointerMotionTest {

    private val tolerance = 1e-5f

    /** The quaternion of a phone turned by [degrees] around the axis [x], [y], [z], from lying flat. */
    private fun turned(degrees: Double, x: Float, y: Float, z: Float): Vec3 {
        val half = Math.toRadians(degrees) / 2
        val s = sin(half).toFloat()
        return upInDeviceFrame(x * s, y * s, z * s, cos(half).toFloat())
    }

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
        assertEquals(expected.z, actual.z, tolerance)
    }

    private fun assertRates(yaw: Float, pitch: Float, actual: PointingRates) {
        assertEquals(yaw, actual.yaw, tolerance)
        assertEquals(pitch, actual.pitch, tolerance)
    }

    // ----- Which way is up -----

    @Test
    fun `up is out of the screen for a phone lying flat`() {
        assertVec(Vec3(0f, 0f, 1f), upInDeviceFrame(0f, 0f, 0f, 1f))
    }

    @Test
    fun `up is towards the top edge for a phone standing upright`() {
        // Tilted back 90 degrees around its x axis, so the screen faces the user
        assertVec(Vec3(0f, 1f, 0f), turned(90.0, 1f, 0f, 0f))
    }

    @Test
    fun `up is towards the left edge for a phone rolled onto its right edge`() {
        assertVec(Vec3(-1f, 0f, 0f), turned(90.0, 0f, 1f, 0f))
    }

    @Test
    fun `turning on the table doesn't change which way is up`() {
        assertVec(Vec3(0f, 0f, 1f), turned(73.0, 0f, 0f, 1f))
    }

    // ----- Yaw and pitch -----

    @Test
    fun `a flat phone turns left around z and tilts up around x`() {
        val flat = Vec3(0f, 0f, 1f)
        assertRates(yaw = 1f, pitch = 0f, pointingRates(Vec3(0f, 0f, 1f), flat))
        assertRates(yaw = 0f, pitch = 1f, pointingRates(Vec3(1f, 0f, 0f), flat))
    }

    @Test
    fun `twisting the phone along its length moves nothing`() {
        assertRates(yaw = 0f, pitch = 0f, pointingRates(Vec3(0f, 2f, 0f), Vec3(0f, 0f, 1f)))
    }

    @Test
    fun `a sideways sweep stays sideways with the phone rolled onto its edge`() {
        val onRightEdge = turned(90.0, 0f, 1f, 0f)
        // Turning left around the vertical is now a turn around the phone's -x axis
        assertRates(yaw = 1f, pitch = 0f, pointingRates(Vec3(-1f, 0f, 0f), onRightEdge))
        // And tilting the top edge up is a turn around its z axis
        assertRates(yaw = 0f, pitch = 1f, pointingRates(Vec3(0f, 0f, 1f), onRightEdge))
    }

    @Test
    fun `a sideways sweep stays sideways at a half roll`() {
        val rolled = turned(45.0, 0f, 1f, 0f)
        // A pure turn around the vertical, written in the rolled phone's axes
        val r = sqrt(0.5f)
        assertRates(yaw = 1f, pitch = 0f, pointingRates(Vec3(-r, 0f, r), rolled))
    }

    @Test
    fun `a sideways sweep stays sideways with the phone pointed up at the TV`() {
        // Top edge raised 40 degrees, as when pointing at a TV on the wall
        val raised = turned(40.0, 1f, 0f, 0f)
        val rates = pointingRates(raised, raised)
        // Turning around the vertical itself: all yaw, no pitch
        assertRates(yaw = 1f, pitch = 0f, rates)
    }

    @Test
    fun `without a rotation sensor the phone's own axes are used`() {
        assertRates(yaw = 0.5f, pitch = -0.25f, pointingRates(Vec3(-0.25f, 9f, 0.5f), up = null))
    }

    @Test
    fun `a phone pointing straight up falls back to its own axes`() {
        // Sideways can't be told from the top edge when the top edge is the vertical
        assertRates(yaw = 0.5f, pitch = -0.25f, pointingRates(Vec3(-0.25f, 9f, 0.5f), Vec3(0f, 1f, 0f)))
    }

    // ----- Gain -----

    @Test
    fun `slow movements get the slow gain and fast ones the fast gain`() {
        assertEquals(0.6f, acceleratedGain(0f, 1f, 3f, 0.6f, 1.8f), tolerance)
        assertEquals(0.6f, acceleratedGain(1f, 1f, 3f, 0.6f, 1.8f), tolerance)
        assertEquals(1.8f, acceleratedGain(3f, 1f, 3f, 0.6f, 1.8f), tolerance)
        assertEquals(1.8f, acceleratedGain(50f, 1f, 3f, 0.6f, 1.8f), tolerance)
    }

    @Test
    fun `the gain rises evenly between the two speeds`() {
        assertEquals(1.2f, acceleratedGain(2f, 1f, 3f, 0.6f, 1.8f), tolerance)
        assertEquals(0.9f, acceleratedGain(1.5f, 1f, 3f, 0.6f, 1.8f), tolerance)
    }
}
