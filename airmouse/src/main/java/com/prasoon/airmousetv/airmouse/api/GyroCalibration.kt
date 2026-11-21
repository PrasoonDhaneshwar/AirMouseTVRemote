package com.prasoon.airmousetv.airmouse.api

import kotlin.math.abs

/** How long the phone has to lie still for a calibration, in seconds. Long enough to average the sensor's noise away. */
const val CALIBRATION_SECONDS = 3

/**
 * A reading further than this from the first one, in rad/s on any axis, means the phone was moved
 * during calibration. A resting gyroscope's noise is a tenth of this; a hand is ten times more.
 */
internal const val CALIBRATION_STILL_RAD_S = 0.03f

/** Fewer readings than this isn't a calibration: the sensor stopped, or the time was cut short. */
internal const val CALIBRATION_MIN_SAMPLES = 100

/** How long the phone has to lie still, in nanoseconds, before [RestBiasEstimator] trusts what it read. Shorter than a manual calibration, since nobody is waiting. */
internal const val AUTO_CALIBRATION_NS = 2_000_000_000L

/**
 * Every reading of a resting phone stays this close to the first, in rad/s on any axis. Tighter than
 * [CALIBRATION_STILL_RAD_S]: nobody is asked to hold still, so what looks like rest must not be a
 * hand, whose tremor alone is several times more.
 */
internal const val AUTO_CALIBRATION_STILL_RAD_S = 0.01f

/** A drift larger than this, about 3 degrees a second, is a slow turn of the phone and not a sensor's drift. */
internal const val AUTO_CALIBRATION_MAX_BIAS_RAD_S = 0.05f

/** A new drift that differs from the one in use by less than this, in rad/s, isn't worth switching to. */
internal const val AUTO_CALIBRATION_MIN_CHANGE_RAD_S = 0.002f

/**
 * Notices the phone lying still, as when it is put down between uses, and reads the gyroscope's
 * resting drift off it: a calibration nobody has to ask for. Feed it every raw reading (the drift not
 * yet taken off) with [add]; it hands back the drift once per rest, after [AUTO_CALIBRATION_NS].
 */
class RestBiasEstimator {
    private var first: Vec3? = null
    private var startNs = 0L
    private var lastNs = 0L
    private var sumX = 0.0
    private var sumY = 0.0
    private var sumZ = 0.0
    private var count = 0
    private var reported = false

    /** The drift, once the phone has lain still long enough, and only then; null for every other reading. */
    @Synchronized
    fun add(sample: MotionSample): Vec3? {
        val angular = sample.angular
        val reference = first
        val gap = (sample.timestampNs - lastNs) / 1e9f
        lastNs = sample.timestampNs
        if (reference == null || gap > MAX_SAMPLE_GAP_S || moved(angular, reference)) {
            begin(sample)
            return null
        }
        sumX += angular.x
        sumY += angular.y
        sumZ += angular.z
        count++
        if (reported || sample.timestampNs - startNs < AUTO_CALIBRATION_NS || count < CALIBRATION_MIN_SAMPLES) return null
        reported = true
        val bias = Vec3((sumX / count).toFloat(), (sumY / count).toFloat(), (sumZ / count).toFloat())
        return bias.takeIf {
            abs(it.x) <= AUTO_CALIBRATION_MAX_BIAS_RAD_S && abs(it.y) <= AUTO_CALIBRATION_MAX_BIAS_RAD_S &&
                abs(it.z) <= AUTO_CALIBRATION_MAX_BIAS_RAD_S
        }
    }

    /** Forgets the rest in progress, as when the sensors stop. */
    @Synchronized
    fun reset() {
        first = null
        reported = false
    }

    private fun begin(sample: MotionSample) {
        first = sample.angular
        startNs = sample.timestampNs
        sumX = sample.angular.x.toDouble()
        sumY = sample.angular.y.toDouble()
        sumZ = sample.angular.z.toDouble()
        count = 1
        reported = false
    }

    private fun moved(angular: Vec3, reference: Vec3) =
        abs(angular.x - reference.x) > AUTO_CALIBRATION_STILL_RAD_S ||
            abs(angular.y - reference.y) > AUTO_CALIBRATION_STILL_RAD_S ||
            abs(angular.z - reference.z) > AUTO_CALIBRATION_STILL_RAD_S
}

/**
 * Measures the turning speed a gyroscope reports while the phone lies still. A perfect sensor would
 * say zero; a real one says a small constant, and that constant, integrated all day, is a pointer
 * that drifts by itself. Subtracting it from every reading stops the drift.
 *
 * Feed it every reading for [CALIBRATION_SECONDS] with [add], then take the [result].
 */
class GyroCalibration {
    private var first: Vec3? = null
    private var sumX = 0.0
    private var sumY = 0.0
    private var sumZ = 0.0
    private var count = 0
    private var moved = false

    @Synchronized
    fun add(angular: Vec3) {
        val reference = first ?: angular.also { first = it }
        if (abs(angular.x - reference.x) > CALIBRATION_STILL_RAD_S ||
            abs(angular.y - reference.y) > CALIBRATION_STILL_RAD_S ||
            abs(angular.z - reference.z) > CALIBRATION_STILL_RAD_S
        ) {
            moved = true
        }
        sumX += angular.x
        sumY += angular.y
        sumZ += angular.z
        count++
    }

    /**
     * The sensor's resting drift, or null if the readings can't be trusted: the phone was moved, or
     * too few arrived. A calibration taken from a moving phone would add drift instead of removing it.
     */
    @Synchronized
    fun result(): Vec3? {
        if (moved || count < CALIBRATION_MIN_SAMPLES) return null
        return Vec3((sumX / count).toFloat(), (sumY / count).toFloat(), (sumZ / count).toFloat())
    }
}
