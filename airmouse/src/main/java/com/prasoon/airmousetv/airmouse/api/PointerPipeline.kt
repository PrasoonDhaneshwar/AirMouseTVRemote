package com.prasoon.airmousetv.airmouse.api

import kotlin.math.hypot

/** One gyroscope reading: when it was taken, how fast the phone was turning, and which way was up (null if unknown). */
data class MotionSample(val timestampNs: Long, val angular: Vec3, val up: Vec3?)

/** A movement of the pointer in mouse counts, right and down being positive. */
data class PointerMove(val dx: Float, val dy: Float)

/**
 * How the pointer should feel. [sensitivity] scales every movement (1 is the default). [smoothing]
 * runs from 0, quickest, to 1, steadiest.
 */
data class PointerSettings(val sensitivity: Float = 1f, val smoothing: Float = 0.5f)

/**
 * Mouse counts per radian turned, before acceleration. The TV moves its pointer about a pixel per
 * count, so a turn of about 35 degrees crosses a 1920-pixel screen: a comfortable wrist movement.
 */
internal const val GYRO_COUNTS_PER_RADIAN = 3200f

/** Turning slower than this (about 9 degrees a second) is aiming at something: scaled down for precision. */
internal const val GYRO_SLOW_RAD_S = 0.15f
/** Turning faster than this (about 115 degrees a second) is a flick across the screen: scaled up. */
internal const val GYRO_FAST_RAD_S = 2f
internal const val GYRO_SLOW_GAIN = 0.6f
internal const val GYRO_FAST_GAIN = 1.8f

/**
 * Turning slower than this, about 1 degree a second, is ignored. A hand trying to hold still
 * wanders at around that speed, and without the cut the pointer creeps while the user reads.
 */
internal const val DEAD_ZONE_RAD_S = 0.02f

/** Mouse counts per dp slid, at the slow and fast ends. A slow slide is for the last few pixels, so it is finer than the gyroscope. */
internal const val SLIDE_SLOW_COUNTS_PER_DP = 1.2f
internal const val SLIDE_FAST_COUNTS_PER_DP = 4.5f
internal const val SLIDE_SLOW_DP_S = 60f
internal const val SLIDE_FAST_DP_S = 900f

/**
 * Readings further apart than this aren't joined up: the sensor stalled or was only just started,
 * and treating the gap as one long turn would throw the pointer.
 */
internal const val MAX_SAMPLE_GAP_S = 0.1f

/**
 * The shortest time between two movements sent. Bluetooth mice report about every 10 ms and the TV
 * draws at 60 Hz; a gyroscope that delivers faster has its readings added up into one movement.
 */
internal const val MIN_SEND_INTERVAL_NS = 8_000_000L

/** Cutoff of the smoothing at rest, in Hz, at the two ends of [PointerSettings.smoothing]. Hand tremor sits around 8 to 12 Hz. */
private const val QUICKEST_CUTOFF_HZ = 4f
private const val STEADIEST_CUTOFF_HZ = 0.8f

/** How fast the smoothing gives way as the turn speeds up, in Hz per rad/s²: a flick starts at 20 rad/s² or more and must not lag. */
private const val SMOOTHING_BETA = 0.6f

/**
 * Turns what the hand does into pointer movement: gyroscope readings while the thumb rests on the
 * pad, and the thumb's own sliding.
 *
 * The thumb is a clutch. The pointer follows the phone only while the thumb is down, so lifting it
 * lets the user bring the phone back to a comfortable position without the pointer following, the
 * way a mouse is lifted off the desk. Each time the thumb comes down the smoothing starts afresh,
 * so nothing from before the lift leaks into the new movement.
 *
 * Gyroscope readings are corrected for how the phone is rolled, smoothed, cut off below
 * [DEAD_ZONE_RAD_S], and scaled by a gain that grows with the turning speed. They are added up and
 * handed out at most every [MIN_SEND_INTERVAL_NS]. Slides are scaled the same way, by their own
 * speed, and handed out at once: touch events already arrive at the screen's pace.
 */
class PointerPipeline(settings: PointerSettings = PointerSettings()) {

    var settings: PointerSettings = settings
        @Synchronized set(value) {
            field = value
            yawFilter = newFilter()
            pitchFilter = newFilter()
        }

    private var yawFilter = newFilter()
    private var pitchFilter = newFilter()

    private var thumbDown = false
    private var lastSampleNs: Long? = null
    private var lastSendNs: Long? = null
    private var pendingX = 0f
    private var pendingY = 0f

    private fun newFilter(): OneEuroFilter {
        val cutoff = QUICKEST_CUTOFF_HZ + settings.smoothing.coerceIn(0f, 1f) * (STEADIEST_CUTOFF_HZ - QUICKEST_CUTOFF_HZ)
        return OneEuroFilter(cutoff, SMOOTHING_BETA)
    }

    /** The thumb touched the pad: the pointer follows the phone from here on. */
    @Synchronized
    fun thumbDown() {
        thumbDown = true
        forgetMotion()
    }

    /** The thumb left the pad: the phone can be moved freely. */
    @Synchronized
    fun thumbUp() {
        thumbDown = false
        forgetMotion()
    }

    private fun forgetMotion() {
        yawFilter.reset()
        pitchFilter.reset()
        lastSampleNs = null
        lastSendNs = null
        pendingX = 0f
        pendingY = 0f
    }

    /** Takes a gyroscope reading. Returns the movement to send now, or null while there is none or the thumb is up. */
    @Synchronized
    fun onMotion(sample: MotionSample): PointerMove? {
        if (!thumbDown) return null
        val previousNs = lastSampleNs
        lastSampleNs = sample.timestampNs
        // The first reading only says where time starts; a turn needs two
        if (previousNs == null) {
            lastSendNs = sample.timestampNs
            return null
        }
        val dt = (sample.timestampNs - previousNs) / 1e9f
        if (dt <= 0f || dt > MAX_SAMPLE_GAP_S) {
            yawFilter.reset()
            pitchFilter.reset()
            return null
        }

        val raw = pointingRates(sample.angular, sample.up)
        val yaw = yawFilter.filter(raw.yaw, dt)
        val pitch = pitchFilter.filter(raw.pitch, dt)
        val speed = hypot(yaw, pitch)
        if (speed >= DEAD_ZONE_RAD_S) {
            val counts = GYRO_COUNTS_PER_RADIAN * settings.sensitivity * dt *
                acceleratedGain(speed, GYRO_SLOW_RAD_S, GYRO_FAST_RAD_S, GYRO_SLOW_GAIN, GYRO_FAST_GAIN)
            // Turning left moves the pointer left and tilting up moves it up, and both are negative on a screen
            pendingX -= yaw * counts
            pendingY -= pitch * counts
        }

        val sentNs = lastSendNs ?: sample.timestampNs
        if (sample.timestampNs - sentNs < MIN_SEND_INTERVAL_NS || (pendingX == 0f && pendingY == 0f)) return null
        lastSendNs = sample.timestampNs
        return PointerMove(pendingX, pendingY).also {
            pendingX = 0f
            pendingY = 0f
        }
    }

    /**
     * The thumb slid by [dxDp], [dyDp] over [dtSeconds]. Returns the movement to send, or null if
     * the thumb isn't down.
     */
    @Synchronized
    fun onSlide(dxDp: Float, dyDp: Float, dtSeconds: Float): PointerMove? {
        if (!thumbDown) return null
        // Without a usable time the speed is unknown, and the careful end of the curve is the safe guess
        val speed = if (dtSeconds > 0f) hypot(dxDp, dyDp) / dtSeconds else 0f
        val countsPerDp = settings.sensitivity *
            acceleratedGain(speed, SLIDE_SLOW_DP_S, SLIDE_FAST_DP_S, SLIDE_SLOW_COUNTS_PER_DP, SLIDE_FAST_COUNTS_PER_DP)
        return PointerMove(dxDp * countsPerDp, dyDp * countsPerDp)
    }
}
