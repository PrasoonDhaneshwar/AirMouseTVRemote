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
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.prasoon.airmousetv.airmouse.api.HID_MOUSE_DESCRIPTOR
import com.prasoon.airmousetv.airmouse.api.HID_MOUSE_REPORT_ID
import com.prasoon.airmousetv.airmouse.api.HidMouse
import com.prasoon.airmousetv.airmouse.api.HidRegistration
import com.prasoon.airmousetv.airmouse.api.PointerSink
import com.prasoon.airmousetv.airmouse.api.hidLinkState
import com.prasoon.airmousetv.airmouse.api.isTvClass
import com.prasoon.airmousetv.airmouse.api.looksLikePairedTv
import com.prasoon.airmousetv.airmouse.api.matchesTvName
import com.prasoon.airmousetv.airmouse.api.tvCandidates
import com.prasoon.airmousetv.airmouse.api.tvToPairUnasked
import com.prasoon.airmousetv.airmouse.model.NearbyDevice
import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.airmouse.model.TvSearch
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

/**
 * A connection the just-paired TV closes within this long of being asked for is taken as refused, not
 * as a TV that is slow to answer. The refusals measured came within a second.
 */
private const val INSTANT_DROP_MS = 3000L

/** How many times setup gives the mouse role up and takes it again for a TV that keeps dropping the connection. */
private const val MAX_ROLE_RETAKES = 2

/** How often the mouse role is asked for again after Android took it away, until the app is back in front and gets it. */
private const val REREGISTER_DELAY_MS = 1500L

/**
 * How many Bluetooth scans in a row the setup runs while looking for the TV. One scan takes about
 * 12 seconds, so this is two minutes: long enough to walk through the TV's settings, and not a
 * radio left scanning for as long as the screen stays open.
 */
private const val MAX_SEARCH_ROUNDS = 10

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

    private val _search = MutableStateFlow(TvSearch())

    /** During setup: what the phone's own search for the TV has found. */
    val search: StateFlow<TvSearch> = _search.asStateFlow()

    private val mouse = HidMouse()
    private var link: HidLink? = null

    /** True if the app may use Bluetooth. Before Android 12 the permission comes with the install. */
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /**
     * True if the phone may look for the TV itself. Scanning without the location permission exists
     * from Android 12; before that the TV has to find the phone instead.
     */
    fun canSearch(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    /** The name the TV lists this phone under while pairing, or null if it can't be read. */
    val phoneName: String?
        @SuppressLint("MissingPermission") // checked by hasPermission()
        get() = if (hasPermission()) adapter?.name else null

    /**
     * Takes the mouse role and connects to the TV at [linkedAddress], or waits for a TV to pair if
     * that is null, looking for it under [tvNames], the names it has on the network. Returns false,
     * having done nothing, if the Bluetooth permission is missing.
     */
    fun start(linkedAddress: String?, tvNames: List<String>): Boolean {
        if (!supported || adapter == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _state.value = PointerLinkState.UNSUPPORTED
            return true
        }
        if (!hasPermission()) return false
        if (link != null) return true
        link = HidLink(
            context, adapter, mouse, linkedAddress, tvNames, canSearch(),
            onState = { _state.value = it },
            onLinked = { onLinked(it) },
            onUnlinked = { onUnlinked() },
            onNote = { onNote(it) },
            onSearch = { _search.value = it }
        ).also { it.open() }
        return true
    }

    /** Gives the mouse role back, which also disconnects the TV's pointer. */
    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) link?.close()
        link = null
        _state.value = PointerLinkState.IDLE
        _search.value = TvSearch()
    }

    /**
     * Finds out, without taking the mouse role or asking the user for anything, whether this phone
     * has it, and calls [onKnown] on the main thread. Android only runs the profile's service on
     * phones whose maker left it in, so being handed the profile at all is the answer. Does nothing
     * while Bluetooth is off: the services of every profile are stopped then, and "no answer" would
     * be mistaken for "not there".
     */
    fun probeRole(onKnown: (Boolean) -> Unit) {
        val bluetooth = adapter
        if (!supported || bluetooth == null) return onKnown(false)
        // Reading whether Bluetooth is on already needs the permission, which a new install lacks until
        // the setup asks for it. The role then stays unknown, and the air mouse is offered all the same
        if (!hasPermission()) {
            Log.i(TAG, "Mouse role probe skipped: no Bluetooth permission yet")
            return
        }
        val main = Handler(Looper.getMainLooper())
        var answered = false
        val answer = { has: Boolean ->
            if (!answered) {
                answered = true
                onKnown(has)
            }
        }
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                bluetooth.closeProfileProxy(profile, proxy)
                answer(true)
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }
        try {
            if (!bluetooth.isEnabled) return
            if (!bluetooth.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)) return answer(false)
            main.postDelayed({ answer(false) }, PROFILE_TIMEOUT_MS)
        } catch (e: SecurityException) {
            // Some phones want the Bluetooth permission even for this; the setup finds out instead
            Log.w(TAG, "Mouse role probe not allowed", e)
        }
    }

    /** Starts pairing with the device at [address], one the search found and the user picked. */
    fun pairWith(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) link?.pairWith(address)
    }

    /** Searches for the TV again after the setup's search ran out. */
    fun retrySearch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) link?.retrySearch()
    }

    override suspend fun move(dx: Float, dy: Float) = send(mouse.move(dx, dy))

    override suspend fun button(button: PointerButton, down: Boolean) = send(listOfNotNull(mouse.button(button, down)))

    override suspend fun scroll(dx: Float, dy: Float) = send(mouse.scroll(dx, dy))

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
    private val tvNames: List<String>,
    private val canSearch: Boolean,
    private val onState: (PointerLinkState) -> Unit,
    private val onLinked: (String) -> Unit,
    private val onUnlinked: () -> Unit,
    private val onNote: (String) -> Unit,
    private val onSearch: (TvSearch) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var hid: BluetoothHidDevice? = null
    /** The TV: the one set up earlier, or during setup the first host to connect. */
    @Volatile private var target: BluetoothDevice? = linkedAddress?.let { address ->
        runCatching { adapter.getRemoteDevice(address) }.getOrNull()
    }
    @Volatile private var connected = false

    /** How many times the known TV has been asked to connect since the link last came up; keeps the report short. */
    private var connectTries = 0
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

    /** During setup: devices the scan has seen that aren't computers or phones, by address. */
    private val found = LinkedHashMap<String, NearbyDevice>()
    private var searching = false
    private var searchRounds = 0

    /** True once a search has run out without pairing; cleared when another starts. See [TvSearch.over]. */
    private var searchOver = false

    /** During setup: the device a pairing has been started with and not yet finished or failed. */
    private var pairing: BluetoothDevice? = null

    /** During setup: the device that paired most recently, whoever started it. It is the likeliest TV, so it is offered the connection first. */
    private var justBonded: BluetoothDevice? = null

    /** During setup: the device last asked to take the mouse connection, and when (see [droppedAtOnce]). */
    private var askedDevice: BluetoothDevice? = null
    private var askedAtMs = 0L

    /** How many times the mouse role has been given up and taken again because a just-paired TV dropped the connection at once. */
    private var roleRetakes = 0
    private var retakingRole = false

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
                note(if (retakingRole) "HID device role given up on purpose; taking it again" else "HID device role taken away; asking again")
                retakingRole = false
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
            note("Mouse connection ${connectionName(state)}: ${describe(device)}, ${if (device == target) "the TV" else if (target == null) "no TV known yet" else "not the TV"}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (target == null && !mayBeTv(device)) {
                        note("Mouse connection from a computer or phone turned away: ${describe(device)}")
                        hid?.disconnect(device)
                    } else if (target == null) {
                        main.removeCallbacks(nextCandidate)
                        candidates.clear()
                        pairing = null
                        stopSearch()
                        target = device
                        publishSearch()
                        note("TV paired for the pointer")
                        onLinked(device.address)
                    }
                    if (device == target) {
                        main.removeCallbacks(reconnect)
                        connected = true
                        connectTries = 0
                        dropped.set(0)
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
                } else if (target == null && registration == HidRegistration.REGISTERED && droppedAtOnce(device)) {
                    retakeRole()
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
                        searching = false
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = deviceIn(intent) ?: return
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    note(
                        "Bond ${bondName(bondState)}: ${describe(device)}; role $registration, " +
                            (if (device == target) "the TV" else if (device == pairing) "the device being paired" else "other")
                    )
                    if (bondState == BluetoothDevice.BOND_BONDED && target == null && mayBeTv(device)) justBonded = device
                    when (bondState) {
                        // The device that has just paired is the likeliest TV, so it goes to the front
                        BluetoothDevice.BOND_BONDED -> if (target == null && registration == HidRegistration.REGISTERED) {
                            candidates.remove(device)
                            candidates.addFirst(device)
                            main.removeCallbacks(nextCandidate)
                            main.postDelayed(nextCandidate, CONNECT_AFTER_BOND_MS)
                        } else if (target == null) {
                            // Paired while Android's dialog had the role: the offer waits for the role to come back
                            note("Bonded without the mouse role; the connection is offered when it is back")
                        }
                        BluetoothDevice.BOND_NONE -> when (device) {
                            target -> unlink("TV pairing removed")
                            // Refused or timed out on one side: back to looking, so it can be tried again
                            pairing -> {
                                note("Pairing did not complete")
                                pairing = null
                                searchRounds = 0
                                startSearch()
                                publishSearch()
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = deviceIn(intent) ?: return
                    if (!mayBeTv(device)) return
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: device.name
                    val looksLikeTv = device.bluetoothClass?.let { isTvClass(it.deviceClass) } == true
                    // Said once per device: a scan reports the same one over and over
                    if (device.address !in found) {
                        note(
                            "Found nearby: ${describe(device)}; the TV's name: ${matchesTvName(name, tvNames)}, " +
                                "looks like a TV: $looksLikeTv, already paired: ${device.bondState == BluetoothDevice.BOND_BONDED}"
                        )
                    }
                    found[device.address] = NearbyDevice(device.address, name, looksLikeTv)
                    // The one device carrying the TV's name needs no question asked
                    if (searching) tvToPairUnasked(found.values.toList(), tvNames)?.let { pairWith(it.address) }
                    publishSearch()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (searching) {
                    val another = ++searchRounds < MAX_SEARCH_ROUNDS && adapter.startDiscovery()
                    if (!another) {
                        // Out of rounds, or Android won't start another (it limits how often): either way the search is over
                        searching = false
                        searchOver = true
                        note("Search over after $searchRounds rounds; ${found.size} devices seen, none paired with")
                        publishSearch()
                    }
                }
            }
            publish()
        }
    }

    private fun deviceIn(intent: Intent): BluetoothDevice? =
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
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // Exported, because these come from the Bluetooth app, which is not the system process and
        // would not reach a receiver that isn't. All four are protected: no other app can send them
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        if (adapter.isEnabled) requestProfile()
        publish()
        // During setup the screen needs to know at once whether the phone looks for the TV itself, not
        // only once the paired devices have been tried
        if (target == null) publishSearch()
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
        val bonded = adapter.bondedDevices.orEmpty()
        // Only those that look like the TV: a phone paired with headphones, speakers and a car would
        // otherwise spend 6 s on each before the search even starts (78 s with 13 devices, measured)
        bonded.filterTo(candidates) { mayBeTv(it) && looksLikePairedTv(it.name, it.bluetoothClass?.deviceClass, tvNames) }
        // The device that has just paired is tried before older pairings, whoever started the pairing
        listOfNotNull(justBonded, pairing).forEach { fresh ->
            if (mayBeTv(fresh) && fresh in bonded) { candidates.remove(fresh); candidates.addFirst(fresh) }
        }
        note("Offering the mouse connection: ${candidates.size} of ${bonded.size} paired devices look like the TV")
        offerToNextCandidate()
    }

    /**
     * A device for a bug report: the end of its address and its Bluetooth class, which decides whether
     * it can be taken for the TV. Its name is left out, since reports get pasted into public places.
     */
    private fun describe(device: BluetoothDevice): String {
        val cls = device.bluetoothClass
        return "…${device.address.takeLast(5)} (class ${cls?.majorDeviceClass?.let { "0x%X".format(it) } ?: "?"}/${cls?.deviceClass?.let { "0x%X".format(it) } ?: "?"})"
    }

    private fun bondName(state: Int) = when (state) {
        BluetoothDevice.BOND_NONE -> "none"
        BluetoothDevice.BOND_BONDING -> "in progress"
        BluetoothDevice.BOND_BONDED -> "done"
        else -> "state $state"
    }

    private fun connectionName(state: Int) = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
        BluetoothProfile.STATE_CONNECTING -> "connecting"
        BluetoothProfile.STATE_CONNECTED -> "connected"
        BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
        else -> "state $state"
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
        val candidate = candidates.removeFirstOrNull() ?: run {
            // Nobody the phone is paired with took the connection: look for the TV instead
            note("No paired device took the mouse connection; searching instead")
            pairing = null
            startSearch()
            publishSearch()
            return
        }
        askedDevice = candidate
        askedAtMs = SystemClock.elapsedRealtime()
        val asked = device.connect(candidate)
        note("Asked ${describe(candidate)} to take the mouse connection (accepted by Bluetooth: $asked); ${candidates.size} more to try")
        main.removeCallbacks(nextCandidate)
        main.postDelayed(nextCandidate, CANDIDATE_TIMEOUT_MS)
    }

    /**
     * True if [device], the one that has just paired, closed the mouse connection within
     * [INSTANT_DROP_MS] of being asked for it, and the role hasn't been retaken too often already.
     * A TV that was merely slow doesn't answer at all; one that answers with a disconnect at once is
     * refusing, as seen on a Hisense Android TV 9 whose pairing the TV had started.
     */
    private fun droppedAtOnce(device: BluetoothDevice): Boolean =
        !retakingRole && device == askedDevice && (device == justBonded || device == pairing) &&
            SystemClock.elapsedRealtime() - askedAtMs < INSTANT_DROP_MS && roleRetakes < MAX_ROLE_RETAKES

    /**
     * Gives the mouse role up and takes it again, then offers the connection afresh. That is what made
     * a TV accept it in the run that prompted this: the role was lost to Android's own dialog, regained,
     * and the very next offer to the same TV went through. [onAppStatusChanged] does the asking again.
     */
    private fun retakeRole() {
        roleRetakes++
        note("The just-paired TV dropped the mouse connection at once; taking the mouse role again ($roleRetakes of $MAX_ROLE_RETAKES)")
        main.removeCallbacks(nextCandidate)
        candidates.clear()
        retakingRole = true
        val device = hid
        if (device == null || !runCatching { device.unregisterApp() }.getOrDefault(false)) {
            // Nothing to give up, or Android won't let go: carry on with the next device as before
            retakingRole = false
            offerToNextCandidate()
        }
    }

    /**
     * Looks for the TV over Bluetooth, where the phone is allowed to. A TV can be found while its
     * "add accessory" screen is open, and some can at any time. Finding it lets the phone start
     * the pairing, which the TV then only has to confirm: nothing has to be picked from a list on
     * the TV, and the TV doesn't need to recognise the phone as an accessory.
     */
    private fun startSearch() {
        if (!canSearch || closed || target != null || pairing != null || !adapter.isEnabled) {
            note("Search not started: can search $canSearch, TV known ${target != null}, pairing ${pairing != null}, Bluetooth on ${adapter.isEnabled}")
            return
        }
        searching = true
        searchOver = false
        val started = adapter.isDiscovering || adapter.startDiscovery()
        note("Search started (a scan was already running: ${adapter.isDiscovering}; scan accepted: $started)")
    }

    /** Looks for the TV again after a search ran out, for a user who has restarted the TV or opened its "add accessory" screen. */
    fun retrySearch() {
        if (closed || target != null || pairing != null || !searchOver) return
        searchRounds = 0
        found.clear()
        startSearch()
        publishSearch()
    }

    private fun stopSearch() {
        if (!searching) return
        searching = false
        adapter.cancelDiscovery()
        note("Search stopped")
    }

    /**
     * Pairs with the device at [address] and, once paired, offers it the mouse connection. The
     * scan is stopped first: a radio that is scanning connects slowly or not at all.
     */
    fun pairWith(address: String) {
        if (closed || target != null || pairing != null) return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        stopSearch()
        pairing = device
        note("Pairing with a device found nearby")
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            offerToPairedDevices()
        } else if (!device.createBond()) {
            note("Pairing could not be started")
            pairing = null
            startSearch()
        }
        publishSearch()
    }

    private fun publishSearch() {
        if (closed) return
        val pairingName = pairing?.let { found[it.address]?.name ?: it.name ?: it.address }
        onSearch(
            TvSearch(
                possible = canSearch,
                candidates = tvCandidates(found.values.toList(), tvNames),
                pairingWith = pairingName,
                over = searchOver && !searching && pairing == null
            )
        )
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
        val accepted = device.connect(tv)
        // Retried every few seconds, so only the first try and then every tenth go in the report
        if (connectTries++ % 10 == 0) note("Connecting to the TV: ${describe(tv)} (accepted by Bluetooth: $accepted; try $connectTries)")
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
        val device = hid
        val tv = target
        if (device == null || tv == null || !connected) return noteDropped("no link")
        try {
            if (!device.sendReport(tv, HID_MOUSE_REPORT_ID, report)) noteDropped("refused by Bluetooth")
        } catch (e: RuntimeException) {
            // The Bluetooth process can die under us; the link state will say so a moment later
            Log.w(TAG, "sendReport failed", e)
        }
    }

    /** Reports that never reached the TV since the link last came up; see [noteDropped]. */
    private val dropped = java.util.concurrent.atomic.AtomicInteger()

    /**
     * Counts a report that didn't go out, and says so for the first and then every hundredth: a
     * pointer that stops short (a recenter that ends a quarter of the way) is otherwise silent. A
     * warning, so it also shows in release builds.
     */
    private fun noteDropped(reason: String) {
        val total = dropped.incrementAndGet()
        if (total == 1 || total % 100 == 0) Log.w(TAG, "Mouse report dropped ($reason); $total since the link came up")
    }

    fun close() {
        closed = true
        main.removeCallbacks(profileTimeout)
        main.removeCallbacks(reconnect)
        main.removeCallbacks(nextCandidate)
        main.removeCallbacks(register)
        stopSearch()
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
