package com.prasoon.airmousetv.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prasoon.airmousetv.data.model.ConnectionState
import com.prasoon.airmousetv.data.api.mismatchSize
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.reportedMismatch
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.data.repository.LastTvStore
import com.prasoon.airmousetv.data.repository.RemoteRepository
import com.prasoon.airmousetv.data.repository.RemoteSessionManager
import com.prasoon.airmousetv.data.repository.TvUnreachableException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RemoteViewModel"
/** How long a scan shows as "scanning" before it settles into found / nothing found. */
private const val SCAN_WINDOW_MS = 8000L

/** How long a TV gets to show its pairing code, or accept us, before the attempt is abandoned. */
private const val PAIRING_RESPONSE_TIMEOUT_MS = 15_000L

/** How often listed TVs are re-checked while discovery is showing. */
private const val LIVENESS_INTERVAL_MS = 5000L
/** Consecutive failed checks before a TV is dropped from the list; one miss can be a dropped packet. */
private const val LIVENESS_MAX_MISSES = 2

/** How long to wait for the TV to report its text after an edit; it normally does within a few hundred ms. */
private const val TV_REPORT_TIMEOUT_MS = 1000L
/** Corrective edits in a row before the TV's text is accepted as it is. */
private const val MAX_TEXT_CORRECTIONS = 4
/** How long typing must pause before the TV's text is checked once more for changes it made by itself. */
private const val TEXT_SETTLE_DELAY_MS = 1500L

/** How many times a dropped session is retried before giving up and returning to discovery. */
private const val MAX_RECONNECT_ATTEMPTS = 4
/** Wait before the first retry; doubles on each attempt. */
private const val RECONNECT_BASE_DELAY_MS = 1000L

/**
 * Drives the whole UI flow: discovery -> connect/pair -> remote.
 * Discovery goes through [RemoteRepository]; the connection itself through [RemoteSessionManager].
 */
@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val repository: RemoteRepository,
    private val remoteSession: RemoteSessionManager,
    private val lastTvStore: LastTvStore
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

    /** Text entry actions waiting to be applied by [launchTextWorker]. */
    private val textActions = Channel<TextAction>(Channel.UNLIMITED)

    /** The text the user wants on the TV right now, the newest one given to [typeText] or [beginTextEntry]. */
    private var wantedText = ""

    /** Retry loop for a dropped session; non-null/active means we are reconnecting. */
    private var reconnectJob: Job? = null

    init {
        observeConnectionState()
        observeImeField()
        launchTextWorker()
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
            val ok = try {
                remoteSession.reconnect(tv.host)
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect to last TV failed", e)
                false
            }
            // On success the Connected state moves us to the remote screen
            if (!ok && _uiState.value.mode is RemoteMode.Pairing) {
                _uiState.value = _uiState.value.copy(
                    mode = RemoteMode.Discovery,
                    error = "Couldn't reach ${tv.displayName} at ${tv.host}. Pick it below to reconnect."
                )
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
                val ok = try {
                    remoteSession.reconnect(tv.host)
                } catch (e: Exception) {
                    false
                }
                if (ok) {
                    _uiState.value = _uiState.value.copy(isReconnecting = false)
                    return@launch
                }
                Log.w(TAG, "Reconnect attempt ${attempt + 1} failed")
            }
            _uiState.value = _uiState.value.copy(
                mode = RemoteMode.Discovery,
                isReconnecting = false,
                isDiscovering = false,
                error = "Connection to TV lost"
            )
        }
    }

    fun startDiscovery() {
        // Not isDiscovering: that flips off when the scan window ends while the listener stays up
        if (discoveryRunning) return
        discoveryRunning = true

        _uiState.value = _uiState.value.copy(isDiscovering = true, error = null)
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_WINDOW_MS)
            _uiState.value = _uiState.value.copy(isDiscovering = false)
        }

        // Cancel old collector, start new one
        tvCollectorJob?.cancel()
        tvCollectorJob = viewModelScope.launch {
            repository.discoveredTvs.collect { tvs ->
                _uiState.value = _uiState.value.copy(discoveredTvs = tvs)
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
            while (true) {
                delay(LIVENESS_INTERVAL_MS)
                val tvs = repository.discoveredTvs.value
                misses.keys.retainAll(tvs.map { it.host }.toSet())
                val results = coroutineScope {
                    tvs.map { tv -> async { tv to remoteSession.isReachable(tv.host) } }.awaitAll()
                }
                for ((tv, ok) in results) {
                    if (ok) {
                        misses.remove(tv.host)
                        continue
                    }
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
                delay(SCAN_WINDOW_MS)

                if (repository.discoveredTvs.value.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "No TVs found. Try again or check Wi-Fi.")
                }
            } finally {
                // Always stop the spinner, whether or not anything was found
                _uiState.value = _uiState.value.copy(isRefreshing = false, isDiscovering = false)
            }
        }
    }

    fun stopDiscovery() {
        discoveryRunning = false
        // A refresh still waiting would otherwise restart discovery after the user has left the list
        refreshJob?.cancel()
        scanTimeoutJob?.cancel()
        livenessJob?.cancel()
        repository.stopDiscovery()
        _uiState.value = _uiState.value.copy(
            isDiscovering = false
        )
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
                            _uiState.value = _uiState.value.copy(
                                mode = RemoteMode.Connected(tv)
                            )
                        }
                    }
                    ConnectionState.AwaitingCode -> {
                        _uiState.value = _uiState.value.copy(awaitingCode = true)
                    }
                    is ConnectionState.Error -> {
                        _uiState.value = _uiState.value.copy(error = state.message)
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
     * Starts connecting to [tv]. If the TV already trusts this app the remote opens directly;
     * otherwise the TV shows a code and [awaitingCode][RemoteUiState.awaitingCode] turns true.
     */
    fun selectTv(tv: DiscoveredTv) {
        if (_uiState.value.isCheckingTv) return
        Log.i(TAG, "📺 TV selected: ${tv.displayName}")

        _uiState.value = _uiState.value.copy(isCheckingTv = true, error = null)

        viewModelScope.launch {
            // Fail fast, staying on the list, if the TV is off, asleep or has moved
            val reachable = remoteSession.isReachable(tv.host)
            if (!reachable) {
                repository.removeDiscovered(tv.host)
                _uiState.value = _uiState.value.copy(
                    isCheckingTv = false,
                    error = "${tv.displayName} isn't responding at ${tv.host}, so it was removed from the list. " +
                        "Turn it on and tap refresh."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isCheckingTv = false,
                mode = RemoteMode.Pairing(tv),
                awaitingCode = false,
                pairingCode = ""
            )
            watchPairingTv(tv)
            try {
                // Reconnects straight away if the TV already trusts us, otherwise starts pairing
                remoteSession.connect(tv.host, "AirMouseTV")
            } catch (e: Exception) {
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
                    null -> failConnect(tv, "${tv.displayName} didn't respond. Make sure it is on, then try again.", removeTv = false)
                    is ConnectionState.Error -> failConnect(tv, outcome.message, removeTv = false)
                    ConnectionState.Disconnected -> failConnect(tv, "Lost the connection to ${tv.displayName}.", removeTv = false)
                    else -> Unit
                }
            }
        }
    }

    /** Ends a connection attempt that can't continue: back to the list with [message] shown, so no spinner is left running. */
    private fun failConnect(tv: DiscoveredTv, message: String?, removeTv: Boolean) {
        pairingWatchJob?.cancel()
        remoteSession.close()
        if (removeTv) repository.removeDiscovered(tv.host)
        _uiState.value = _uiState.value.copy(
            mode = RemoteMode.Discovery,
            isDiscovering = false,
            awaitingCode = false,
            pairingCode = "",
            error = message
        )
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
                    _uiState.value = _uiState.value.copy(
                        mode = RemoteMode.Discovery,
                        isDiscovering = false,
                        awaitingCode = false,
                        pairingCode = "",
                        error = "${tv.displayName} went offline while pairing. Turn it on and try again."
                    )
                    return@launch
                }
            }
        }
    }

    /** Keeps only hex digits, upper-cased and capped at 6, and clears any shown error. */
    fun updatePairingCode(code: String) {
        // Pairing codes are 6 hex digits
        val hex = code.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
        _uiState.value = _uiState.value.copy(pairingCode = hex, error = null)
    }

    /** Sends the typed code to the TV; ignored until all 6 digits are entered. */
    fun submitPairingCode() {
        val code = _uiState.value.pairingCode
        if (code.length != 6) return  // 6-digit hex code

        viewModelScope.launch {
            remoteSession.submitPairingCode(code)
        }
    }


    /** Abandons the connection attempt and returns to discovery. */
    fun cancelPairing() {
        reconnectJob?.cancel()
        pairingWatchJob?.cancel()
        remoteSession.close()
        _uiState.value = _uiState.value.copy(
            mode = RemoteMode.Discovery,
            isDiscovering = false,
            awaitingCode = false,
            pairingCode = "",
            error = null
        )
    }

    /** Leaves the current TV for good: stops any retry, forgets it so it isn't reconnected on launch, and returns to discovery. */
    fun switchTv() {
        reconnectJob?.cancel()
        lastTvStore.clear()
        // Mode changes first so the Disconnected from close() doesn't start a reconnect
        _uiState.value = _uiState.value.copy(
            mode = RemoteMode.Discovery,
            isDiscovering = false,
            isReconnecting = false,
            awaitingCode = false,
            pairingCode = "",
            error = null
        )
        remoteSession.close()
    }

    /** Recent handshake and text-input events, for the user to copy into a bug report. */
    fun diagnosticsReport(): String = repository.diagnosticsReport()

    /** Starts a text entry session whose field already shows [initial] on the TV. */
    fun beginTextEntry(initial: String) {
        wantedText = initial
        textActions.trySend(TextAction.Begin(initial))
    }

    /** Makes the TV's focused text field show [text]; edits made in quick succession are merged. */
    fun typeText(text: String) {
        wantedText = text
        textActions.trySend(TextAction.Set(text))
    }

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
    fun submitText() {
        textActions.trySend(TextAction.Enter)
    }

    /**
     * Applies queued text actions one at a time, in order. Consecutive [TextAction.Set]s are
     * merged into the latest one: swipe typing makes the phone keyboard rewrite the word many
     * times a second, and only the final text matters, so intermediate states are never sent. The
     * merging happens after waiting for the repository's edit slot, so slow sends merge more.
     * Characters the remote can't type are reported through [RemoteUiState.error].
     *
     * The TV doesn't always apply an edit as planned, and it reports its field's text back after
     * each one. So after an edit through the text input session the worker compares that report
     * with what it meant to send. If they differ, the TV's own text becomes the baseline for the
     * next edit and the latest wanted text is sent again, up to [MAX_TEXT_CORRECTIONS] times in a
     * row, so a TV that rewrites the text (or can never match it) doesn't cause an endless loop.
     * The TV can also change its text on its own a moment later, so once typing has paused for
     * [TEXT_SETTLE_DELAY_MS] the worker checks the TV's latest report once more.
     */
    private fun launchTextWorker() {
        viewModelScope.launch {
            var tvText = ""
            var corrections = 0
            var lastGap = 0  // how far the TV's text was from the wanted text before the last correction
            var settleCheckDue = false
            var settleFocuses = 0  // how many fields had taken focus when the last edit was made
            var carried: TextAction? = null

            /** The TV shows [actual] instead of what was sent: trust it as the baseline and, if it isn't what the user wants, send that again. */
            fun onTvTextDiffers(actual: String) {
                Log.w(TAG, "TV text differs from what was sent: ${actual.length} chars, expected ${tvText.length}")
                remoteSession.note("TV text differs (${actual.length} chars, expected ${tvText.length})")
                tvText = actual
                // A correction must bring the TV closer to the wanted text; if it didn't, the TV isn't
                // taking the edits and more of them would only pile more text on it
                val gap = mismatchSize(actual, wantedText)
                val improving = corrections == 0 || gap < lastGap
                lastGap = gap
                if (improving && corrections++ < MAX_TEXT_CORRECTIONS && gap > 0) {
                    textActions.trySend(TextAction.Set(wantedText))
                } else {
                    corrections = 0
                }
            }

            while (true) {
                val first = carried
                    ?: if (settleCheckDue) withTimeoutOrNull(TEXT_SETTLE_DELAY_MS) { textActions.receive() } else textActions.receive()
                if (first == null) {
                    // Typing paused: has the TV changed its text since the last edit, without being asked?
                    // Not if another field has taken focus since, whose text has nothing to do with ours.
                    settleCheckDue = false
                    val reported = remoteSession.imeField.value
                    when {
                        !remoteSession.hasTextSession || reported == null ->
                            Log.d(TAG, "Settle check skipped: no text session")
                        reported.focuses != settleFocuses ->
                            Log.d(TAG, "Settle check skipped: another field took focus")
                        reported.value != tvText -> onTvTextDiffers(reported.value)
                    }
                    continue
                }
                var action: TextAction = first
                carried = null
                if (action is TextAction.Set) {
                    // Edits can't be sent faster than the TV copes with, so let more pile up first
                    repository.awaitEditSlot()
                    while (true) {
                        val next = textActions.tryReceive().getOrNull() ?: break
                        if (next is TextAction.Set) action = next else { carried = next; break }
                    }
                }
                try {
                    when (action) {
                        is TextAction.Begin -> {
                            tvText = action.initial
                            corrections = 0
                            settleCheckDue = false
                        }
                        is TextAction.Set -> {
                            val reportsBefore = remoteSession.imeField.value?.reports ?: 0
                            val skipped = repository.typeEdit(tvText, action.text)
                            tvText = action.text
                            if (skipped.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(error = "Can't type: ${skipped.distinct().joinToString(" ")}")
                            }
                            if (remoteSession.hasTextSession) {
                                settleCheckDue = true
                                settleFocuses = remoteSession.imeField.value?.focuses ?: 0
                                val actual = awaitReportedMismatch(action.text, reportsBefore)
                                if (actual == null) corrections = 0 else onTvTextDiffers(actual)
                            }
                        }
                        TextAction.Enter -> {
                            settleCheckDue = false  // the TV moves on after Enter; its next text isn't ours to correct
                            repository.sendKey(TvKey.ENTER)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e  // the ViewModel is going away; not a send failure
                } catch (e: Exception) {
                    Log.e(TAG, "text action failed", e)
                    _uiState.value = _uiState.value.copy(error = "Send failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Waits briefly for the TV to report its field's text as [expected] after an edit. Returns the
     * text it reports instead if that differs, or null if it matches or the TV reported nothing.
     */
    private suspend fun awaitReportedMismatch(expected: String, reportsBefore: Int): String? {
        withTimeoutOrNull(TV_REPORT_TIMEOUT_MS) { remoteSession.imeField.first { it?.value == expected } }
        return remoteSession.imeField.value.reportedMismatch(expected, reportsBefore)
    }

    /** Sends one key tap; failures are surfaced through [RemoteUiState.error]. */
    fun sendKey(key: TvKey, action: KeyAction = KeyAction.TAP) {
        viewModelScope.launch {
            try {
                repository.sendKey(key, action)
            } catch (e: Exception) {
                Log.e(TAG, "sendKey failed", e)
                _uiState.value = _uiState.value.copy(error = "Send failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tvCollectorJob?.cancel()
        repository.stopDiscovery()
        repository.releaseResources()
    }

}

/** Something to do to the TV's text field, applied in order by the view model. */
private sealed interface TextAction {
    /** A new text entry session; the TV's field currently shows [initial]. */
    data class Begin(val initial: String) : TextAction
    /** The field should show [text]. */
    data class Set(val text: String) : TextAction
    /** Press Enter. */
    object Enter : TextAction
}
