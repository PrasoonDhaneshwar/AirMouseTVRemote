package com.prasoon.airmousetv.airmouse.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.prasoon.airmousetv.airmouse.api.HID_MOUSE_DESCRIPTOR
import com.prasoon.airmousetv.airmouse.api.HID_MOUSE_REPORT_ID
import com.prasoon.airmousetv.airmouse.api.HidMouse
import com.prasoon.airmousetv.airmouse.api.HidRegistration
import com.prasoon.airmousetv.airmouse.api.PointerSink
import com.prasoon.airmousetv.airmouse.api.hidLinkState
import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "BluetoothHidSink"

/**
 * How long Android gets to hand over the mouse role before the phone is taken not to have it. On a
 * phone whose maker removed the profile the request is never answered, and it normally is within a
 * second.
 */
private const val PROFILE_TIMEOUT_MS = 5000L

/** How long to wait before trying the TV again after the Bluetooth link dropped or a connect failed. */
private const val RECONNECT_DELAY_MS = 3000L

/**
 * How long a TV that has just paired gets to open the mouse connection itself before the phone
 * opens it. Most TVs connect within a second of pairing; some pair and then wait for the accessory.
 */
private const val CONNECT_AFTER_BOND_MS = 2000L

/**
 * How long one paired device gets to accept the mouse connection during setup before the next one
 * is tried. A device that takes mice answers within a second or two; one that is off never does.
 */
private const val CANDIDATE_TIMEOUT_MS = 6000L

/** How often the mouse role is asked for again after Android took it away, until the app is back in front and gets it. */
private const val REREGISTER_DELAY_MS = 1500L

/**
 * The pointer over Bluetooth: the phone presents itself to the TV as a mouse (the HID device
 * role, in Android since 9), and the TV draws its own pointer and treats the reports as real mouse
 * input. Nothing is installed on the TV.
 *
 * The role belongs to the app only while it is in front; Android takes it away in the background.
 * So it is taken with [start] when the air mouse shows and given back with [stop] when it goes.
 * Call both from the main thread.
 *
 * Pairing is the system's: the TV pairs with the phone from its own Bluetooth settings. With no TV
 * known yet (during setup), the mouse connection is also offered to the devices the phone is paired
 * with, and the first one to have it open, whichever side opened it, is taken as the TV and
 * announced through [onLinked].
 */
class BluetoothHidSink(
    private val context: Context
) : PointerSink {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** False on phones that can never be a Bluetooth mouse: older than Android 9, or without Bluetooth. */
    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && adapter != null

    private val _state = MutableStateFlow(PointerLinkState.IDLE)
    override val state: StateFlow<PointerLinkState> = _state.asStateFlow()

    /** Called with the Bluetooth address of a TV that connected during setup. */
    var onLinked: (address: String) -> Unit = {}

    /** Called when the TV's pairing turns out to be gone, so the air mouse has to be set up again. */
    var onUnlinked: () -> Unit = {}

    /** Receives events worth keeping for a bug report. */
    var onNote: (String) -> Unit = {}

    private val mouse = HidMouse()
    private var link: HidLink? = null

    /** True if the app may use Bluetooth. Before Android 12 the permission comes with the install. */
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** The name the TV lists this phone under while pairing, or null if it can't be read. */
    val phoneName: String?
        @SuppressLint("MissingPermission") // checked by hasPermission()
        get() = if (hasPermission()) adapter?.name else null

    /**
     * Takes the mouse role and connects to the TV at [linkedAddress], or waits for a TV to pair if
     * that is null. Returns false, having done nothing, if the Bluetooth permission is missing.
     */
    fun start(linkedAddress: String?): Boolean {
        if (!supported || adapter == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _state.value = PointerLinkState.UNSUPPORTED
            return true
        }
        if (!hasPermission()) return false
        if (link != null) return true
        link = HidLink(
            context, adapter, mouse, linkedAddress,
            onState = { _state.value = it },
            onLinked = { onLinked(it) },
            onUnlinked = { onUnlinked() },
            onNote = { onNote(it) }
        ).also { it.open() }
        return true
    }

    /** Gives the mouse role back, which also disconnects the TV's pointer. */
    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) link?.close()
        link = null
        _state.value = PointerLinkState.IDLE
    }

    override suspend fun move(dx: Float, dy: Float) = send(mouse.move(dx, dy))

    override suspend fun button(button: PointerButton, down: Boolean) = send(listOfNotNull(mouse.button(button, down)))

    /** Sideways scrolling is dropped: the mouse the TV was told about has one wheel. */
    override suspend fun scroll(dx: Float, dy: Float) = send(mouse.scroll(dy))

    private fun send(reports: List<ByteArray>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val current = link ?: return
        reports.forEach(current::send)
    }
}

/**
 * One stretch of holding the mouse role, from [open] to [close]. Everything except [send] runs on
 * the main thread, where the Bluetooth callbacks are delivered too.
 */
@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission") // BluetoothHidSink.start() only creates this once the permission is held
private class HidLink(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val mouse: HidMouse,
    linkedAddress: String?,
    private val onState: (PointerLinkState) -> Unit,
    private val onLinked: (String) -> Unit,
    private val onUnlinked: () -> Unit,
    private val onNote: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var hid: BluetoothHidDevice? = null
    /** The TV: the one set up earlier, or during setup the first host to connect. */
    @Volatile private var target: BluetoothDevice? = linkedAddress?.let { address ->
        runCatching { adapter.getRemoteDevice(address) }.getOrNull()
    }
    @Volatile private var connected = false
    private var registration = HidRegistration.PENDING
    private var closed = false

    /**
     * True once Android has given this link the mouse role. Losing it after that is not a refusal:
     * Android takes the role from an app the moment anything covers it, its own pairing and
     * "make visible" dialogs included, so it is simply asked for again until it comes back.
     */
    private var heldBefore = false

    private val register = Runnable { register() }

    private val profileTimeout = Runnable {
        if (registration != HidRegistration.PENDING) return@Runnable
        // No profile object at all means the phone has no such profile; having one that then never
        // confirms the app means the role was not given
        registration = if (hid == null) HidRegistration.NO_PROFILE else HidRegistration.REFUSED
        note("HID device role: no answer from Android ($registration)")
        publish()
    }

    private val reconnect = Runnable { connectTarget() }

    /** During setup: paired devices still to be offered the mouse connection, one at a time. */
    private val candidates = ArrayDeque<BluetoothDevice>()
    private val nextCandidate = Runnable { offerToNextCandidate() }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (closed) {
                adapter.closeProfileProxy(profile, proxy)
                return
            }
            hid = proxy as BluetoothHidDevice
            register()
        }

        override fun onServiceDisconnected(profile: Int) {
            // Bluetooth was switched off; onBluetoothOn asks again
            hid = null
            connected = false
            publish()
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (closed) return
            main.removeCallbacks(profileTimeout)
            if (registered) {
                registration = HidRegistration.REGISTERED
                heldBefore = true
                note("HID device role held")
                if (target != null) connectTarget() else offerToPairedDevices()
            } else if (heldBefore) {
                connected = false
                registration = HidRegistration.PENDING
                note("HID device role taken away; asking again")
                main.removeCallbacks(register)
                main.postDelayed(register, REREGISTER_DELAY_MS)
            } else {
                registration = HidRegistration.REFUSED
                note("HID device role refused")
            }
            publish()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (closed) return
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (target == null && !mayBeTv(device)) {
                        note("Mouse connection from a computer or phone turned away")
                        hid?.disconnect(device)
                    } else if (target == null) {
                        main.removeCallbacks(nextCandidate)
                        candidates.clear()
                        target = device
                        note("TV paired for the pointer")
                        onLinked(device.address)
                    }
                    if (device == target) {
                        main.removeCallbacks(reconnect)
                        connected = true
                        note("Pointer link up")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> if (device == target) {
                    if (connected) note("Pointer link down")
                    connected = false
                    // Whatever was held when the link went must not still be held when it returns
                    mouse.reset()
                    main.removeCallbacks(reconnect)
                    main.postDelayed(reconnect, RECONNECT_DELAY_MS)
                }
            }
            publish()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (closed) return
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_ON -> requestProfile()
                    BluetoothAdapter.STATE_OFF -> {
                        hid = null
                        connected = false
                        main.removeCallbacks(reconnect)
                        main.removeCallbacks(nextCandidate)
                        main.removeCallbacks(register)
                        main.removeCallbacks(profileTimeout)
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = bondedDevice(intent) ?: return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                        // The device that has just paired is the likeliest TV, so it goes to the front
                        BluetoothDevice.BOND_BONDED -> if (target == null && registration == HidRegistration.REGISTERED) {
                            candidates.remove(device)
                            candidates.addFirst(device)
                            main.removeCallbacks(nextCandidate)
                            main.postDelayed(nextCandidate, CONNECT_AFTER_BOND_MS)
                        }
                        BluetoothDevice.BOND_NONE -> if (device == target) unlink("TV pairing removed")
                    }
                }
            }
            publish()
        }
    }

    private fun bondedDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    fun open() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        // Exported, because these come from the Bluetooth app, which is not the system process and
        // would not reach a receiver that isn't. Both are protected: no other app can send them
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        if (adapter.isEnabled) requestProfile()
        publish()
    }

    /** Asks Android for the mouse role. The answer comes through onAppStatusChanged. */
    private fun register() {
        val device = hid ?: return
        if (closed) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Air Mouse", "Pointer for Android TV", "Air Mouse TV Remote",
            BluetoothHidDevice.SUBCLASS1_MOUSE, HID_MOUSE_DESCRIPTOR
        )
        if (device.registerApp(sdp, null, null, ContextCompat.getMainExecutor(context), callback)) return
        main.removeCallbacks(profileTimeout)
        if (heldBefore) {
            // Still covered by a system dialog, or in the background: keep asking
            main.removeCallbacks(register)
            main.postDelayed(register, REREGISTER_DELAY_MS)
        } else {
            registration = HidRegistration.REFUSED
            note("HID device role refused: another app may hold it")
            publish()
        }
    }

    private fun requestProfile() {
        if (hid != null) return
        registration = HidRegistration.PENDING
        main.removeCallbacks(profileTimeout)
        if (adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)) {
            main.postDelayed(profileTimeout, PROFILE_TIMEOUT_MS)
        } else {
            registration = HidRegistration.NO_PROFILE
            note("HID device profile not available on this phone")
        }
    }

    /**
     * During setup, offers the mouse connection to the devices the phone is already paired with.
     * A TV only opens it by itself if the phone was a mouse at the moment the two paired. One that
     * was paired earlier, or while Android's pairing dialog had pushed the app into the
     * background, knows the phone as something else and waits to be asked (seen on a Hisense
     * Android TV 9, which then listed the phone with a game pad icon and never connected).
     */
    private fun offerToPairedDevices() {
        candidates.clear()
        adapter.bondedDevices.orEmpty().filterTo(candidates, ::mayBeTv)
        offerToNextCandidate()
    }

    /**
     * False for computers and phones. They take mice too, and during setup the pointer must not
     * end up driving a laptop that happens to be paired and in the room.
     */
    private fun mayBeTv(device: BluetoothDevice): Boolean {
        val kind = device.bluetoothClass?.majorDeviceClass
        return kind != BluetoothClass.Device.Major.COMPUTER && kind != BluetoothClass.Device.Major.PHONE
    }

    private fun offerToNextCandidate() {
        val device = hid ?: return
        if (closed || target != null || registration != HidRegistration.REGISTERED) return
        val candidate = candidates.removeFirstOrNull() ?: return
        device.connect(candidate)
        main.removeCallbacks(nextCandidate)
        main.postDelayed(nextCandidate, CANDIDATE_TIMEOUT_MS)
    }

    /** Opens the mouse connection to the known TV. Does nothing during setup, when no TV is known yet. */
    private fun connectTarget() {
        val tv = target ?: return
        val device = hid ?: return
        if (closed || connected || registration != HidRegistration.REGISTERED) return
        if (tv.bondState != BluetoothDevice.BOND_BONDED) {
            unlink("TV no longer paired")
            publish()
            return
        }
        device.connect(tv)
        // A TV that is off or out of range gives no answer at all, so keep asking while the pad shows
        main.removeCallbacks(reconnect)
        main.postDelayed(reconnect, RECONNECT_DELAY_MS)
    }

    private fun unlink(reason: String) {
        note(reason)
        target = null
        connected = false
        main.removeCallbacks(reconnect)
        onUnlinked()
    }

    /** Sends one mouse report if the TV is connected. Safe from any thread. */
    fun send(report: ByteArray) {
        val device = hid ?: return
        val tv = target ?: return
        if (!connected) return
        try {
            device.sendReport(tv, HID_MOUSE_REPORT_ID, report)
        } catch (e: RuntimeException) {
            // The Bluetooth process can die under us; the link state will say so a moment later
            Log.w(TAG, "sendReport failed", e)
        }
    }

    fun close() {
        closed = true
        main.removeCallbacks(profileTimeout)
        main.removeCallbacks(reconnect)
        main.removeCallbacks(nextCandidate)
        main.removeCallbacks(register)
        mouse.reset()?.let(::send)
        runCatching { context.unregisterReceiver(receiver) }
        hid?.let { device ->
            runCatching { device.unregisterApp() }
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, device)
        }
        hid = null
        connected = false
    }

    private fun publish() {
        if (closed) return
        onState(
            hidLinkState(
                started = true,
                bluetoothOn = adapter.isEnabled,
                registration = registration,
                linked = target != null,
                connected = connected
            )
        )
    }

    private fun note(event: String) {
        Log.i(TAG, event)
        onNote(event)
    }
}
