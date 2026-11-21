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
 * Mouse counts per radian turned, before acceleration. The TV adds acceleration of its own: a pixel
 * per count for slow movement and 1.5 to 1.75 at the speed of a sweep (measured on a Hisense Android
 * TV 9), so a turn of about 35 degrees crosses a 1920-pixel
 * screen: a comfortable wrist movement.
 */
internal const val GYRO_COUNTS_PER_RADIAN = 2100f

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

/**
 * A phone turning slower than this, about a third of a degree a second, is lying on something. A
 * hand can't hold a phone that still: its tremor alone is several times more.
 */
internal const val AT_REST_RAD_S = 0.006f

/** How long the phone has to lie that still before pointing pauses. Long enough that a steady aim isn't taken for a table. */
internal const val REST_BEFORE_PAUSE_NS = 1_500_000_000L

/** A paused phone turning faster than this, about 15 degrees a second, has been picked up. A bump to the table stays below it. */
internal const val PICK_UP_RAD_S = 0.25f

/**
 * How long after being picked up the phone is still ignored. Picking a phone up swings it through
 * half a turn, which would throw the pointer into a corner before the user has even aimed.
 */
internal const val SETTLE_AFTER_PICK_UP_NS = 400_000_000L

/**
 * How far back a press undoes the pointer's movement. A finger landing on the screen, or pushing a
 * volume button, jolts the phone during the last tenth of a second before the press registers, and
 * moves the pointer off what the user was aiming at.
 */
internal const val PRESS_REWIND_NS = 100_000_000L

/** Cutoff of the smoothing at rest, in Hz, at the two ends of [PointerSettings.smoothing]. Hand tremor sits around 8 to 12 Hz. */
private const val QUICKEST_CUTOFF_HZ = 4f
private const val STEADIEST_CUTOFF_HZ = 0.8f

/** How fast the smoothing gives way as the turn speeds up, in Hz per rad/s²: a flick starts at 20 rad/s² or more and must not lag. */
private const val SMOOTHING_BETA = 0.6f

/**
 * Turns what the hand does into pointer movement.
 *
 * With [airPointing] on, the pointer follows the phone: no finger has to be on the screen. Gyroscope
 * readings have the sensor's resting drift ([gyroBias]) taken off, are corrected for how the phone
 * is rolled, smoothed, cut off below [DEAD_ZONE_RAD_S], and scaled by a gain that grows with the
 * turning speed. They are added up and handed out at most every [MIN_SEND_INTERVAL_NS].
 *
 * Three things stop the pointer following:
 * - [freeze], while a finger is held on the pad, so the phone can be brought back to a comfortable
 *   position without the pointer coming along, the way a mouse is lifted off the desk.
 * - Putting the phone down. A phone that lies still for [REST_BEFORE_PAUSE_NS] pauses, and wakes
 *   only when it is clearly picked up, and then a moment later, once the swing of picking it up is
 *   over.
 * - Turning [airPointing] off, which makes the pad a mouse pad: [onSlide] moves the pointer and the
 *   gyroscope is ignored.
 *
 * A press (a tap on the pad, a volume button) jolts the phone just before it registers, so
 * [rewind] hands back the movement of the last [PRESS_REWIND_NS] for the caller to undo before it
 * clicks.
 */
class PointerPipeline(settings: PointerSettings = PointerSettings()) {

    var settings: PointerSettings = settings
        @Synchronized set(value) {
            field = value
            yawFilter = newFilter()
            pitchFilter = newFilter()
        }

    /** True while moving the phone moves the pointer; false while the pad is used as a mouse pad. */
    var airPointing: Boolean = true
        @Synchronized set(value) {
            field = value
            forgetMotion()
        }

    /** The turning speed the gyroscope reports while the phone lies still, taken off every reading. */
    @Volatile var gyroBias: Vec3 = Vec3(0f, 0f, 0f)

    private var yawFilter = newFilter()
    private var pitchFilter = newFilter()

    private var frozen = false
    private var lastSampleNs: Long? = null
    private var lastSendNs: Long? = null
    private var pendingX = 0f
    private var pendingY = 0f

    /** When the phone was last seen turning faster than [AT_REST_RAD_S]; null until the first reading. */
    private var lastStirNs: Long? = null
    private var paused = false
    /** While waking from a pause: when the swing of picking the phone up counts as over. */
    private var settledAtNs: Long? = null

    /** Movements handed out in the last [PRESS_REWIND_NS], oldest first, for [rewind]. */
    private val recent = ArrayDeque<Triple<Long, Float, Float>>()

    private fun newFilter(): OneEuroFilter {
        val cutoff = QUICKEST_CUTOFF_HZ + settings.smoothing.coerceIn(0f, 1f) * (STEADIEST_CUTOFF_HZ - QUICKEST_CUTOFF_HZ)
        return OneEuroFilter(cutoff, SMOOTHING_BETA)
    }

    /** Stops the pointer following the phone until [unfreeze]. */
    @Synchronized
    fun freeze() {
        frozen = true
        forgetMotion()
    }

    /** Lets the pointer follow the phone again, from where the phone points now. */
    @Synchronized
    fun unfreeze() {
        frozen = false
        forgetMotion()
    }

    /** Starts afresh, as when the pad comes on screen: not frozen, not paused, nothing carried over. */
    @Synchronized
    fun reset() {
        frozen = false
        paused = false
        settledAtNs = null
        lastStirNs = null
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

    /**
     * The movement that undoes what the pointer did in the last [PRESS_REWIND_NS], or null if it
     * didn't move. Movement from before that is forgotten too, so a second press undoes nothing twice.
     */
    @Synchronized
    fun rewind(): PointerMove? {
        val latest = lastSampleNs ?: recent.lastOrNull()?.first
        val undo = recent.filter { latest == null || latest - it.first <= PRESS_REWIND_NS }
        recent.clear()
        pendingX = 0f
        pendingY = 0f
        if (undo.isEmpty()) return null
        return PointerMove(-undo.sumOf { it.second.toDouble() }.toFloat(), -undo.sumOf { it.third.toDouble() }.toFloat())
    }

    /** Takes a gyroscope reading. Returns the movement to send now, or null while there is none. */
    @Synchronized
    fun onMotion(sample: MotionSample): PointerMove? {
        if (!airPointing) return null
        val angular = Vec3(sample.angular.x - gyroBias.x, sample.angular.y - gyroBias.y, sample.angular.z - gyroBias.z)
        if (isPutDown(sample.timestampNs, angular.length) || frozen) return null

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

        val raw = pointingRates(angular, sample.up)
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
        return handOut(sample.timestampNs, pendingX, pendingY).also {
            pendingX = 0f
            pendingY = 0f
        }
    }

    /**
     * Tracks whether the phone has been put down, from how fast it is turning at [nowNs]. Returns
     * true while pointing is paused for that, including the moment after it is picked up again.
     */
    private fun isPutDown(nowNs: Long, speed: Float): Boolean {
        if (paused) {
            val settledAt = settledAtNs
            when {
                settledAt == null && speed >= PICK_UP_RAD_S -> settledAtNs = nowNs + SETTLE_AFTER_PICK_UP_NS
                settledAt != null && nowNs >= settledAt -> {
                    paused = false
                    settledAtNs = null
                    lastStirNs = nowNs
                    forgetMotion()
                }
            }
            return paused
        }
        val lastStir = lastStirNs
        if (lastStir == null || speed >= AT_REST_RAD_S) {
            lastStirNs = nowNs
        } else if (nowNs - lastStir >= REST_BEFORE_PAUSE_NS) {
            paused = true
            forgetMotion()
        }
        return paused
    }

    /**
     * The finger slid by [dxDp], [dyDp] over [dtSeconds] on the pad used as a mouse pad. Returns the
     * movement to send, or null while air pointing is on, where a finger on the pad freezes the
     * pointer instead.
     */
    @Synchronized
    fun onSlide(dxDp: Float, dyDp: Float, dtSeconds: Float): PointerMove? {
        if (airPointing) return null
        // Without a usable time the speed is unknown, and the careful end of the curve is the safe guess
        val speed = if (dtSeconds > 0f) hypot(dxDp, dyDp) / dtSeconds else 0f
        val countsPerDp = settings.sensitivity *
            acceleratedGain(speed, SLIDE_SLOW_DP_S, SLIDE_FAST_DP_S, SLIDE_SLOW_COUNTS_PER_DP, SLIDE_FAST_COUNTS_PER_DP)
        return PointerMove(dxDp * countsPerDp, dyDp * countsPerDp)
    }

    private fun handOut(nowNs: Long, dx: Float, dy: Float): PointerMove {
        recent.addLast(Triple(nowNs, dx, dy))
        while (recent.isNotEmpty() && nowNs - recent.first().first > PRESS_REWIND_NS) recent.removeFirst()
        return PointerMove(dx, dy)
    }
}
