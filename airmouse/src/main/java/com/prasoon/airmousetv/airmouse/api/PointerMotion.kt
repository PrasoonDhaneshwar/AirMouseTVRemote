package com.prasoon.airmousetv.airmouse.api

import kotlin.math.sqrt

/** A direction or a turning speed in the phone's own axes: x to its right edge, y to its top edge, z out of the screen. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    infix fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z

    infix fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    val length get() = sqrt(this dot this)
}

/** How fast the phone is turning, in rad/s: [yaw] to the left, [pitch] upwards. */
data class PointingRates(val yaw: Float, val pitch: Float)

/**
 * Below this length the phone's top edge points almost straight up or down, and "sideways" can no
 * longer be worked out from it. About 11 degrees off vertical.
 */
private const val MIN_SIDEWAYS_LENGTH = 0.2f

/**
 * Which way is up, seen from the phone, given its orientation as the unit quaternion of a rotation
 * vector sensor ([x], [y], [z], [w]). This is the bottom row of the rotation matrix that turns
 * phone axes into world axes, whose Z axis points at the sky.
 */
fun upInDeviceFrame(x: Float, y: Float, z: Float, w: Float) = Vec3(
    2f * (x * z - w * y),
    2f * (y * z + w * x),
    1f - 2f * (x * x + y * y)
)

/**
 * Splits the gyroscope's turning speed [angular] into the two turns that move a pointer: yaw around
 * the vertical, and pitch around the horizontal line across the direction the phone points in.
 *
 * Measuring both against [up] rather than against the phone's own axes is what lets the phone be
 * held at any roll: a sideways sweep is a turn around the vertical however the hand is twisted, so
 * it moves the pointer sideways and nowhere else. Twisting the phone along its length is neither
 * turn and moves nothing.
 *
 * Without [up] (a phone with no rotation sensor), or with the phone pointing straight up or down,
 * the phone's own axes are used, which is right for a phone held flat.
 */
fun pointingRates(angular: Vec3, up: Vec3?): PointingRates {
    val sideways = up?.let { Vec3(0f, 1f, 0f) cross it }
    val sidewaysLength = sideways?.length ?: 0f
    if (up == null || sideways == null || sidewaysLength < MIN_SIDEWAYS_LENGTH) {
        return PointingRates(yaw = angular.z, pitch = angular.x)
    }
    return PointingRates(yaw = angular dot up, pitch = (angular dot sideways) / sidewaysLength)
}

/**
 * How much a movement at [speed] is scaled: [slowGain] at or below [slowSpeed], [fastGain] at or
 * above [fastSpeed], and a straight line in between. Slow movements are scaled down so a small
 * target can be hit, fast ones up so the far side of the screen is one flick away.
 */
fun acceleratedGain(speed: Float, slowSpeed: Float, fastSpeed: Float, slowGain: Float, fastGain: Float): Float {
    val t = ((speed - slowSpeed) / (fastSpeed - slowSpeed)).coerceIn(0f, 1f)
    return slowGain + t * (fastGain - slowGain)
}
