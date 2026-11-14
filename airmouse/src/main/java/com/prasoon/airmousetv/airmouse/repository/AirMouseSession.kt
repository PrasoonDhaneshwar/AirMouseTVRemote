package com.prasoon.airmousetv.airmouse.repository

import android.content.Context
import android.util.Log
import com.prasoon.airmousetv.airmouse.api.PointerMove
import com.prasoon.airmousetv.airmouse.api.PointerPipeline
import com.prasoon.airmousetv.airmouse.api.PointerSink
import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.airmouse.model.TvSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "AirMouseSession"

/** Something to send to the TV's pointer, applied in order. */
private sealed interface PointerEvent {
    data class Move(val dx: Float, val dy: Float) : PointerEvent
    data class Button(val button: PointerButton, val down: Boolean) : PointerEvent
    data class Scroll(val notches: Float) : PointerEvent
}

/**
 * The air mouse for one TV: owns the link that carries the pointer and feeds it from the phone's
 * motion and the pad's touches.
 *
 * It knows nothing of the Wi-Fi remote, and the remote nothing of it, so the keys keep working
 * whatever happens to the pointer. The app keeps one for its whole life, like the remote session, so a pointer link
 * survives a configuration change.
 *
 * Movement arrives from the sensor thread and touches from the main thread. Both are queued and
 * one worker sends them, so a button press can't overtake the movement that led up to it.
 */
class AirMouseSession private constructor(
    private val hidSink: BluetoothHidSink,
    private val sensors: MotionSensorSource,
    private val links: AirMouseLinkStore,
) {
    private val sink: PointerSink = hidSink
    private val pipeline = PointerPipeline()
    private val events = Channel<PointerEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The air mouse of this app, built on the application [context]. */
    constructor(context: Context) : this(BluetoothHidSink(context), MotionSensorSource(context), AirMouseLinkStore(context))

    /** The TV the session was last started for; a TV that pairs during setup is saved under this name. */
    @Volatile private var tvName: String? = null

    val state: StateFlow<PointerLinkState> get() = sink.state

    /** False on phones that can never be a Bluetooth mouse, where the air mouse isn't offered at all. */
    val available: Boolean get() = hidSink.supported

    val hasGyroscope: Boolean get() = sensors.hasGyroscope

    /** The name the TV lists this phone under while pairing, or null if it can't be read. */
    val phoneName: String? get() = hidSink.phoneName

    /** During setup: what the phone's own search for the TV over Bluetooth has found. */
    val search: StateFlow<TvSearch> get() = hidSink.search

    /** Starts pairing with the device at [address], one the search found and the user picked. Call from the main thread. */
    fun pairWith(address: String) = hidSink.pairWith(address)

    /** Called when the TV's Bluetooth pairing turns out to be gone and the air mouse needs setting up again. */
    var onUnlinked: () -> Unit = {}

    /** Receives events worth keeping for a bug report. */
    var onNote: (String) -> Unit
        get() = hidSink.onNote
        set(value) { hidSink.onNote = value }

    init {
        hidSink.onLinked = { address -> tvName?.let { links.save(it, address) } }
        hidSink.onUnlinked = {
            tvName?.let(links::clear)
            onUnlinked()
        }
        scope.launch { work() }
    }

    /** True once the TV called [tvName] has been paired for the pointer. */
    fun isLinked(tvName: String): Boolean = links.addressFor(tvName) != null

    /**
     * Brings the pointer up for the TV called [tvName]: connects to it if it has been set up, or
     * waits for it to pair if not. Returns false, having done nothing, if the Bluetooth permission
     * is missing. Call from the main thread.
     */
    fun start(tvName: String, tvNames: List<String>): Boolean {
        this.tvName = tvName
        if (!hidSink.start(links.addressFor(tvName), tvNames)) return false
        sensors.start { sample -> pipeline.onMotion(sample)?.let(::send) }
        return true
    }

    /** Lets the pointer go: stops the sensors and gives the Bluetooth mouse role back. Call from the main thread. */
    fun stop() {
        sensors.stop()
        pipeline.thumbUp()
        hidSink.stop()
    }

    /** Forgets the pairing of the TV called [tvName], so the air mouse has to be set up again for it. */
    fun unlink(tvName: String) {
        links.clear(tvName)
    }

    /** The thumb touched the pad or left it. The pointer follows the phone only while it is down. */
    fun thumb(down: Boolean) {
        if (down) pipeline.thumbDown() else pipeline.thumbUp()
    }

    /** The thumb slid by [dxDp], [dyDp] over [dtSeconds]. */
    fun slide(dxDp: Float, dyDp: Float, dtSeconds: Float) {
        pipeline.onSlide(dxDp, dyDp, dtSeconds)?.let(::send)
    }

    fun button(button: PointerButton, down: Boolean) {
        events.trySend(PointerEvent.Button(button, down))
    }

    /** Scrolls at the pointer by [notches] of the wheel, up being positive. */
    fun scroll(notches: Float) {
        events.trySend(PointerEvent.Scroll(notches))
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
                    is PointerEvent.Scroll -> sink.scroll(0f, event.notches)
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
