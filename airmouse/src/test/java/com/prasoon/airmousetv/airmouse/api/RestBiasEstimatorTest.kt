package com.prasoon.airmousetv.airmouse.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RestBiasEstimatorTest {
    private val stepNs = 10_000_000L

    /** Feeds [seconds] of 100 Hz readings from [reading] and returns the first drift handed back, if any. */
    private fun RestBiasEstimator.feed(seconds: Float, startNs: Long = 0L, reading: (Int) -> Vec3): Vec3? {
        var result: Vec3? = null
        for (i in 0 until (seconds * 100).toInt()) {
            add(MotionSample(startNs + i * stepNs, reading(i), up = null))?.let { if (result == null) result = it }
        }
        return result
    }

    private val resting = Vec3(0.004f, -0.003f, 0.001f)

    @Test
    fun `a phone lying still gives its drift after a couple of seconds`() {
        val drift = RestBiasEstimator().feed(2.5f) { resting }
        assertNotNull(drift)
        assertEquals(resting.x, drift!!.x, 1e-4f)
        assertEquals(resting.y, drift.y, 1e-4f)
        assertEquals(resting.z, drift.z, 1e-4f)
    }

    @Test
    fun `sensor noise around the drift averages out`() {
        val drift = RestBiasEstimator().feed(2.5f) { i -> Vec3(resting.x + if (i % 2 == 0) 0.003f else -0.003f, resting.y, resting.z) }
        assertEquals(resting.x, drift!!.x, 5e-4f)
    }

    @Test
    fun `too short a rest gives nothing`() {
        assertNull(RestBiasEstimator().feed(1.5f) { resting })
    }

    @Test
    fun `a hand's tremor never counts as rest`() {
        assertNull(RestBiasEstimator().feed(10f) { i -> Vec3(if (i % 2 == 0) 0.04f else -0.04f, 0f, 0f) })
    }

    @Test
    fun `a movement restarts the wait`() {
        val estimator = RestBiasEstimator()
        // Still for 1.5 s, then a bump, then still again: the second rest has to last in full
        estimator.feed(1.5f) { resting }
        estimator.add(MotionSample(1_500_000_000L, Vec3(0.5f, 0f, 0f), null))
        assertNull(estimator.feed(1.5f, startNs = 1_510_000_000L) { resting })
        assertNotNull(estimator.feed(1f, startNs = 3_010_000_000L) { resting })
    }

    @Test
    fun `a slow steady turn is not a drift`() {
        assertNull(RestBiasEstimator().feed(3f) { Vec3(0.08f, 0f, 0f) })
    }

    @Test
    fun `a rest gives its drift once, not on every reading after it`() {
        val estimator = RestBiasEstimator()
        estimator.feed(2.5f) { resting }
        assertNull(estimator.feed(1f, startNs = 2_500_000_000L) { resting })
    }

    @Test
    fun `a gap in the readings starts over`() {
        val estimator = RestBiasEstimator()
        estimator.feed(1.5f) { resting }
        // The sensor stalled for a second: what came before can't be joined to what follows
        assertNull(estimator.feed(1f, startNs = 2_500_000_000L) { resting })
    }
}
