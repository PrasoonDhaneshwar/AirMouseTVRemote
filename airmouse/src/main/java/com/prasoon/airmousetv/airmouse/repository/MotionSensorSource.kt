package com.prasoon.airmousetv.airmouse.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.prasoon.airmousetv.airmouse.api.MotionSample
import com.prasoon.airmousetv.airmouse.api.Vec3
import com.prasoon.airmousetv.airmouse.api.upInDeviceFrame
import kotlin.math.sqrt

/**
 * How often the gyroscope is asked to report, in microseconds: 100 times a second, the rate of a
 * Bluetooth mouse. The default "game" rate is 50, which shows as a pointer moving in steps.
 */
private const val SAMPLING_PERIOD_US = 10_000

/**
 * Reads the phone's motion for the air mouse: the gyroscope for how fast it turns, and the game
 * rotation vector for which way is up. The game rotation vector leaves the compass out, so the
 * pointer doesn't jump near speakers or metal.
 *
 * Sensors run only between [start] and [stop], since a gyroscope kept running drains the battery.
 */
class MotionSensorSource(
    context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotation = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    /** False on phones without a gyroscope, where the air mouse can only be moved by sliding the thumb. */
    val hasGyroscope: Boolean get() = gyroscope != null

    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    /** Starts delivering readings to [onSample], on a thread of its own. Does nothing without a gyroscope or if already started. */
    @Synchronized
    fun start(onSample: (MotionSample) -> Unit) {
        val manager = sensorManager ?: return
        val gyro = gyroscope ?: return
        if (listener != null) return

        val newListener = object : SensorEventListener {
            /** Which way is up, from the newest rotation reading; null until the first one arrives. */
            private var up: Vec3? = null

            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        // Some phones leave out the fourth value; it follows from the quaternion having length 1
                        val w = if (v.size >= 4) v[3] else sqrt((1f - v[0] * v[0] - v[1] * v[1] - v[2] * v[2]).coerceAtLeast(0f))
                        up = upInDeviceFrame(v[0], v[1], v[2], w)
                    }
                    Sensor.TYPE_GYROSCOPE -> onSample(MotionSample(event.timestamp, Vec3(v[0], v[1], v[2]), up))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        // Off the main thread: 100 readings a second there would compete with drawing the pad
        val newThread = HandlerThread("AirMouseSensors").apply { start() }
        val handler = Handler(newThread.looper)
        manager.registerListener(newListener, gyro, SAMPLING_PERIOD_US, handler)
        rotation?.let { manager.registerListener(newListener, it, SAMPLING_PERIOD_US, handler) }
        listener = newListener
        thread = newThread
    }

    @Synchronized
    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        thread?.quitSafely()
        thread = null
    }
}
