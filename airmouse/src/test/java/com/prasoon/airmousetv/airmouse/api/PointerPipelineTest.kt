package com.prasoon.airmousetv.airmouse.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PointerPipelineTest {

    /** 100 gyroscope readings a second. */
    private val stepNs = 10_000_000L
    private val flat = Vec3(0f, 0f, 1f)

    /** A hand: feeds readings to [pipeline] on a steady clock and adds up where the pointer went. */
    private class Hand(val pipeline: PointerPipeline, private val stepNs: Long) {
        var nowNs = 1_000_000_000L
        var x = 0f
        var y = 0f
        var moves = 0

        /** Turns the phone at [angular] rad/s for [samples] readings. */
        fun turn(angular: Vec3, samples: Int, up: Vec3? = Vec3(0f, 0f, 1f)) {
            repeat(samples) {
                nowNs += stepNs
                pipeline.onMotion(MotionSample(nowNs, angular, up))?.let { move ->
                    x += move.dx
                    y += move.dy
                    moves++
                }
            }
        }
    }

    private fun hand(settings: PointerSettings = PointerSettings()) = Hand(PointerPipeline(settings), stepNs)

    // ----- The clutch -----

    @Test
    fun `the pointer doesn't follow the phone until the thumb is down`() {
        val hand = hand()
        hand.turn(Vec3(0f, 0f, 1f), samples = 50)
        assertEquals(0, hand.moves)
    }

    @Test
    fun `the pointer follows the phone while the thumb is down`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(0f, 0f, 1f), samples = 50)
        assertTrue(hand.moves > 0)
    }

    @Test
    fun `lifting the thumb lets go of the pointer`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(0f, 0f, 1f), samples = 50)
        val before = hand.x
        hand.pipeline.thumbUp()
        hand.turn(Vec3(0f, 0f, 1f), samples = 50)
        assertEquals(before, hand.x, 0f)
    }

    @Test
    fun `re-aiming with the thumb up is not made up for when it comes back down`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(0f, 0f, 0.5f), samples = 20)
        hand.pipeline.thumbUp()
        // A long swing back while the pointer is let go
        hand.nowNs += 2_000_000_000L
        hand.pipeline.thumbDown()
        val before = hand.x
        hand.turn(Vec3(0f, 0f, 0.5f), samples = 2)
        // One sample's worth at most, not two seconds' worth
        assertTrue(abs(hand.x - before) < 30f)
    }

    // ----- Direction -----

    @Test
    fun `turning left moves the pointer left`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(0f, 0f, 1f), samples = 50)
        assertTrue(hand.x < 0f)
        assertEquals(0f, hand.y, 0f)
    }

    @Test
    fun `tilting up moves the pointer up`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(1f, 0f, 0f), samples = 50)
        assertTrue(hand.y < 0f)
        assertEquals(0f, hand.x, 0f)
    }

    @Test
    fun `a sideways sweep moves the pointer sideways with the phone rolled onto its edge`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        // On its right edge, up is the phone's -x, and a turn to the right is around +x
        hand.turn(Vec3(1f, 0f, 0f), samples = 50, up = Vec3(-1f, 0f, 0f))
        assertTrue(hand.x > 0f)
        assertEquals(0f, hand.y, 1e-3f)
    }

    // ----- Feel -----

    @Test
    fun `a still hand doesn't move the pointer`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(DEAD_ZONE_RAD_S * 0.5f, 0f, DEAD_ZONE_RAD_S * 0.5f), samples = 200)
        assertEquals(0, hand.moves)
    }

    @Test
    fun `a steady turn covers the angle turned times the gain for that speed`() {
        val hand = hand(PointerSettings(smoothing = 0f))
        hand.pipeline.thumbDown()
        // A full second at a speed between the slow and fast ends
        val speed = 1f
        hand.turn(Vec3(0f, 0f, -speed), samples = 101)
        val gain = acceleratedGain(speed, GYRO_SLOW_RAD_S, GYRO_FAST_RAD_S, GYRO_SLOW_GAIN, GYRO_FAST_GAIN)
        assertEquals(GYRO_COUNTS_PER_RADIAN * gain * speed, hand.x, 5f)
    }

    @Test
    fun `turning twice as fast covers more than twice the distance`() {
        val slow = hand().apply { pipeline.thumbDown(); turn(Vec3(0f, 0f, -0.5f), samples = 100) }
        val fast = hand().apply { pipeline.thumbDown(); turn(Vec3(0f, 0f, -1f), samples = 100) }
        assertTrue(fast.x > slow.x * 2.2f)
    }

    @Test
    fun `sensitivity scales the movement`() {
        val normal = hand().apply { pipeline.thumbDown(); turn(Vec3(0f, 0f, -1f), samples = 100) }
        val doubled = hand(PointerSettings(sensitivity = 2f)).apply { pipeline.thumbDown(); turn(Vec3(0f, 0f, -1f), samples = 100) }
        assertEquals(normal.x * 2f, doubled.x, 1f)
    }

    @Test
    fun `more smoothing lets less of a shaking hand through`() {
        /** How far the pointer travels, back and forth, for a hand shaking at 10 Hz well above the dead zone. */
        fun shake(smoothing: Float): Float {
            val hand = hand(PointerSettings(smoothing = smoothing))
            hand.pipeline.thumbDown()
            var travelled = 0f
            repeat(300) { i ->
                val before = hand.x
                hand.turn(Vec3(0f, 0f, 0.3f * sin(2 * PI * 10 * i * 0.01).toFloat()), samples = 1)
                travelled += abs(hand.x - before)
            }
            return travelled
        }
        val quickest = shake(smoothing = 0f)
        val steadiest = shake(smoothing = 1f)
        assertTrue("steadiest $steadiest should be well under quickest $quickest", steadiest < quickest * 0.7f)
    }

    // ----- Timing -----

    @Test
    fun `the first reading after the thumb comes down only starts the clock`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(0f, 0f, 3f), samples = 1)
        assertEquals(0, hand.moves)
    }

    @Test
    fun `a gap in the readings is not treated as one long turn`() {
        val hand = hand()
        hand.pipeline.thumbDown()
        hand.turn(Vec3(0f, 0f, 1f), samples = 10)
        val before = hand.x
        hand.nowNs += 500_000_000L
        hand.turn(Vec3(0f, 0f, 1f), samples = 1)
        assertEquals(before, hand.x, 0f)
    }

    @Test
    fun `readings that arrive faster than the TV needs are added up`() {
        // A gyroscope running at 400 Hz
        val fast = Hand(PointerPipeline(PointerSettings(smoothing = 0f)), stepNs = 2_500_000L)
        fast.pipeline.thumbDown()
        fast.turn(Vec3(0f, 0f, -1f), samples = 401)
        // No more movements than one per send interval, and none of the distance lost
        assertTrue(fast.moves <= 1_000_000_000L / MIN_SEND_INTERVAL_NS + 1)
        val normal = hand(PointerSettings(smoothing = 0f)).apply { pipeline.thumbDown(); turn(Vec3(0f, 0f, -1f), samples = 101) }
        assertEquals(normal.x, fast.x, normal.x * 0.02f)
    }

    // ----- Sliding -----

    @Test
    fun `sliding the thumb moves the pointer the same way`() {
        val pipeline = PointerPipeline()
        pipeline.thumbDown()
        val move = pipeline.onSlide(10f, -4f, 0.016f)
        assertNotNull(move)
        assertTrue(move!!.dx > 0f && move.dy < 0f)
        // The direction is kept: both axes get the same gain
        assertEquals(10f / -4f, move.dx / move.dy, 1e-4f)
    }

    @Test
    fun `a slide with the thumb up moves nothing`() {
        assertNull(PointerPipeline().onSlide(10f, 0f, 0.016f))
    }

    @Test
    fun `a slow slide is finer than a fast one`() {
        val pipeline = PointerPipeline()
        pipeline.thumbDown()
        // The same 10 dp, crossed in 500 ms and in 5 ms
        val slow = pipeline.onSlide(10f, 0f, 0.5f)!!
        val fast = pipeline.onSlide(10f, 0f, 0.005f)!!
        assertEquals(10f * SLIDE_SLOW_COUNTS_PER_DP, slow.dx, 1e-3f)
        assertEquals(10f * SLIDE_FAST_COUNTS_PER_DP, fast.dx, 1e-3f)
    }

    @Test
    fun `a slide with no time attached gets the careful gain`() {
        val pipeline = PointerPipeline()
        pipeline.thumbDown()
        assertEquals(10f * SLIDE_SLOW_COUNTS_PER_DP, pipeline.onSlide(10f, 0f, 0f)!!.dx, 1e-3f)
    }

    @Test
    fun `sliding while pointing adds to the pointing and leaves it as it was`() {
        val turn = Vec3(0f, 0f, -1f)
        val pointingOnly = hand().apply { pipeline.thumbDown(); turn(turn, samples = 100) }
        val both = hand().apply {
            pipeline.thumbDown()
            turn(turn, samples = 50)
            assertNotNull(pipeline.onSlide(20f, 0f, 0.1f))
            turn(turn, samples = 50)
        }
        // The slide was handed out by itself; what the turn produced is unchanged by it
        assertEquals(pointingOnly.x, both.x, 1e-3f)
    }
}
