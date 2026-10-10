package com.prasoon.airmousetv.data.repository

import android.os.SystemClock
import android.util.Log
import com.prasoon.airmousetv.data.api.KeyPayloadFactory
import com.prasoon.airmousetv.data.api.TextKeyMapper
import com.prasoon.airmousetv.proto.remote.RemoteKeyCode
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.data.model.TvKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteRepository"
/**
 * Minimum time between two edits to the TV's text field. Measured on the TV: the word being
 * typed is only replaced reliably when the previous edit was more than about 350 ms earlier;
 * edits closer together are applied unpredictably (the word is often appended instead).
 */
private const val EDIT_GAP_MS = 450L

/**
 * Single entry point for the data layer: TV discovery (NSD, with an HTTP name lookup fallback)
 * and key sending through the [RemoteSessionManager].
 */
@Singleton
class RemoteRepository @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val nsdDiscoveryEngine: NsdDiscoveryEngine,
    private val tvPortScanner: TvPortScanner,
    private val tvCacheManager: TvCacheManager,
    private val session: RemoteSessionManager
) : NsdDiscoveryListener {

    /** Background scope for discovery work and TV name lookups. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** TVs found so far, de-duplicated by service name. */
    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs

    /** The in-flight startDiscovery() launch; non-null/active means discovery is running. */
    private var discoveryJob: Job? = null

    init {
        nsdDiscoveryEngine.registerListener(this)
    }

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) {
            Log.w(TAG, "⚠️ Discovery already active, ignoring startDiscovery()")
            return
        }

        discoveryJob = scope.launch {
            // Read the latest value directly rather than suspending on the flow
            if (!networkMonitor.isNetworkAvailable.value) {
                Log.d(TAG, "🌐 Network not connected, waiting...")
                return@launch
            }

            nsdDiscoveryEngine.startDiscovery()
        }
    }

    fun stopDiscovery() {
        Log.d(TAG, "🛑 Stopping discovery...")
        discoveryJob?.cancel()
        discoveryJob = null
        nsdDiscoveryEngine.stopDiscovery()
        Log.d(TAG, "🧹 Discovery fully cleaned")
    }

    fun releaseResources() {
        nsdDiscoveryEngine.releaseResources()
    }

    // ===== NsdDiscoveryListener implementation =====
    override fun onTvDiscovered(tv: DiscoveredTv) {
        Log.i("RemoteRepository", "📊 TV discovered: ${tv.displayName}")
        updateDiscoveredList(tv)
    }

    override fun onHttpScanNeeded(tv: DiscoveredTv) {
        scope.launch {
            // Show a name derived from the service name straight away; the HTTP lookup below may replace it
            val fallbackName = tv.name.split("-").first()
                .replace(Regex("[^A-Z0-9]"), "").take(8).uppercase() + " TV"
            updateDiscoveredList(tv.copy(friendlyName = fallbackName))

            // Try HTTP scan
            val friendlyName = tvPortScanner.fetchFriendlyName(tv.host)
            val finalTv = if (friendlyName != null && friendlyName != fallbackName) {
                Log.i("RemoteRepository", "✅ HTTP found real name: $friendlyName")
                tv.copy(friendlyName = friendlyName)
            } else {
                Log.i("RemoteRepository", "🔄 Keeping fallback: $fallbackName")
                tv.copy(friendlyName = fallbackName)
            }
            updateDiscoveredList(finalTv)
        }
    }

    /** Adds [updatedTv], or replaces the entry already listed for the same TV. */
    private fun updateDiscoveredList(updatedTv: DiscoveredTv) {
        // Atomic: this runs on the IO scope while onServiceLost runs on NSD's thread
        _discoveredTvs.update { current ->
            val list = current.toMutableList()
            // The same TV can be advertised under several service types; match by name or address
            val idx = list.indexOfFirst { it.name == updatedTv.name || it.host == updatedTv.host }
            if (idx >= 0) {
                list[idx] = updatedTv
            } else {
                list.add(updatedTv)
            }
            list.distinctBy { it.host }
        }
        Log.i(TAG, "📊 TVs: ${_discoveredTvs.value.size} - ${updatedTv.displayName}")
    }

    /** Forget every TV found so far, e.g. before a rescan, so a TV that has since been switched off drops out. */
    fun clearDiscovered() {
        _discoveredTvs.value = emptyList()
    }

    /** Drops the TV at [host] from the list, e.g. after it stopped answering. */
    fun removeDiscovered(host: String) {
        _discoveredTvs.value = _discoveredTvs.value.filterNot { it.host == host }
    }

    /** Drops the TV whose remote service stopped being advertised, matched by [host] when known, else by [serviceName]. */
    override fun onServiceLost(serviceName: String, host: String?) {
        _discoveredTvs.update { list ->
            list.filterNot { it.name == serviceName || (host != null && it.host == host) }
        }
        Log.d(TAG, "📊 Service lost: $serviceName, now ${_discoveredTvs.value.size} TVs")
    }


    /** Sends one key tap to the TV over the active remote session. */
    suspend fun sendKey(key: TvKey, action: KeyAction = KeyAction.TAP) {
        session.send(KeyPayloadFactory.keyPress(key, action))
    }

    /** Serialises typing so edits made in quick succession reach the TV in order. */
    private val typingLock = Mutex()

    /** When the last text edit was sent, on the [SystemClock.elapsedRealtime] clock. */
    private var lastEditAt = 0L

    /** Waits until at least [EDIT_GAP_MS] have passed since the last text edit. */
    suspend fun awaitEditSlot() {
        val wait = lastEditAt + EDIT_GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
    }

    /**
     * Makes the TV's focused text field go from [previous] to [current], assuming the TV's cursor
     * is at the end.
     *
     * With a text input session open the edit goes through it (see [typeAsText]). Otherwise the
     * difference is typed as key presses: backspaces over what changed at the end, then the new
     * characters. Returns the characters that couldn't be typed as key presses and were skipped.
     */
    suspend fun typeEdit(previous: String, current: String): List<Char> =
        typingLock.withLock {
            if (session.hasTextSession) {
                typeAsText(previous, current)
                return@withLock emptyList()
            }

            val common = previous.commonPrefixWith(current).length
            repeat(previous.length - common) {
                session.send(KeyPayloadFactory.keyCode(RemoteKeyCode.KEYCODE_DEL))
            }

            val skipped = mutableListOf<Char>()
            for (c in current.substring(common)) {
                val stroke = TextKeyMapper.toStroke(c)
                if (stroke == null) {
                    skipped += c
                    continue
                }
                if (stroke.shift) session.send(KeyPayloadFactory.keyCode(RemoteKeyCode.KEYCODE_SHIFT_LEFT, KeyAction.LONG_START))
                session.send(KeyPayloadFactory.keyCode(stroke.code))
                if (stroke.shift) session.send(KeyPayloadFactory.keyCode(RemoteKeyCode.KEYCODE_SHIFT_LEFT, KeyAction.LONG_END))
            }
            skipped
        }

    /**
     * Sends an edit through the TV's text input session. Observed on the TV: a text edit replaces
     * the word at the cursor (the text after the last whitespace) and keeps what is before it,
     * and a delete edit removes characters before the cursor.
     *
     * So whatever differs at the end of [previous] is deleted first, then, if [current] has new
     * characters, the word at the cursor plus those characters is sent so it replaces that word:
     * "why" then "why " then "why h" go out as "why", "why " and "h".
     */
    private suspend fun typeAsText(previous: String, current: String) {
        val common = previous.commonPrefixWith(current).length
        val deleted = previous.length - common
        if (deleted > 0) {
            session.deleteText(deleted)
            lastEditAt = SystemClock.elapsedRealtime()
        }
        if (current.length > common) {
            if (deleted > 0) awaitEditSlot()
            session.sendText(current.substring(wordStart(current.substring(0, common))))
            lastEditAt = SystemClock.elapsedRealtime()
        }
    }

    /** Index where the last whitespace-delimited word of [text] starts. */
    private fun wordStart(text: String): Int = text.indexOfLast { it.isWhitespace() } + 1
}
