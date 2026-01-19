package com.prasoon.airmousetv.data.repository

import android.util.Log
import com.prasoon.airmousetv.data.model.DiscoveredTv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteRepository"
@Singleton
class RemoteRepository @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val nsdDiscoveryEngine: NsdDiscoveryEngine,
    private val tvPortScanner: TvPortScanner,
    private val tvCacheManager: TvCacheManager
) : NsdDiscoveryListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs

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
            // ✅ FIXED: Check network synchronously (no suspend Flow.first())
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

    fun clearPortCache() {
        tvCacheManager.clearAll()
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
            // Quick fallback for instant UI (your original logic)
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

    // Your original updateDiscoveredList with service lost handling
    private fun updateDiscoveredList(updatedTv: DiscoveredTv) {
        val list = _discoveredTvs.value.toMutableList()
        val idx = list.indexOfFirst { it.name == updatedTv.name }
        if (idx >= 0) {
            list[idx] = updatedTv
        } else {
            list.add(updatedTv)
        }
        _discoveredTvs.value = list.distinctBy { it.name }
        Log.i(TAG, "📊 TVs: ${_discoveredTvs.value.size} - ${updatedTv.displayName}")
    }

    // Handle service lost (your original logic)
    fun onServiceLost(serviceName: String) {
        val currentList = _discoveredTvs.value
        val filtered = currentList.filterNot { it.name == serviceName }
        if (filtered.size < currentList.size) {
            _discoveredTvs.value = filtered
            Log.d(TAG, "📊 Removed lost service, now ${filtered.size} TVs")
        }
    }
}
