package com.prasoon.airmousetv.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import javax.inject.Singleton

private const val TAG = "RemoteRepository"

data class DiscoveredTv(
    val name: String,
    val friendlyName: String? = null,
    val host: String,
    val port: Int,
    val macAddress: String? = null,
    val serviceType: String = ""
) {
    val displayName: String get() = friendlyName ?: run {
        val model = name.split("-").first()
            .replace(Regex("[^A-Z0-9]"), "")
            .take(8)
            .uppercase()
        "$model TV"
    }
}

@Singleton
class RemoteRepository(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val executor = Executors.newSingleThreadExecutor()

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var serviceInfoCallback: NsdManager.ServiceInfoCallback? = null

    private var multicastLock: WifiManager.MulticastLock? = null

    private companion object {
        private val TV_PORTS = listOf(8008, 8009, 7675, 8060, 5353, 80, 8080)
    }

    private val SERVICE_TYPE = "_googlecast._tcp."

    init {
        checkNetwork()
        acquireMulticastLock()
    }

    private fun checkNetwork() {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            Log.d(TAG, "📶 WiFi enabled: ${wifiManager.isWifiEnabled}")

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            val isConnected = networkInfo?.isConnected ?: false
            Log.d(TAG, "🌐 Network connected: $isConnected")
        } catch (e: Exception) {
            Log.e(TAG, "Network check failed", e)
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("airmousetv.nsd")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            Log.d(TAG, "🔒 Multicast lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock", e)
        }
    }

    private val prefs = context.getSharedPreferences("tv_cache", Context.MODE_PRIVATE)
    private fun saveWorkingPort(ip: String, port: Int) {
        prefs.edit { putInt("port_$ip", port) }
        Log.d(TAG, "💾 Cached port $port for $ip")
    }

    private fun getCachedPort(ip: String): Int? {
        val port = prefs.getInt("port_$ip", -1)
        return if (port > 0) port else null
    }

    fun clearPortCache() {
        prefs.edit { clear() }
        Log.i(TAG, "🗑️ Cleared all TV port cache")
    }

    fun startDiscovery() {
        Log.d(TAG, "🔍 Starting TV discovery...")
        if (discoveryListener != null) {
            Log.w(TAG, "⚠️ Discovery already active, ignoring startDiscovery()")
            return
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "✅ Discovery started for: $regType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "⏹️ Discovery stopped: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "📡 Service found: ${serviceInfo.serviceName}/${serviceInfo.serviceType}")

                if (!serviceInfo.serviceType.contains(SERVICE_TYPE)) {
                    Log.d(TAG, "❌ Skipping non-TV service")
                    return
                }

                Log.i(TAG, "🎉 ANDROID TV DETECTED! Resolving: ${serviceInfo.serviceName}")

                if (Build.VERSION.SDK_INT >= 34) {
                    startServiceInfoCallback(serviceInfo)
                } else {
                    startResolveLegacy(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.w(TAG, "🔌 Service lost: ${serviceInfo.serviceName}")
                scope.launch(Dispatchers.Main) {
                    val currentList = _discoveredTvs.value
                    val filtered = currentList.filterNot { it.name == serviceInfo.serviceName }
                    if (filtered.size < currentList.size) {
                        _discoveredTvs.value = filtered
                        Log.d(TAG, "📊 Removed lost service, now ${filtered.size} TVs")
                    }
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "💥 Discovery START failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "💥 Discovery STOP failed: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener!!)
    }

    fun stopDiscovery() {
        Log.d(TAG, "🛑 Stopping discovery...")

        // Stop discovery with full error handling
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
                Log.d(TAG, "✅ Discovery stopped")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "⚠️ stopServiceDiscovery ignored (expected)", e)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ stopServiceDiscovery failed", e)
            }
        }

        // Null ALL listeners immediately
        discoveryListener = null
        serviceInfoCallback?.let {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    nsdManager.unregisterServiceInfoCallback(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ServiceInfoCallback cleanup ignored", e)
            }
            serviceInfoCallback = null
        }
        resolveListener = null  // Safe for all API levels

        Log.d(TAG, "🧹 Discovery fully cleaned")
    }

    fun releaseResources() {
        multicastLock?.release()
        Log.d(TAG, "🔓 Multicast lock RELEASED")
        multicastLock = null
    }

    // ✅ FIXED VERSION - Replace your fetchFriendlyName() function
    // ✅ MODIFIED fetchFriendlyName() - Replace your current one completely
    private suspend fun fetchFriendlyName(host: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Scanning $host on TV ports...")

        // ========================================
        // ✅ STEP 1: TRY CACHED PORT FIRST (⚡ 100ms)
        // ========================================
        getCachedPort(host)?.let { cachedPort ->
            Log.d(TAG, "💾 Using cached port $cachedPort for $host")
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, cachedPort), 1000)

                socket.getOutputStream().use { out ->
                    val request = "GET / HTTP/1.1\r\nHost: $host:$cachedPort\r\nUser-Agent: Android/Remote\r\n\r\n"
                    out.write(request.toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                socket.getInputStream().use { input ->
                    val buffer = ByteArray(2048)
                    val bytes = input.read(buffer)
                    if (bytes > 0) {
                        val response = String(buffer, 0, bytes, Charsets.UTF_8)
                        Log.d(TAG, "📄 Cached port $cachedPort: ${response.take(100)}...")

                        response.findFriendlyName()?.let {
                            socket.close()
                            Log.i(TAG, "✅ Cache HIT: '${it}'")
                            return@withContext it
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.d(TAG, "💥 Cached port $cachedPort failed, full scanning...")
            }
        }

        // ========================================
        // ✅ STEP 2: FULL PORT SCAN (only if cache miss)
        // ========================================
        TV_PORTS.forEach { port ->
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 1500)

                socket.getOutputStream().use { out ->
                    val request = "GET / HTTP/1.1\r\nHost: $host:$port\r\nUser-Agent: Android/Remote\r\n\r\n"
                    out.write(request.toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                socket.getInputStream().use { input ->
                    val buffer = ByteArray(2048)
                    val bytes = input.read(buffer)
                    if (bytes > 0) {
                        val response = String(buffer, 0, bytes, Charsets.UTF_8)
                        Log.d(TAG, "📄 Port $port response: ${response.take(200)}...")

                        response.findFriendlyName()?.let {
                            saveWorkingPort(host, port)  // ✅ CACHE THE WINNER!
                            socket.close()
                            Log.i(TAG, "✅ Found '${it}' on port $port! CACHED.")
                            return@withContext it
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.d(TAG, "Port $port failed: ${e.message}")
            }
        }

        Log.d(TAG, "❌ No friendly name found via HTTP")
        null
    }

    private fun String.findFriendlyName(): String? = try {
        // JSON patterns
        "\"friendlyName\"\\s*:.*?\"([^\"]+)\"".toRegex()
            .find(this)?.groupValues?.getOrNull(1)
            ?: "\"deviceName\"\\s*:.*?\"([^\"]+)\"".toRegex()
                .find(this)?.groupValues?.getOrNull(1)
            ?: "\"name\"\\s*:.*?\"([^\"]+)\"".toRegex()
                .find(this)?.groupValues?.getOrNull(1)

            // XML patterns
            ?: "<friendlyName[^>]*>([^<]+)</friendlyName>".toRegex(RegexOption.IGNORE_CASE)
                .find(this)?.groupValues?.getOrNull(1)
            ?: "<deviceName[^>]*>([^<]+)</deviceName>".toRegex(RegexOption.IGNORE_CASE)
                .find(this)?.groupValues?.getOrNull(1)

            // Header pattern
            ?: "FRIENDLY\\s*:\\s*([\\w\\s\\-\\+\\.]+)".toRegex(RegexOption.IGNORE_CASE)
                .find(this)?.groupValues?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.length > 2 && it != "unknown" }

    } catch (e: Exception) {
        Log.e(TAG, "Regex failed", e)
        null
    }


    private suspend fun updateTvWithFriendlyName(tv: DiscoveredTv): DiscoveredTv {
        // Quick fallback for instant UI
        val fallbackName = tv.name.split("-").first().replace(Regex("[^A-Z0-9]"), "").take(8).uppercase() + " TV"
        updateDiscoveredList(tv.copy(friendlyName = fallbackName))

        // Try HTTP scan in background
        val friendlyName = fetchFriendlyName(tv.host)
        if (friendlyName != null && friendlyName != fallbackName) {
            Log.i(TAG, "✅ HTTP found real name: $friendlyName")
            updateDiscoveredList(tv.copy(friendlyName = friendlyName))
        } else {
            Log.i(TAG, "🔄 Keeping fallback: $fallbackName")
        }
        return tv.copy(friendlyName = friendlyName ?: fallbackName)
    }

    // ✅ SOLUTION 2: Updated API 34+ callback with TXT record parsing
    private fun startServiceInfoCallback(discovered: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT < 34) return

        val baseInfo = NsdServiceInfo().apply {
            serviceName = discovered.serviceName
            serviceType = discovered.serviceType
        }

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val port = info.port.takeIf { it > 0 } ?: return

                // 🔍 LOG ALL TXT RECORDS FIRST
                Log.d(TAG, "📋 TXT Records: ${info.attributes.map {
                    "${it.key}=${it.value?.toString(Charsets.UTF_8)?.take(50)}"
                }}")

                // ✅ Extract friendly name from TXT records (FASTEST)
                val friendlyNameFromTxt = info.attributes["fn"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["friendlyName"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["name"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["deviceName"]?.toString(Charsets.UTF_8)

                if (!friendlyNameFromTxt.isNullOrBlank()) {
                    Log.i(TAG, "🎉 TXT friendlyName: $friendlyNameFromTxt")
                    val tv = DiscoveredTv(
                        name = info.serviceName,
                        friendlyName = friendlyNameFromTxt,
                        host = host,
                        port = port
                    )
                    scope.launch { updateDiscoveredList(tv) }
                    return
                }

                // Fallback to HTTP scan
                Log.d(TAG, "🔍 No TXT name, trying HTTP scan...")
                val tv = DiscoveredTv(name = info.serviceName, host = host, port = port)
                scope.launch { updateTvWithFriendlyName(tv) }
            }

            override fun onServiceLost() {}
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG, "ServiceInfoCallback registration failed: $errorCode")
            }
            override fun onServiceInfoCallbackUnregistered() {}
        }

        serviceInfoCallback = callback
        nsdManager.registerServiceInfoCallback(baseInfo, executor, callback)
    }

    // ✅ SOLUTION 2: Updated Legacy callback with TXT record parsing
    private fun startResolveLegacy(discovered: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= 34) return

        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val port = info.port.takeIf { it > 0 } ?: return

                // 🔍 LOG ALL TXT RECORDS FIRST
                Log.d(TAG, "📋 TXT Records: ${info.attributes.map {
                    "${it.key}=${it.value?.toString(Charsets.UTF_8)?.take(50)}"
                }}")

                // ✅ Extract friendly name from TXT records (FASTEST)
                val friendlyNameFromTxt = info.attributes["fn"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["friendlyName"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["name"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["deviceName"]?.toString(Charsets.UTF_8)

                if (!friendlyNameFromTxt.isNullOrBlank()) {
                    Log.i(TAG, "🎉 TXT friendlyName: $friendlyNameFromTxt")
                    val tv = DiscoveredTv(
                        name = info.serviceName,
                        friendlyName = friendlyNameFromTxt,
                        host = host,
                        port = port
                    )
                    scope.launch { updateDiscoveredList(tv) }
                    return
                }

                // Fallback to HTTP scan
                Log.d(TAG, "🔍 No TXT name, trying HTTP scan...")
                val tv = DiscoveredTv(name = info.serviceName, host = host, port = port)
                scope.launch { updateTvWithFriendlyName(tv) }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "💥 Resolve failed: $errorCode for ${serviceInfo.serviceName}")
            }
            override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {}
            override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        resolveListener = listener
        nsdManager.resolveService(discovered, listener)
    }

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
}
