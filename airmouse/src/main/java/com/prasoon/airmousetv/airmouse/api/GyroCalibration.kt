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
