package com.prasoon.airmousetv.airmouse.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.prasoon.airmousetv.airmouse.api.AUTO_CALIBRATION_MIN_CHANGE_RAD_S
import com.prasoon.airmousetv.airmouse.api.CALIBRATION_SECONDS
import com.prasoon.airmousetv.airmouse.api.GyroCalibration
import com.prasoon.airmousetv.airmouse.api.RestBiasEstimator
import com.prasoon.airmousetv.airmouse.api.Vec3
import kotlin.math.abs
import com.prasoon.airmousetv.airmouse.api.PointerMove
import com.prasoon.airmousetv.airmouse.api.PointerPipeline
import com.prasoon.airmousetv.airmouse.api.PointerSink
import com.prasoon.airmousetv.airmouse.api.RECENTER_RETURN_STEP
import com.prasoon.airmousetv.airmouse.api.recenterMoves
import com.prasoon.airmousetv.airmouse.api.tvLinkKeys
import com.prasoon.airmousetv.airmouse.api.toPointerSettings
import com.prasoon.airmousetv.airmouse.api.WheelPacer
import com.prasoon.airmousetv.airmouse.api.wakeMoves
import com.prasoon.airmousetv.airmouse.model.AirMouseSettings
import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.airmouse.model.TvSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "AirMouseSession"

/**
 * How long the pointer stays still after a press ends. Letting go of the screen or a button jolts
 * the phone as much as pressing did, and a pointer that jumps as the click lands turns it into a drag.
 */
private const val STILL_AFTER_PRESS_MS = 100L

/**
 * The gap between the movements of a recenter: the pace of a Bluetooth mouse, about one report every
 * 10 ms. The walk back from the corner relies on it, since the TV speeds up a pointer that moves
 * faster than this (see [RECENTER_RETURN_STEP]).
 */
private const val PACED_MOVE_GAP_MS = 10L

/** Something to send to the TV's pointer, applied in order. */
private sealed interface PointerEvent {
    data class Move(val dx: Float, val dy: Float) : PointerEvent
    data class Button(val button: PointerButton, val down: Boolean) : PointerEvent
    data class Scroll(val dx: Float, val dy: Float) : PointerEvent

    /**
     * Movements sent one at a time with [PACED_MOVE_GAP_MS] between them. With [holdStill] the
     * pointer stopped following the phone for them, and follows again once the last has gone.
     */
    data class Paced(val moves: List<PointerMove>, val holdStill: Boolean = false) : PointerEvent
}

/**
 * The air mouse for one TV: owns the link that carries the pointer and feeds it from the phone's
 * motion and the pad's touches.
 *
 * It knows nothing of the Wi-Fi remote, and the remote nothing of it, so the keys keep working
 * whatever happens to the pointer. The app keeps one for its whole life, like the remote session,
 * so a pointer link survives a configuration change.
 *
 * Movement arrives from the sensor thread and touches from the main thread. Both are queued and
 * one worker sends them, so a button press can't overtake the movement that led up to it.
 */
class AirMouseSession private constructor(
    private val hidSink: BluetoothHidSink,
    private val sensors: MotionSensorSource,
    private val links: AirMouseLinkStore,
    private val store: AirMouseSettingsStore,
) {
    private val sink: PointerSink = hidSink
    private val pipeline = PointerPipeline()
    private val events = Channel<PointerEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The air mouse of this app, built on the application [context]. */
    constructor(context: Context) : this(
        BluetoothHidSink(context), MotionSensorSource(context), AirMouseLinkStore(context), AirMouseSettingsStore(context)
    )

    /** The names of the TV the session was last started for; a TV that pairs during setup is saved under them. */
    @Volatile private var tvKeys: List<String> = emptyList()

    /** Collects gyroscope readings while a calibration runs; null otherwise. */
    @Volatile private var calibration: GyroCalibration? = null

    /** Reads the gyroscope's drift off the phone whenever it is put down; see [RestBiasEstimator]. */
    private val restBias = RestBiasEstimator()

    /** True while the sensors run for an open pad; the pointer is shown to the TV only then. */
    @Volatile private var sensingNow = false

    private fun wake() {
        events.trySend(PointerEvent.Paced(wakeMoves()))
    }

    /** Ends the stillness after a press; replaced when another press starts before it has run. */
    private var unfreeze: Job? = null

    /** Space out the turns of the wheel and of the sideways wheel, each on its own clock; see [WheelPacer]. */
    private val wheel = WheelPacer()
    private val pan = WheelPacer()

    /** Send the turns [wheel] and [pan] held back; null when none are waiting. */
    private var wheelFlush: Job? = null
    private var panFlush: Job? = null

    val state: StateFlow<PointerLinkState> get() = sink.state

    /** False on phones that can never be a Bluetooth mouse, where the air mouse isn't offered at all. */
    val available: Boolean get() = hidSink.supported

    val hasGyroscope: Boolean get() = sensors.hasGyroscope

    /** The name the TV lists this phone under while pairing, or null if it can't be read. */
    val phoneName: String? get() = hidSink.phoneName

    /** During setup: what the phone's own search for the TV over Bluetooth has found. */
    val search: StateFlow<TvSearch> get() = hidSink.search

    /** How the user has set the air mouse up; changing it takes effect at once and is remembered. */
    var settings: AirMouseSettings = store.load()
        set(value) {
            field = value
            store.save(value)
            applySettings()
        }

    /** Whether this phone has the Bluetooth mouse role: null while that hasn't been found out yet. */
    val hasMouseRole: Boolean? get() = store.hasMouseRole

    /** True once the air mouse has been offered right after a TV was paired; that offer is made once. */
    var offeredAfterPairing: Boolean
        get() = store.offeredAfterPairing
        set(value) { store.offeredAfterPairing = value }

    /** Called when the TV's Bluetooth pairing turns out to be gone and the air mouse needs setting up again. */
    var onUnlinked: () -> Unit = {}

    /** Receives events worth keeping for a bug report. */
    var onNote: (String) -> Unit
        get() = hidSink.onNote
        set(value) { hidSink.onNote = value }

    init {
        hidSink.onLinked = { address -> if (tvKeys.isNotEmpty()) links.save(tvKeys, address) }
        hidSink.onUnlinked = {
            if (tvKeys.isNotEmpty()) links.clear(tvKeys)
            onUnlinked()
        }
        pipeline.gyroBias = store.gyroBias
        applySettings()
        scope.launch { work() }
        scope.launch {
            sink.state.collect { link ->
                when (link) {
                    // What a real attempt to take the role found out is worth more than any probe
                    PointerLinkState.UNSUPPORTED -> if (hidSink.supported) store.hasMouseRole = false
                    PointerLinkState.NEEDS_PAIRING -> store.hasMouseRole = true
                    PointerLinkState.READY -> {
                        store.hasMouseRole = true
                        pipeline.reset()
                        // Only for a pad that is open: on the D-pad or touchpad a pointer would just be in the way
                        if (sensingNow) wake()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun applySettings() {
        pipeline.settings = settings.toPointerSettings()
        // Without a gyroscope there is nothing to point with, whatever was chosen on another phone's backup
        pipeline.airPointing = settings.airPointing && sensors.hasGyroscope
    }

    /**
     * Finds out whether this phone has the Bluetooth mouse role, if that isn't known yet and
     * Bluetooth is on, and calls [onKnown] once it is. Asks for nothing and shows nothing.
     */
    fun findOutMouseRole(onKnown: (Boolean) -> Unit) {
        store.hasMouseRole?.let { return onKnown(it) }
        hidSink.probeRole { has ->
            store.hasMouseRole = has
            onKnown(has)
        }
    }

    /** True once the TV known by [tvKeys] (see [tvLinkKeys]) has been paired for the pointer, under any of them. */
    fun isLinked(tvKeys: List<String>): Boolean = links.addressFor(tvKeys) != null

    /**
     * Brings the pointer up for the TV known by [tvKeys] (see [tvLinkKeys]): connects to it if it has
     * been set up, or looks for it over Bluetooth under the names people see, [tvNames], and waits for
     * it to pair if not. Returns false, having done nothing, if the Bluetooth permission is missing.
     * The link stays up until [stop]; the sensors run only while [sensing], since the gyroscope is what
     * costs battery and nothing is pointed while another mode is showing. Calling this again just
     * changes [sensing].
     * Call from the main thread.
     */
    fun start(tvKeys: List<String>, tvNames: List<String>, sensing: Boolean = true): Boolean {
        this.tvKeys = tvKeys
        val address = links.addressFor(tvKeys)
        // Found under one of its names: saved under the rest too, so it is found by the name it goes by next time
        if (address != null) links.save(tvKeys, address)
        if (!hidSink.start(address, tvNames)) return false
        val padOpened = sensing && !sensingNow
        sensingNow = sensing
        // The TV hides a pointer nobody has moved for a while; show it as the pad opens, before the user aims
        if (padOpened && state.value == PointerLinkState.READY) wake()
        if (sensing) {
            sensors.start { sample ->
                calibration?.add(sample.angular)
                restBias.add(sample)?.let(::adoptRestBias)
                pipeline.onMotion(sample)?.let(::send)
            }
        } else {
            sensors.stop()
            restBias.reset()
            pipeline.reset()
        }
        return true
    }

    /**
     * Takes the drift read off a resting phone as the one to correct by. The readings it came from
     * were raw, so it replaces the old correction, like a manual calibration. Not while one runs.
     */
    private fun adoptRestBias(bias: Vec3) {
        if (calibration != null) return
        val old = pipeline.gyroBias
        if (maxOf(abs(bias.x - old.x), abs(bias.y - old.y), abs(bias.z - old.z)) < AUTO_CALIBRATION_MIN_CHANGE_RAD_S) return
        Log.i(TAG, "Gyro drift read off a resting phone: $bias (was $old)")
        store.gyroBias = bias
        pipeline.gyroBias = bias
    }

    /** Lets the pointer go: stops the sensors and gives the Bluetooth mouse role back. Call from the main thread. */
    fun stop() {
        sensingNow = false
        sensors.stop()
        restBias.reset()
        pipeline.reset()
        wheelFlush?.cancel()
        panFlush?.cancel()
        wheel.reset()
        pan.reset()
        hidSink.stop()
    }

    /** Forgets the pairing of the TV known by [tvKeys], so the air mouse has to be set up again for it. */
    fun unlink(tvKeys: List<String>) {
        links.clear(tvKeys)
    }

    /** Starts pairing with the device at [address], one the search found and the user picked. Call from the main thread. */
    fun pairWith(address: String) = hidSink.pairWith(address)

    /** Looks for the TV again after the setup's search ran out. Call from the main thread. */
    fun retrySearch() = hidSink.retrySearch()

    /**
     * A finger came down on the pad or a button (true), or left it. While it is down the pointer
     * doesn't follow the phone, and the jolt of it landing is undone, so a click that follows
     * lands on what the user was aiming at. The pointer stays still a moment longer after it lifts.
     */
    fun press(down: Boolean) {
        unfreeze?.cancel()
        if (down) {
            pipeline.rewind()?.let(::send)
            pipeline.freeze()
        } else {
            unfreeze = scope.launch {
                delay(STILL_AFTER_PRESS_MS)
                pipeline.unfreeze()
            }
        }
    }

    /** The finger slid by [dxDp], [dyDp] over [dtSeconds] on the pad used as a mouse pad. */
    fun slide(dxDp: Float, dyDp: Float, dtSeconds: Float) {
        pipeline.onSlide(dxDp, dyDp, dtSeconds)?.let(::send)
    }

    fun button(button: PointerButton, down: Boolean) {
        events.trySend(PointerEvent.Button(button, down))
    }

    /** Scrolls at the pointer: [dy] notches of the wheel, up being positive, and [dx] sideways, right being positive. */
    fun scroll(dx: Float, dy: Float) {
        // Paced, so a TV app that animates each turn of the wheel gets every notch (see [WheelPacer])
        if (dy != 0f) {
            val toSend = wheel.add(dy, SystemClock.uptimeMillis())
            if (toSend != null) events.trySend(PointerEvent.Scroll(0f, toSend)) else scheduleWheelFlush()
        }
        if (dx != 0f) {
            val toSend = pan.add(dx, SystemClock.uptimeMillis())
            if (toSend != null) events.trySend(PointerEvent.Scroll(toSend, 0f)) else schedulePanFlush()
        }
    }

    /** The same as [scheduleWheelFlush], for the sideways wheel. */
    @Synchronized
    private fun schedulePanFlush() {
        val due = pan.nextDueMs ?: return
        panFlush?.cancel()
        panFlush = scope.launch {
            delay((due - SystemClock.uptimeMillis()).coerceAtLeast(0))
            pan.flush(SystemClock.uptimeMillis())?.let { events.trySend(PointerEvent.Scroll(it, 0f)) }
        }
    }

    /**
     * Sends the notches the pacer held back once their time has come. Started afresh on every held-back
     * notch rather than only when none is waiting: a send that is just finishing would otherwise still
     * count as waiting, and the notch that came in meanwhile would be left until the next one.
     */
    @Synchronized
    private fun scheduleWheelFlush() {
        val due = wheel.nextDueMs ?: return
        wheelFlush?.cancel()
        wheelFlush = scope.launch {
            delay((due - SystemClock.uptimeMillis()).coerceAtLeast(0))
            wheel.flush(SystemClock.uptimeMillis())?.let { events.trySend(PointerEvent.Scroll(0f, it)) }
        }
    }

    /** Brings the pointer back to about the middle of the screen, for when it has been lost at an edge. */
    fun recenter() {
        // Held still meanwhile: a hand that moves while the pointer is walked back would take it somewhere else
        unfreeze?.cancel()
        pipeline.freeze()
        Log.i(TAG, "Recenter: queued, link ${state.value}")
        events.trySend(PointerEvent.Paced(recenterMoves(), holdStill = true))
    }

    /**
     * Measures the gyroscope's resting drift while the phone lies still for [CALIBRATION_SECONDS],
     * and takes it off every reading from then on. Returns false, changing nothing, if the phone
     * was moved meanwhile or there is no gyroscope. The session must be started.
     */
    suspend fun calibrate(): Boolean {
        if (!sensors.hasGyroscope) return false
        val measuring = GyroCalibration()
        calibration = measuring
        try {
            delay(CALIBRATION_SECONDS * 1000L)
        } finally {
            calibration = null
        }
        // Measured on the sensor's own readings, before any earlier correction, so this replaces the old one
        val bias = measuring.result() ?: return false
        store.gyroBias = bias
        pipeline.gyroBias = bias
        return true
    }

    private fun send(move: PointerMove) {
        events.trySend(PointerEvent.Move(move.dx, move.dy))
    }

    private suspend fun work() {
        for (event in events) {
            try {
                when (event) {
                    is PointerEvent.Move -> sink.move(event.dx, event.dy)
                    is PointerEvent.Button -> sink.button(event.button, event.down)
                    is PointerEvent.Scroll -> sink.scroll(event.dx, event.dy)
                    is PointerEvent.Paced -> {
                        val startedMs = SystemClock.uptimeMillis()
                        var sent = 0
                        try {
                            for (move in event.moves) {
                                sink.move(move.dx, move.dy)
                                sent++
                                delay(PACED_MOVE_GAP_MS)
                            }
                        } finally {
                            if (event.holdStill) {
                                pipeline.unfreeze()
                                // 10 ms a move is what the walk back counts on, so a long total says the TV got fewer
                                Log.i(TAG, "Recenter: sent $sent of ${event.moves.size} moves in ${SystemClock.uptimeMillis() - startedMs} ms")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One lost movement is not worth stopping the pointer for
                Log.w(TAG, "pointer event failed", e)
            }
        }
    }
}
