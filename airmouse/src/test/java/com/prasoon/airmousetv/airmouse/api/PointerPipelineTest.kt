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

        /** Leaves the phone lying on a table for [seconds]. */
        fun rest(seconds: Float) = turn(Vec3(0f, 0f, 0f), (seconds * 1e9 / stepNs).toInt())

        /** Applies the movement a press undoes, as the session would send it. */
        fun rewind() {
            pipeline.rewind()?.let { move ->
                x += move.dx
                y += move.dy
            }
        }
    }

    private fun hand(settings: PointerSettings = PointerSettings()) = Hand(PointerPipeline(settings), stepNs)

    private val left = Vec3(0f, 0f, 1f)
    private val right = Vec3(0f, 0f, -1f)

    // ----- Air pointing -----

    @Test
    fun `the pointer follows the phone with no finger on the pad`() {
        val hand = hand()
        hand.turn(left, samples = 50)
        assertTrue(hand.moves > 0)
    }

    @Test
    fun `turning left moves the pointer left`() {
        val hand = hand()
        hand.turn(left, samples = 50)
        assertTrue(hand.x < 0f)
        assertEquals(0f, hand.y, 0f)
    }

    @Test
    fun `tilting up moves the pointer up`() {
        val hand = hand()
        hand.turn(Vec3(1f, 0f, 0f), samples = 50)
        assertTrue(hand.y < 0f)
        assertEquals(0f, hand.x, 0f)
    }

    @Test
    fun `a sideways sweep moves the pointer sideways with the phone rolled onto its edge`() {
        val hand = hand()
        // On its right edge, up is the phone's -x, and a turn to the right is around +x
        hand.turn(Vec3(1f, 0f, 0f), samples = 50, up = Vec3(-1f, 0f, 0f))
        assertTrue(hand.x > 0f)
        assertEquals(0f, hand.y, 1e-3f)
    }

    @Test
    fun `with air pointing off the phone can be moved without the pointer following`() {
        val hand = hand()
        hand.pipeline.airPointing = false
        hand.turn(left, samples = 100)
        assertEquals(0, hand.moves)
    }

    // ----- Holding the pointer still -----

    @Test
    fun `a frozen pointer doesn't follow the phone`() {
        val hand = hand()
        hand.turn(left, samples = 50)
        val before = hand.x
        hand.pipeline.freeze()
        hand.turn(left, samples = 50)
        assertEquals(before, hand.x, 0f)
    }

    @Test
    fun `re-aiming while frozen is not made up for afterwards`() {
        val hand = hand()
        hand.turn(Vec3(0f, 0f, 0.5f), samples = 20)
        hand.pipeline.freeze()
        // A long swing back while the pointer is held
        hand.turn(Vec3(0f, 0f, -3f), samples = 100)
        hand.pipeline.unfreeze()
        val before = hand.x
        hand.turn(Vec3(0f, 0f, 0.5f), samples = 2)
        // One sample's worth at most, not the swing
        assertTrue(abs(hand.x - before) < 30f)
    }

    @Test
    fun `the pointer follows again once unfrozen`() {
        val hand = hand()
        hand.pipeline.freeze()
        hand.pipeline.unfreeze()
        hand.turn(right, samples = 50)
        assertTrue(hand.x > 0f)
    }

    // ----- Undoing the jolt of a press -----

    @Test
    fun `a press undoes the movement of the last tenth of a second`() {
        val hand = hand(PointerSettings(smoothing = 0f))
        hand.turn(right, samples = 100)
        val aimedAt = hand.x
        // The finger lands: the phone jerks for 80 ms
        hand.turn(Vec3(2f, 0f, 2f), samples = 8)
        assertTrue(hand.x != aimedAt)
        hand.rewind()
        // Back to where it was aimed, less the last few samples of the sweep that fall in the same tenth of a second
        assertEquals(aimedAt, hand.x, 130f)
        assertEquals(0f, hand.y, 1e-2f)
    }

    @Test
    fun `movement from longer ago is left alone`() {
        val hand = hand(PointerSettings(smoothing = 0f))
        hand.turn(right, samples = 100)
        val travelled = hand.x
        hand.rewind()
        // A second's sweep is not undone by a press, only its last tenth
        assertTrue(hand.x > travelled * 0.8f)
    }

    @Test
    fun `a second press undoes nothing more`() {
        val hand = hand()
        hand.turn(right, samples = 50)
        hand.rewind()
        assertNull(hand.pipeline.rewind())
    }

    @Test
    fun `a press with the pointer at rest undoes nothing`() {
        assertNull(PointerPipeline().rewind())
    }

    // ----- Putting the phone down -----

    @Test
    fun `a phone lying still pauses, and a nudge doesn't wake it`() {
        val hand = hand()
        hand.turn(right, samples = 50)
        hand.rest(seconds = 2f)
        val before = hand.x
        // Someone bumps the table
        hand.turn(Vec3(0f, 0f, -PICK_UP_RAD_S * 0.5f), samples = 30)
        assertEquals(before, hand.x, 0f)
    }

    @Test
    fun `picking the phone up doesn't throw the pointer`() {
        val hand = hand()
        hand.rest(seconds = 2f)
        // The swing of picking it up: fast, and over within the settling time
        hand.turn(Vec3(3f, 0f, -3f), samples = 30)
        assertEquals(0, hand.moves)
    }

    @Test
    fun `pointing resumes a moment after the phone is picked up`() {
        val hand = hand()
        hand.rest(seconds = 2f)
        hand.turn(Vec3(3f, 0f, -3f), samples = 30)
        hand.turn(right, samples = 100)
        assertTrue(hand.x > 0f)
    }

    @Test
    fun `a hand holding a steady aim is not taken for a table`() {
        val hand = hand()
        // Three seconds of the tremor every hand has, well under the dead zone but above a table's stillness
        repeat(300) { i -> hand.turn(Vec3(0f, 0f, AT_REST_RAD_S * 2f * if (i % 2 == 0) 1f else -1f), samples = 1) }
        hand.turn(right, samples = 5)
        // Not paused: the turn moves the pointer at once, without having to count as a pick-up first
        assertTrue(hand.moves > 0)
    }

    // ----- Feel -----

    @Test
    fun `a slow drift of the hand doesn't move the pointer`() {
        val hand = hand()
        hand.turn(Vec3(DEAD_ZONE_RAD_S * 0.5f, 0f, DEAD_ZONE_RAD_S * 0.5f), samples = 200)
        assertEquals(0, hand.moves)
    }

    @Test
    fun `a steady turn covers the angle turned times the gain for that speed`() {
        val hand = hand(PointerSettings(smoothing = 0f))
        // A full second at a speed between the slow and fast ends
        val speed = 1f
        hand.turn(Vec3(0f, 0f, -speed), samples = 101)
        val gain = acceleratedGain(speed, GYRO_SLOW_RAD_S, GYRO_FAST_RAD_S, GYRO_SLOW_GAIN, GYRO_FAST_GAIN)
        assertEquals(GYRO_COUNTS_PER_RADIAN * gain * speed, hand.x, 5f)
    }

    @Test
    fun `turning twice as fast covers more than twice the distance`() {
        val slow = hand().apply { turn(Vec3(0f, 0f, -0.5f), samples = 100) }
        val fast = hand().apply { turn(Vec3(0f, 0f, -1f), samples = 100) }
        assertTrue(fast.x > slow.x * 2.2f)
    }

    @Test
    fun `sensitivity scales the movement`() {
        val normal = hand().apply { turn(right, samples = 100) }
        val doubled = hand(PointerSettings(sensitivity = 2f)).apply { turn(right, samples = 100) }
        assertEquals(normal.x * 2f, doubled.x, 1f)
    }

    @Test
    fun `more smoothing lets less of a shaking hand through`() {
        /** How far the pointer travels, back and forth, for a hand shaking at 10 Hz well above the dead zone. */
        fun shake(smoothing: Float): Float {
            val hand = hand(PointerSettings(smoothing = smoothing))
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

    @Test
    fun `the gyroscope's resting drift is taken off before anything else`() {
        val hand = hand()
        // A sensor that reports a slow turn while the phone is still, above the dead zone
        val drift = Vec3(0.05f, 0f, -0.05f)
        hand.pipeline.gyroBias = drift
        hand.turn(drift, samples = 100)
        assertEquals(0, hand.moves)
    }

    @Test
    fun `without the correction the same drift moves the pointer by itself`() {
        val hand = hand()
        hand.turn(Vec3(0.05f, 0f, -0.05f), samples = 100)
        assertTrue(hand.moves > 0)
    }

    // ----- Timing -----

    @Test
    fun `the first reading only starts the clock`() {
        val hand = hand()
        hand.turn(Vec3(0f, 0f, 3f), samples = 1)
        assertEquals(0, hand.moves)
    }

    @Test
    fun `a gap in the readings is not treated as one long turn`() {
        val hand = hand()
        hand.turn(left, samples = 10)
        val before = hand.x
        hand.nowNs += 500_000_000L
        hand.turn(left, samples = 1)
        assertEquals(before, hand.x, 0f)
    }

    @Test
    fun `readings that arrive faster than the TV needs are added up`() {
        // A gyroscope running at 400 Hz
        val fast = Hand(PointerPipeline(PointerSettings(smoothing = 0f)), stepNs = 2_500_000L)
        fast.turn(right, samples = 401)
        // No more movements than one per send interval, and none of the distance lost
        assertTrue(fast.moves <= 1_000_000_000L / MIN_SEND_INTERVAL_NS + 1)
        val normal = hand(PointerSettings(smoothing = 0f)).apply { turn(right, samples = 101) }
        assertEquals(normal.x, fast.x, normal.x * 0.02f)
    }

    // ----- The pad as a mouse pad -----

    private fun mousePad() = PointerPipeline().apply { airPointing = false }

    @Test
    fun `sliding a finger moves the pointer the same way`() {
        val move = mousePad().onSlide(10f, -4f, 0.016f)
        assertNotNull(move)
        assertTrue(move!!.dx > 0f && move.dy < 0f)
        // The direction is kept: both axes get the same gain
        assertEquals(10f / -4f, move.dx / move.dy, 1e-4f)
    }

    @Test
    fun `with air pointing on a finger on the pad doesn't slide the pointer`() {
        // It freezes the pointer instead, so the slide of a re-aiming thumb must not move it
        assertNull(PointerPipeline().onSlide(10f, 0f, 0.016f))
    }

    @Test
    fun `a slow slide is finer than a fast one`() {
        val pad = mousePad()
        // The same 10 dp, crossed in 500 ms and in 5 ms
        val slow = pad.onSlide(10f, 0f, 0.5f)!!
        val fast = pad.onSlide(10f, 0f, 0.005f)!!
        assertEquals(10f * SLIDE_SLOW_COUNTS_PER_DP, slow.dx, 1e-3f)
        assertEquals(10f * SLIDE_FAST_COUNTS_PER_DP, fast.dx, 1e-3f)
    }

    @Test
    fun `a slide with no time attached gets the careful gain`() {
        assertEquals(10f * SLIDE_SLOW_COUNTS_PER_DP, mousePad().onSlide(10f, 0f, 0f)!!.dx, 1e-3f)
    }

    @Test
    fun `sensitivity scales a slide too`() {
        val pad = PointerPipeline(PointerSettings(sensitivity = 2f)).apply { airPointing = false }
        assertEquals(20f * SLIDE_SLOW_COUNTS_PER_DP, pad.onSlide(10f, 0f, 0.5f)!!.dx, 1e-3f)
    }
}
