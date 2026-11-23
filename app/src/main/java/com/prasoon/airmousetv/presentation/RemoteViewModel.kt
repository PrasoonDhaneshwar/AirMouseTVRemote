package com.prasoon.airmousetv.presentation

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prasoon.airmousetv.data.api.PAIRING_CODE_LENGTH
import com.prasoon.airmousetv.data.api.ScrollKeys
import com.prasoon.airmousetv.data.api.STILL_TALKING_AFTER_POWER_MS
import com.prasoon.airmousetv.data.api.lostAfterPowerOff
import com.prasoon.airmousetv.data.api.mayBeSwitchingOff
import com.prasoon.airmousetv.airmouse.api.VolumeAction
import com.prasoon.airmousetv.airmouse.api.nextSetupStep
import com.prasoon.airmousetv.airmouse.api.tvLinkKeys
import com.prasoon.airmousetv.airmouse.api.volumeAction
import com.prasoon.airmousetv.data.api.normalizePairingCode
import com.prasoon.airmousetv.data.api.parseIpv4
import com.prasoon.airmousetv.airmouse.model.AirMouseSettings
import com.prasoon.airmousetv.airmouse.model.AirMouseSetupStep
import com.prasoon.airmousetv.airmouse.model.AirMouseUiState
import com.prasoon.airmousetv.data.model.ConnectionState
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.airmouse.model.ScrollWith
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.airmouse.repository.AirMouseSession
import com.prasoon.airmousetv.data.repository.HostProbe
import com.prasoon.airmousetv.data.repository.LastTvStore
import com.prasoon.airmousetv.data.repository.RemoteRepository
import com.prasoon.airmousetv.data.repository.RemoteSessionManager
import com.prasoon.airmousetv.data.repository.TvUnreachableException
import com.prasoon.airmousetv.data.repository.WakeSupportStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RemoteViewModel"

/**
 * How long the Bluetooth link to the TV outlives the app going to the background. A trip to another
 * app and back inside this time finds the pointer already connected; after it the mouse role is
 * given back, since Android may hand it to another app and the link costs battery for nobody.
 */
private const val AIR_LINK_GRACE_MS = 60_000L
/** How long a scan shows as "scanning" before it settles into found / nothing found. */
private const val SCAN_WINDOW_MS = 8000L

/**
 * How often the network is swept again while no TV is listed. An idle Google TV can stop answering
 * multicast discovery while its remote port still answers (seen on a Google TV: it only showed up
 * once YouTube was started on it), so the sweep is what finds it. A sweep takes about 2 s, so this
 * makes the TV appear within seconds without scanning non-stop.
 */
private const val RESWEEP_INTERVAL_MS = 10_000L

/** How long a TV gets to show its pairing code, or accept us, before the attempt is abandoned. */
private const val PAIRING_RESPONSE_TIMEOUT_MS = 15_000L

/** How long a turned-down code stays in its boxes, in red, before they empty for the next try. */
private const val CODE_REJECTED_SHOWN_MS = 1000L

/** How long the "Paired" tick shows before the remote opens: long enough for its animation to finish. */
private const val PAIRED_SHOWN_MS = 900L

/** How often listed TVs are re-checked while discovery is showing. */
private const val LIVENESS_INTERVAL_MS = 5000L
/** Consecutive failed checks before a TV is dropped from the list; one miss can be a dropped packet. */
private const val LIVENESS_MAX_MISSES = 2
/**
 * How long a newly listed TV is exempt from being dropped. A Hisense TV was seen advertising itself
 * about 10 s before its remote port accepted connections, and was dropped 11 s after it appeared.
 */
private const val LIVENESS_GRACE_MS = 30_000L

/**
 * How long the saved TV must stay silent before the list says it isn't on this network. A restart
 * took the Hisense about a minute to rejoin Wi-Fi, so two minutes avoids crying wolf during a normal boot.
 */
private const val OFF_NETWORK_AFTER_MS = 120_000L

/** How many times a dropped session is retried before giving up and returning to discovery. */
private const val MAX_RECONNECT_ATTEMPTS = 4
/** Wait before the first retry; doubles on each attempt. */
private const val RECONNECT_BASE_DELAY_MS = 1000L

/**
 * How long after a Power tap to look at whether the TV stayed on the network: past
 * [STILL_TALKING_AFTER_POWER_MS] plus a ping interval and some margin, so a TV in network standby
 * has pinged by then.
 */
private const val STAYS_ON_NETWORK_CHECK_MS = STILL_TALKING_AFTER_POWER_MS + 10_000L

/** The names this TV's air mouse link is kept under; the remote can know one TV by different ones (see [tvLinkKeys]). */
private val DiscoveredTv.airMouseKeys: List<String> get() = tvLinkKeys(name, friendlyName, host)

/**
 * Drives the whole UI flow: discovery -> connect/pair -> remote.
 * Discovery goes through [RemoteRepository]; the connection itself through [RemoteSessionManager].
 */
@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val repository: RemoteRepository,
    private val remoteSession: RemoteSessionManager,
    private val lastTvStore: LastTvStore,
    private val wakeSupportStore: WakeSupportStore,
    private val airMouse: AirMouseSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()
    /** Collects the repository's TV list into [uiState]; replaced on each discovery restart. */
    private var tvCollectorJob: Job? = null  // Track the collector
    private var scanTimeoutJob: Job? = null
    /** The refresh button's restart-and-wait sequence; cancelled when discovery is stopped. */
    private var refreshJob: Job? = null
    private var livenessJob: Job? = null
    private var pairingWatchJob: Job? = null
    private var discoveryRunning = false

    /** Keeps the TV's focused text field in step with what is typed on the phone. */
    private val textEntry = TextEntryController(
        scope = viewModelScope,
        tv = SessionTvTextPort(repository, remoteSession),
        onError = { message -> _uiState.value = _uiState.value.copy(error = message) }
    )

    /** Retry loop for a dropped session; non-null/active means we are reconnecting. */
    private var reconnectJob: Job? = null

    /** Watches whether the TV stays on the network after a Power tap; see [watchStaysOnNetwork]. */
    private var powerWatchJob: Job? = null

    /** True while the remote shows the air mouse pad. */
    private var airPadShowing = false

    /** True from the moment the user starts the air mouse setup until it is closed or finished. */
    private var airSetupBegun = false

    /** True for [AIR_LINK_GRACE_MS] after the app goes to the background, so a quick trip elsewhere keeps the Bluetooth link. */
    private var inGrace = false

    /** Lets go of the Bluetooth link once the grace period has run out; replaced when the app returns or goes away again. */
    private var graceJob: Job? = null

    /** False while the app is in the background, where Android takes the Bluetooth mouse role away anyway. */
    private var inForeground = false

    init {
        observeConnectionState()
        observeImeField()
        observeAirMouse()
        lastTvStore.load()?.let { connectToLastTv(it) }
    }

    private fun observeImeField() {
        viewModelScope.launch {
            repository.foregroundApp.collect { app ->
                _uiState.value = _uiState.value.copy(foregroundApp = app)
            }
        }
        viewModelScope.launch {
            remoteSession.imeField.collect { field ->
                _uiState.value = _uiState.value.copy(imeField = field)
            }
        }
    }

    /**
     * On launch, silently reopens the session with the TV used last time. This never starts
     * pairing: if the TV is off, has moved or no longer trusts us, the user lands on discovery.
     */
    private fun connectToLastTv(tv: DiscoveredTv) {
        Log.i(TAG, "Reconnecting to last TV: ${tv.displayName}")
        _uiState.value = _uiState.value.copy(mode = RemoteMode.Pairing(tv))
        viewModelScope.launch {
            val ok = tryReconnect(tv)
            // On success the Connected state moves us to the remote screen
            if (!ok && _uiState.value.mode is RemoteMode.Pairing) {
                returnToDiscovery(error = "Couldn't reach ${tv.displayName} at ${tv.host}. Pick it below to reconnect.")
            }
        }
    }

    /** Retries a lost session with growing delays; gives up to discovery after [MAX_RECONNECT_ATTEMPTS]. */
    private fun startReconnect(tv: DiscoveredTv) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReconnecting = true, error = null)
            repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
                delay(RECONNECT_BASE_DELAY_MS shl attempt)
                if (tryReconnect(tv)) {
                    _uiState.value = _uiState.value.copy(isReconnecting = false)
                    return@launch
                }
                Log.w(TAG, "Reconnect attempt ${attempt + 1} failed")
            }
            returnToDiscovery(error = lostConnectionMessage(tv))
        }
    }

    /**
     * Why the session with [tv] couldn't be restored. If it was lost right after a Power tap and the
     * TV is now off the network, the TV leaves the network when switched off: that is remembered,
     * and the user is told to use the TV's own remote.
     */
    private suspend fun lostConnectionMessage(tv: DiscoveredTv): String {
        val afterPowerOff = lostAfterPowerOff(
            now = SystemClock.elapsedRealtime(),
            lastHeardAt = remoteSession.lastHeardAt,
            lastPowerAt = remoteSession.lastPowerAt
        )
        if (!afterPowerOff || remoteSession.isReachable(tv.host)) return "Connection to TV lost"
        remoteSession.note("TV left the network after switching off; it can't be woken from the app")
        wakeSupportStore.setCannotWake(tv.name, true)
        return "${tv.displayName} left the network when it switched off, so the app can't turn it back on. " +
            "Use the TV's own remote. To wake it from the app, turn on network standby in the TV's power settings."
    }

    /**
     * After a Power tap on a TV remembered as leaving the network when off: if the session is
     * still up well afterwards and the TV kept talking, it stays on the network (network standby
     * was turned on, or it was being switched on), so the warning is no longer needed.
     */
    private fun watchStaysOnNetwork(tv: DiscoveredTv) {
        val tappedAt = remoteSession.lastPowerAt ?: return
        powerWatchJob?.cancel()
        powerWatchJob = viewModelScope.launch {
            val dropped = withTimeoutOrNull(STAYS_ON_NETWORK_CHECK_MS) {
                remoteSession.connectionState.first { it != ConnectionState.Connected }
            }
            if (dropped == null && !mayBeSwitchingOff(remoteSession.lastHeardAt, tappedAt)) {
                remoteSession.note("TV stayed on the network after Power; it can be woken from the app")
                wakeSupportStore.setCannotWake(tv.name, false)
                _uiState.value = _uiState.value.copy(powerOffIsFinal = false)
            }
        }
    }

    /** Re-opens the session with [tv] without pairing; false if that fails for any reason. */
    private suspend fun tryReconnect(tv: DiscoveredTv): Boolean =
        try {
            remoteSession.reconnect(tv.host)
        } catch (e: Exception) {
            Log.w(TAG, "Reconnect to ${tv.displayName} failed", e)
            false
        }

    /** Shows the TV list with [error], clearing everything about the TV that was being paired with or used. */
    private fun returnToDiscovery(error: String?) {
        _uiState.value = _uiState.value.copy(
            mode = RemoteMode.Discovery,
            isDiscovering = false,
            isReconnecting = false,
            awaitingCode = false,
            pairingCode = "",
            codeRejected = false,
            paired = false,
            pairedOffer = false,
            error = error,
            airMouse = AirMouseUiState()
        )
        airSetupBegun = false
        airMouse.stop()
    }

    /** Hides the message currently shown; the list or remote underneath is unchanged. */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun startDiscovery() {
        // Not isDiscovering: that flips off when the scan window ends while the listener stays up
        if (discoveryRunning) return
        discoveryRunning = true

        _uiState.value = _uiState.value.copy(isDiscovering = true, error = null)
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            // Sweep at once, alongside multicast. On a busy 2.4/5 GHz router multicast is slow and lossy
            // (it is sent at the lowest Wi-Fi rate), so waiting for it first only delayed the TV by seconds.
            // The sweep takes ~2 s and a TV found by both is listed once (matched by address).
            val firstSweep = launch { scanSubnet() }
            launch { probeSavedTv() }
            delay(SCAN_WINDOW_MS)
            firstSweep.join()
            _uiState.value = _uiState.value.copy(isDiscovering = false)
            // Keep sweeping, quietly, for as long as nothing is listed and the list is showing
            // (stopDiscovery cancels this). A refresh runs its own sweep, so skip while one is going
            while (true) {
                delay(RESWEEP_INTERVAL_MS)
                if (repository.discoveredTvs.value.isEmpty() && !_uiState.value.isRefreshing) {
                    scanSubnet(patient = false)
                    probeSavedTv()
                }
            }
        }

        // Cancel old collector, start new one
        tvCollectorJob?.cancel()
        tvCollectorJob = viewModelScope.launch {
            repository.discoveredTvs.collect { tvs ->
                _uiState.value = _uiState.value.copy(
                    discoveredTvs = tvs,
                    savedTvStarting = _uiState.value.savedTvStarting && tvs.isEmpty(),
                    savedTvOffNetwork = _uiState.value.savedTvOffNetwork && tvs.isEmpty()
                )
                refreshSavedTvAddress(tvs)
            }
        }

        repository.startDiscovery()
        startLivenessChecks()
    }

    /**
     * NSD does not reliably report a TV that was switched off, so while discovery is showing every
     * listed TV is probed periodically and dropped after [LIVENESS_MAX_MISSES] misses in a row.
     */
    private fun startLivenessChecks() {
        livenessJob?.cancel()
        livenessJob = viewModelScope.launch {
            val misses = mutableMapOf<String, Int>()
            val firstSeen = mutableMapOf<String, Long>()
            while (true) {
                delay(LIVENESS_INTERVAL_MS)
                val tvs = repository.discoveredTvs.value
                val now = SystemClock.elapsedRealtime()
                misses.keys.retainAll(tvs.map { it.host }.toSet())
                firstSeen.keys.retainAll(tvs.map { it.host }.toSet())
                tvs.forEach { firstSeen.getOrPut(it.host) { now } }
                val results = coroutineScope {
                    tvs.map { tv -> async { tv to remoteSession.isReachable(tv.host) } }.awaitAll()
                }
                for ((tv, ok) in results) {
                    if (ok) {
                        misses.remove(tv.host)
                        continue
                    }
                    // A TV announces itself before its remote port accepts connections, so a TV just listed,
                    // or one being connected to, is not judged yet
                    if (_uiState.value.isCheckingTv || now - (firstSeen[tv.host] ?: now) < LIVENESS_GRACE_MS) continue
                    val count = (misses[tv.host] ?: 0) + 1
                    if (count >= LIVENESS_MAX_MISSES) {
                        Log.i(TAG, "${tv.displayName} stopped answering, removing from list")
                        misses.remove(tv.host)
                        repository.removeDiscovered(tv.host)
                    } else {
                        misses[tv.host] = count
                    }
                }
            }
        }
    }

    /** If the saved TV shows up in discovery at a new address (DHCP change), keep the saved address current. */
    private fun refreshSavedTvAddress(tvs: List<DiscoveredTv>) {
        val saved = lastTvStore.load() ?: return
        val seen = tvs.firstOrNull { it.name == saved.name } ?: return
        if (seen.host != saved.host) {
            Log.i(TAG, "Saved TV moved ${saved.host} -> ${seen.host}")
            lastTvStore.save(seen)
        }
    }

    fun retryDiscovery() {
        if (_uiState.value.isRefreshing) return
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, isDiscovering = true, error = null)
            try {
                // Force repository clean restart, forgetting TVs found earlier: NSD does not always report a TV
                // that was switched off as lost
                repository.stopDiscovery()
                repository.clearDiscovered()
                delay(800)  // Let NSD fully reset

                repository.startDiscovery()
                // The refresh was asked for, so also sweep the network, alongside the multicast wait
                val sweep = launch { scanSubnet() }
                delay(SCAN_WINDOW_MS)
                sweep.join()
            } finally {
                // Always stop the spinner, whether or not anything was found
                _uiState.value = _uiState.value.copy(isRefreshing = false, isDiscovering = false)
            }
        }
    }

    /** When the saved TV first stopped answering in a row of checks, on the [SystemClock.elapsedRealtime] clock; 0 if it answers. */
    private var savedTvUnreachableSince = 0L

    /** Lists the last-used TV as soon as its saved address answers, without waiting for any discovery. */
    private suspend fun probeSavedTv() {
        val saved = lastTvStore.load() ?: return
        try {
            val probe = remoteSession.probeHost(saved.host)
            if (probe == HostProbe.OPEN) repository.addDiscovered(saved)
            // On the network but not serving yet: say so, rather than leaving the screen looking dead
            val now = SystemClock.elapsedRealtime()
            if (probe == HostProbe.UNREACHABLE) {
                if (savedTvUnreachableSince == 0L) savedTvUnreachableSince = now
            } else {
                savedTvUnreachableSince = 0L
            }
            _uiState.value = _uiState.value.copy(
                savedTvStarting = probe == HostProbe.REFUSED,
                // Silent for a while: not just booting, so it may have joined another network (or band)
                savedTvOffNetwork = savedTvUnreachableSince != 0L && now - savedTvUnreachableSince >= OFF_NETWORK_AFTER_MS
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Saved TV probe failed", e)
        }
    }

    /** Looks for TVs by probing the network directly; a failure only means that nothing more is found. */
    private suspend fun scanSubnet(patient: Boolean = true) {
        try {
            repository.scanSubnet(patient)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Subnet scan failed", e)
        }
    }

    /** Lists the TV at [address], typed by the user, and starts connecting to it. Ignored if it isn't an IPv4 address. */
    fun addTvByAddress(address: String) {
        val host = parseIpv4(address) ?: return
        selectTv(repository.addByAddress(host))
    }

    fun stopDiscovery() {
        discoveryRunning = false
        // A refresh still waiting would otherwise restart discovery after the user has left the list
        refreshJob?.cancel()
        scanTimeoutJob?.cancel()
        livenessJob?.cancel()
        repository.stopDiscovery()
        _uiState.value = _uiState.value.copy(
            isDiscovering = false,
            savedTvStarting = false,
            savedTvOffNetwork = false
        )
        savedTvUnreachableSince = 0L
    }

    /** Translates [ConnectionState] changes from the session into UI state. */
    private fun observeConnectionState() {
        viewModelScope.launch {
            remoteSession.connectionState.collect { state ->
                when (state) {
                    ConnectionState.Connected -> {
                        val tv = (_uiState.value.mode as? RemoteMode.Pairing)?.tv
                        if (tv != null) {
                            lastTvStore.save(tv)
                            openRemote(tv)
                        }
                    }
                    ConnectionState.AwaitingCode -> {
                        _uiState.value = _uiState.value.copy(awaitingCode = true)
                    }
                    is ConnectionState.Error -> when {
                        state.wrongCode -> showCodeRejected()
                        // Any other failure ends the pairing attempt, so the code boxes make way for "try again"
                        _uiState.value.mode is RemoteMode.Pairing -> _uiState.value = _uiState.value.copy(
                            awaitingCode = false, pairingCode = "", codeRejected = false, error = state.message
                        )
                        else -> _uiState.value = _uiState.value.copy(error = state.message)
                    }
                    ConnectionState.Disconnected -> {
                        (_uiState.value.mode as? RemoteMode.Connected)?.let { startReconnect(it.tv) }
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Moves from the pairing screen to the remote for [tv]. After a typed code the pairing screen
     * first confirms it for [PAIRED_SHOWN_MS]; a TV that already trusted us opens the remote at once.
     */
    private fun openRemote(tv: DiscoveredTv) {
        val codeWasTyped = _uiState.value.awaitingCode
        if (codeWasTyped) _uiState.value = _uiState.value.copy(paired = true, error = null)
        // A TV paired for the first time is the moment to say the air mouse exists: once, and unless the
        // phone is known to be unable to do it. (Unknown is offered: a new install can't find out
        // before it has the Bluetooth permission.) The screen then waits for an answer instead of moving on
        val offerAirMouse = codeWasTyped && airMouse.available && airMouse.hasMouseRole != false &&
            !airMouse.offeredAfterPairing && !airMouse.isLinked(tv.airMouseKeys)
        if (offerAirMouse) {
            airMouse.offeredAfterPairing = true
            _uiState.value = _uiState.value.copy(pairedOffer = true)
            return
        }
        viewModelScope.launch {
            if (codeWasTyped) delay(PAIRED_SHOWN_MS)
            showRemote(tv)
        }
    }

    /** Leaves the pairing screen for the remote of [tv], if that pairing screen is still the one showing. */
    private fun showRemote(tv: DiscoveredTv) {
        if (_uiState.value.mode != RemoteMode.Pairing(tv)) return
        _uiState.value = _uiState.value.copy(
            mode = RemoteMode.Connected(tv),
            awaitingCode = false,
            pairingCode = "",
            paired = false,
            pairedOffer = false,
            powerOffIsFinal = wakeSupportStore.cannotWake(tv.name),
            airMouse = AirMouseUiState(
                available = airMouse.available,
                linked = airMouse.isLinked(tv.airMouseKeys),
                hasGyroscope = airMouse.hasGyroscope,
                roleMissing = airMouse.hasMouseRole == false,
                settings = airMouse.settings
            )
        )
        // Finding out asks the user nothing; on a phone where it is known already this returns at once
        airMouse.findOutMouseRole { has -> updateAirMouse { it.copy(roleMissing = !has) } }
        // The session may have dropped while the tick was showing, when no remote was there to notice
        if (remoteSession.connectionState.value == ConnectionState.Disconnected) startReconnect(tv)
    }

    /**
     * Answers the air mouse offer on the Paired screen: opens the remote, and with [setUp] goes
     * straight on into the air mouse setup.
     */
    fun answerPairedOffer(setUp: Boolean) {
        val tv = (_uiState.value.mode as? RemoteMode.Pairing)?.tv ?: return
        if (!_uiState.value.pairedOffer) return
        showRemote(tv)
        if (setUp) openAirMouseSetup()
    }

    /**
     * Shows the typed code as wrong, then empties the boxes: codes are typed fast, so the user
     * shouldn't have to delete six characters before trying again.
     */
    private fun showCodeRejected() {
        _uiState.value = _uiState.value.copy(codeRejected = true, error = "That code didn't match. Try again.")
        viewModelScope.launch {
            delay(CODE_REJECTED_SHOWN_MS)
            if (_uiState.value.codeRejected) {
                _uiState.value = _uiState.value.copy(codeRejected = false, pairingCode = "")
            }
        }
    }


    /**
     * Starts connecting to [tv]. If the TV already trusts this app the remote opens directly;
     * otherwise the TV shows a code and [awaitingCode][RemoteUiState.awaitingCode] turns true.
     */
    fun selectTv(tv: DiscoveredTv) {
        if (_uiState.value.isCheckingTv) return
        Log.i(TAG, "📺 TV selected: ${tv.displayName}")

        _uiState.value = _uiState.value.copy(isCheckingTv = true, error = null)

        viewModelScope.launch {
            // Fail fast, staying on the list, if the TV is off, asleep or has moved
            val reachable = remoteSession.isReachableWithRetry(tv.host)
            if (!reachable) {
                // Not removed here: one failed tap can be a dropped packet between Wi-Fi bands. A TV that is
                // really gone is dropped by the liveness checks, after repeated misses.
                val message = "${tv.displayName} didn't answer. Check it's on and on the same Wi-Fi band, then tap it again."
                _uiState.value = _uiState.value.copy(isCheckingTv = false, error = message)
                // A retry from the pairing screen: show the list again, where the TV can be tapped once more
                if (_uiState.value.mode is RemoteMode.Pairing) returnToDiscovery(error = message)
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isCheckingTv = false,
                mode = RemoteMode.Pairing(tv),
                awaitingCode = false,
                pairingCode = "",
                codeRejected = false,
                paired = false,
                pairedOffer = false
            )
            watchPairingTv(tv)
            try {
                // Reconnects straight away if the TV already trusts us, otherwise starts pairing
                remoteSession.connect(tv.host, "AirMouseTV")
            } catch (e: Exception) {
                if (remoteSession.isPairingPortStuck(tv.host)) {
                    failConnect(tv, stuckPairingMessage(tv), removeTv = false)
                    return@launch
                }
                failConnect(tv, if (e is TvUnreachableException) e.message else "Connection failed: ${e.message}", removeTv = e is TvUnreachableException)
                return@launch
            }

            // Connected, or the TV showing its code, should follow quickly; if not, don't leave a spinner running
            val outcome = withTimeoutOrNull(PAIRING_RESPONSE_TIMEOUT_MS) {
                remoteSession.connectionState.first {
                    it is ConnectionState.AwaitingCode || it is ConnectionState.Connected ||
                        it is ConnectionState.Error || it is ConnectionState.Disconnected
                }
            }
            val stillConnecting = (_uiState.value.mode as? RemoteMode.Pairing)?.tv?.host == tv.host &&
                !_uiState.value.awaitingCode
            if (stillConnecting) {
                when (outcome) {
                    null -> failConnect(
                        tv,
                        if (remoteSession.isPairingPortStuck(tv.host)) stuckPairingMessage(tv)
                        else "${tv.displayName} didn't respond. Make sure it is on, then try again.",
                        removeTv = false
                    )
                    is ConnectionState.Error -> failConnect(tv, outcome.message, removeTv = false)
                    ConnectionState.Disconnected -> failConnect(tv, "Lost the connection to ${tv.displayName}.", removeTv = false)
                    else -> Unit
                }
            }
        }
    }

    /**
     * Ends a connection attempt that can't continue, so no spinner is left running. A TV that is gone
     * ([removeTv]) leaves the list and the list is shown; otherwise the pairing screen stays, showing
     * [message] and offering to try the same TV again.
     */
    private fun failConnect(tv: DiscoveredTv, message: String?, removeTv: Boolean) {
        remoteSession.noteFailure("connect to ${tv.host} failed: ${message ?: "no reason given"}")
        pairingWatchJob?.cancel()
        remoteSession.close()
        if (removeTv) {
            repository.removeDiscovered(tv.host)
            returnToDiscovery(error = message)
        } else {
            _uiState.value = _uiState.value.copy(
                awaitingCode = false, pairingCode = "", codeRejected = false,
                error = message ?: "Couldn't connect to ${tv.displayName}."
            )
        }
    }

    private fun stuckPairingMessage(tv: DiscoveredTv) =
        "${tv.displayName} is still busy with an earlier pairing. Restart the TV, or force-stop Android TV Remote Service in its settings, then try again."

    /** Starts over with the TV the pairing screen is showing, after a failed attempt. */
    fun retryPairing() {
        val tv = (_uiState.value.mode as? RemoteMode.Pairing)?.tv ?: return
        selectTv(tv)
    }

    /**
     * The pairing connection carries no pings (the user may sit on the code screen for minutes), so
     * the silence check used by the remote session can't apply. Probe the TV instead, and return to
     * discovery if it stops answering, e.g. it was switched off while its code was showing.
     */
    private fun watchPairingTv(tv: DiscoveredTv) {
        pairingWatchJob?.cancel()
        pairingWatchJob = viewModelScope.launch {
            var misses = 0
            while ((_uiState.value.mode as? RemoteMode.Pairing)?.tv?.host == tv.host) {
                delay(LIVENESS_INTERVAL_MS)
                if (remoteSession.isReachable(tv.host)) {
                    misses = 0
                } else if (++misses >= LIVENESS_MAX_MISSES) {
                    Log.i(TAG, "${tv.displayName} stopped answering during pairing")
                    remoteSession.close()
                    repository.removeDiscovered(tv.host)
                    returnToDiscovery(error = "${tv.displayName} went offline while pairing. Turn it on and try again.")
                    return@launch
                }
            }
        }
    }

    /**
     * Keeps only what can be part of a pairing code (see [normalizePairingCode]) and clears any shown
     * error. The code is sent as soon as it is complete, so there is no button to press.
     */
    fun updatePairingCode(code: String) {
        val state = _uiState.value
        // The boxes are showing a result (wrong, or paired); keys typed meanwhile would be lost or sent twice
        if (state.codeRejected || state.paired) return
        val normalized = normalizePairingCode(code)
        if (normalized == state.pairingCode) return
        _uiState.value = state.copy(pairingCode = normalized, error = null)
        if (normalized.length == PAIRING_CODE_LENGTH) submitPairingCode()
    }

    /** Sends the typed code to the TV; ignored until all 6 digits are entered. */
    fun submitPairingCode() {
        val code = _uiState.value.pairingCode
        if (code.length != PAIRING_CODE_LENGTH) return

        viewModelScope.launch {
            remoteSession.submitPairingCode(code)
        }
    }


    /** Abandons the connection attempt and returns to discovery. */
    fun cancelPairing() {
        // Back on the Paired screen while it offers the air mouse means "not now", not a way to stay there
        if (_uiState.value.pairedOffer) return answerPairedOffer(setUp = false)
        // Already paired and about to open the remote: there is nothing left to cancel
        if (_uiState.value.paired) return
        reconnectJob?.cancel()
        pairingWatchJob?.cancel()
        remoteSession.close()
        returnToDiscovery(error = null)
    }

    /** Leaves the current TV for good: stops any retry, forgets it so it isn't reconnected on launch, and returns to discovery. */
    fun switchTv() {
        reconnectJob?.cancel()
        lastTvStore.clear()
        // Mode changes first so the Disconnected from close() doesn't start a reconnect
        returnToDiscovery(error = null)
        remoteSession.close()
    }

    /** Recent handshake and text-input events, for the user to copy into a bug report. */
    fun diagnosticsReport(): String = repository.diagnosticsReport()

    /** Starts a text entry session whose field already shows [initial] on the TV. */
    fun beginTextEntry(initial: String) = textEntry.begin(initial)

    /** The TV's text whenever it ends up different from what was typed and cannot be brought back in line. */
    val tvTextAccepted get() = textEntry.acceptedTvText

    /** Makes the TV's focused text field show [text]; edits made in quick succession are merged. */
    fun typeText(text: String) = textEntry.type(text)

    /** Opens YouTube search results for [query] on the TV; failures are surfaced through [RemoteUiState.error]. */
    fun searchYouTube(query: String) {
        viewModelScope.launch {
            try {
                if (!repository.searchYouTube(query)) {
                    _uiState.value = _uiState.value.copy(error = "This TV can't open links")
                }
            } catch (e: Exception) {
                Log.e(TAG, "searchYouTube failed", e)
                _uiState.value = _uiState.value.copy(error = "Send failed: ${e.message}")
            }
        }
    }

    /** Presses Enter on the TV once all text typed so far has been sent. */
    fun submitText() = textEntry.submit()

    /** Sends one key tap; failures are surfaced through [RemoteUiState.error]. */
    fun sendKey(key: TvKey, action: KeyAction = KeyAction.TAP) {
        viewModelScope.launch {
            try {
                val sent = repository.sendKey(key, action)
                val tv = (_uiState.value.mode as? RemoteMode.Connected)?.tv
                if (sent && key == TvKey.POWER && action == KeyAction.TAP && tv != null && _uiState.value.powerOffIsFinal) {
                    watchStaysOnNetwork(tv)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendKey failed", e)
                _uiState.value = _uiState.value.copy(error = "Send failed: ${e.message}")
            }
        }
    }

    // ===== Air mouse =====

    private fun observeAirMouse() {
        airMouse.onNote = remoteSession::note
        // Asked once, quietly, so that by the time a TV is paired the app knows whether to mention the air mouse
        airMouse.findOutMouseRole { }
        // The TV's Bluetooth pairing is gone: the pad makes way for the offer to set it up again
        airMouse.onUnlinked = { updateAirMouse { it.copy(linked = false) } }
        viewModelScope.launch {
            airMouse.state.collect { link ->
                val tv = (_uiState.value.mode as? RemoteMode.Connected)?.tv
                updateAirMouse {
                    it.copy(
                        link = link,
                        // What a real attempt to take the role found out settles the question either way
                        roleMissing = when (link) {
                            PointerLinkState.UNSUPPORTED -> true
                            PointerLinkState.NEEDS_PAIRING, PointerLinkState.READY -> false
                            else -> it.roleMissing
                        },
                        linked = tv != null && airMouse.isLinked(tv.airMouseKeys),
                        setup = it.setup?.let { step -> if (airSetupBegun) nextSetupStep(step, link) else step }
                    )
                }
            }
        }
        viewModelScope.launch {
            airMouse.search.collect { search -> updateAirMouse { it.copy(search = search) } }
        }
        // The link comes up when the remote connects to a TV that has been set up, and goes with it
        viewModelScope.launch {
            _uiState.map { (it.mode as? RemoteMode.Connected)?.tv?.airMouseKeys }.distinctUntilChanged().collect { syncAirMouse() }
        }
    }

    /** Starts pairing with the device at [address], one the setup's search found and the user picked as their TV. */
    fun pairAirMouseWith(address: String) = airMouse.pairWith(address)

    /** Searches for the TV again, once the setup's search has run out and the user has had a chance to restart the TV. */
    fun retryAirMouseSearch() = airMouse.retrySearch()

    private fun updateAirMouse(change: (AirMouseUiState) -> AirMouseUiState) {
        _uiState.value = _uiState.value.copy(airMouse = change(_uiState.value.airMouse))
    }

    /**
     * Holds the Bluetooth mouse role while the app is in front (and for a minute after), once the TV
     * has been set up, or while something on screen needs it: the pad, or a
     * setup that has been started. Without the Bluetooth permission nothing starts and the link
     * stays idle, which the pad shows as "allow Bluetooth".
     *
     * A setup doesn't let go while the app is behind something else, because that something is
     * Android's own pairing or "make visible" dialog. Android takes the role away under those
     * anyway; staying started is what lets the link ask for it again the moment the dialog closes,
     * and then offer the TV the pointer connection it didn't open by itself.
     */
    private fun syncAirMouse() {
        val tv = (_uiState.value.mode as? RemoteMode.Connected)?.tv
        // A TV that has been set up, on a phone that can do it, gets its link as soon as the remote
        // connects, so opening the pad (or coming back to it from the D-pad) doesn't wait for Bluetooth
        val ready = tv != null && airMouse.hasMouseRole == true && airMouse.isLinked(tv.airMouseKeys)
        val visible = inForeground || inGrace
        if (tv != null && (airSetupBegun || (visible && (airPadShowing || ready)))) {
            // The TV goes by the same name over Bluetooth on most models, which is how the search knows it
            // The sensors, which cost the battery, run only while the pad is there to be pointed with
            airMouse.start(
                tv.airMouseKeys, listOfNotNull(tv.friendlyName, tv.displayName, tv.name).distinct(),
                sensing = inForeground && airPadShowing
            )
        } else {
            airMouse.stop()
        }
    }

    /**
     * Tells the air mouse whether the app is in front. The Bluetooth link outlives a short trip to
     * another app, [AIR_LINK_GRACE_MS], and is let go after that: Android may hand the mouse role to
     * another app meanwhile, and nothing is gained by holding it for an app nobody is looking at.
     */
    fun setAirMouseForeground(foreground: Boolean) {
        inForeground = foreground
        graceJob?.cancel()
        inGrace = !foreground
        if (!foreground) {
            graceJob = viewModelScope.launch {
                delay(AIR_LINK_GRACE_MS)
                inGrace = false
                syncAirMouse()
            }
        }
        // Android may have taken the role away while the app was behind; coming back is the time to ask again
        if (foreground && airMouse.state.value == PointerLinkState.BLOCKED) airMouse.stop()
        syncAirMouse()
    }

    /** Tells the air mouse whether its pad is on screen; the pointer is connected only while it is. */
    fun setAirMousePadShowing(showing: Boolean) {
        airPadShowing = showing
        syncAirMouse()
    }

    /** Tries the pointer again after the user has been asked for the Bluetooth permission. */
    fun retryAirMouse() {
        syncAirMouse()
    }

    /** Opens the one-time air mouse setup at its first screen. */
    fun openAirMouseSetup() {
        updateAirMouse { it.copy(setup = AirMouseSetupStep.INTRO) }
    }

    /**
     * Starts the setup proper, once the user has allowed Bluetooth: takes the mouse role, which is
     * also how the app finds out whether this phone has one. The steps then follow the link's state.
     */
    fun beginAirMouseSetup() {
        if (_uiState.value.airMouse.setup == null) return
        airSetupBegun = true
        syncAirMouse()
        updateAirMouse { it.copy(phoneName = airMouse.phoneName) }
    }

    /** The user says the TV is searching for accessories; the phone is being made visible to it. */
    fun awaitTvPairing() {
        if (_uiState.value.airMouse.setup == AirMouseSetupStep.ON_TV) {
            updateAirMouse { it.copy(setup = AirMouseSetupStep.WAITING) }
        }
    }

    /** One step back in the setup, or out of it from its first screen. */
    fun backInAirMouseSetup() {
        when (_uiState.value.airMouse.setup) {
            AirMouseSetupStep.WAITING -> updateAirMouse { it.copy(setup = AirMouseSetupStep.ON_TV) }
            // Finished: there is nothing to go back to, so Back leaves like the button does
            AirMouseSetupStep.DONE -> finishAirMouseSetup()
            else -> closeAirMouseSetup()
        }
    }

    /** Leaves the setup unfinished. */
    fun closeAirMouseSetup() {
        airSetupBegun = false
        updateAirMouse { it.copy(setup = null) }
        syncAirMouse()
    }

    /**
     * Leaves the finished setup. The pointer link is kept as it is: the remote switches to the air
     * mouse pad next, and letting go here would only make the TV reconnect a moment later.
     */
    fun finishAirMouseSetup() {
        airSetupBegun = false
        updateAirMouse { it.copy(setup = null) }
    }

    /** Forgets the TV's Bluetooth pairing and opens the setup again, for a pointer that won't reconnect. */
    fun redoAirMouseSetup() {
        val tv = (_uiState.value.mode as? RemoteMode.Connected)?.tv ?: return
        airMouse.stop()
        airMouse.unlink(tv.airMouseKeys)
        updateAirMouse { it.copy(linked = false, setup = AirMouseSetupStep.INTRO) }
    }

    /** Forgets the TV's Bluetooth pairing from the settings sheet; the air mouse pad makes way for the offer to set it up. */
    fun unlinkAirMouse() {
        val tv = (_uiState.value.mode as? RemoteMode.Connected)?.tv ?: return
        airMouse.stop()
        airMouse.unlink(tv.airMouseKeys)
        updateAirMouse { it.copy(linked = false) }
    }

    /** Changes how the air mouse feels and what the volume buttons do; takes effect at once and is remembered. */
    fun updateAirMouseSettings(settings: AirMouseSettings) {
        airMouse.settings = settings
        updateAirMouse { it.copy(settings = settings) }
    }

    /**
     * Measures the gyroscope's resting drift while the phone lies flat, so the pointer stops
     * creeping by itself. The outcome shows in [AirMouseUiState.calibrated].
     */
    fun calibrateAirMouse() {
        if (_uiState.value.airMouse.calibrating) return
        updateAirMouse { it.copy(calibrating = true, calibrated = null) }
        viewModelScope.launch {
            val taken = airMouse.calibrate()
            updateAirMouse { it.copy(calibrating = false, calibrated = taken) }
        }
    }

    /** Forgets how the last calibration ended, when the settings sheet that showed it closes. */
    fun clearCalibrationResult() {
        updateAirMouse { it.copy(calibrated = null) }
    }

    /**
     * A finger came down on the air mouse pad (true) or left it. While it is down the pointer holds
     * still, so the phone can be re-aimed and a click lands where the user was pointing.
     */
    fun airPress(down: Boolean) = airMouse.press(down)

    /** Brings the pointer back to about the middle of the TV's screen. */
    fun airRecenter() = airMouse.recenter()

    /** What the volume button being held right now was taken for, or null if none is held for the pointer. */
    private var heldVolumeAction: VolumeAction? = null

    /**
     * Takes a press of one of the phone's volume buttons ([volumeUp] for the upper one) for the air
     * mouse, if it is the pointer's to take right now. A click goes down at once and stays down for
     * as long as the button is, so holding it drags. Returns false to leave the button to the TV's
     * volume.
     */
    fun airVolumeKeyDown(volumeUp: Boolean): Boolean {
        val air = _uiState.value.airMouse
        val pointing = airPadShowing && air.link == PointerLinkState.READY && air.hasGyroscope
        val action = volumeAction(volumeUp, air.settings, pointing)
        if (action == VolumeAction.VOLUME) return false
        heldVolumeAction = action
        if (action == VolumeAction.CLICK) {
            // Pushing a side button jolts the phone like a tap does, so the pointer holds still around it
            airMouse.press(true)
            airMouse.button(PointerButton.PRIMARY, true)
        }
        return true
    }

    /**
     * The volume button taken by [airVolumeKeyDown] came back up. Back is sent now, like a key tap
     * on screen, unless the press was [cancelled] by the window losing focus, when nobody meant it.
     */
    fun airVolumeKeyUp(cancelled: Boolean = false) {
        when (heldVolumeAction) {
            VolumeAction.CLICK -> {
                airMouse.button(PointerButton.PRIMARY, false)
                airMouse.press(false)
            }
            // Over Wi-Fi, like the Back key on screen: what a right click does is up to each TV app
            VolumeAction.BACK -> if (!cancelled) sendKey(TvKey.BACK)
            else -> Unit
        }
        heldVolumeAction = null
    }

    /** The finger slid over the air mouse pad by [dxDp], [dyDp] in [dtSeconds], with the pad used as a mouse pad. */
    fun airSlide(dxDp: Float, dyDp: Float, dtSeconds: Float) = airMouse.slide(dxDp, dyDp, dtSeconds)

    /** Presses or releases a button of the TV's pointer. */
    fun airButton(button: PointerButton, down: Boolean) = airMouse.button(button, down)

    private val scrollKeys = ScrollKeys()

    /**
     * Scrolls the TV by [dx] notches sideways (right positive) and [dy] of the wheel (up positive):
     * with D-pad keys by default, which TV apps scroll smoothly, or with the Bluetooth wheel.
     */
    fun airScroll(dx: Float, dy: Float) {
        if (airMouse.settings.scrollWith == ScrollWith.MOUSE_WHEEL) {
            airMouse.scroll(dx, dy)
        } else {
            scrollKeys.add(dx, dy).forEach { sendKey(it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        airMouse.stop()
        tvCollectorJob?.cancel()
        repository.stopDiscovery()
        repository.releaseResources()
    }

}
