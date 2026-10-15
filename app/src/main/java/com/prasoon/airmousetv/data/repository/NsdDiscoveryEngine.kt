package com.prasoon.airmousetv.data.repository

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.prasoon.airmousetv.data.model.DiscoveredTv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_NSD = "NsdDiscoveryEngine"
private const val SERVICE_TYPE_CAST = "_googlecast._tcp."
// Advertised by the TV's remote service itself (port 6466), so it is present whenever the remote works
private const val SERVICE_TYPE_REMOTE = "_androidtvremote2._tcp."
private val SERVICE_TYPES = listOf(SERVICE_TYPE_REMOTE, SERVICE_TYPE_CAST)
/** TXT record keys that may hold the TV's display name, in order of preference. */
private val NAME_ATTRIBUTES = listOf("fn", "friendlyName", "name", "deviceName")

/**
 * Callback interface for discovery events - RemoteRepository implements this
 */
interface NsdDiscoveryListener {
    fun onTvDiscovered(tv: DiscoveredTv)
    fun onHttpScanNeeded(tv: DiscoveredTv)

    /** The TV's remote service stopped being advertised; [host] is where it was resolved, if it got that far. */
    fun onServiceLost(serviceName: String, host: String?)
}

@Singleton
class NsdDiscoveryEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val executor = Executors.newSingleThreadExecutor()

    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    /** API 34+: one callback per service name, so a service announced again doesn't stack callbacks. */
    private val serviceInfoCallbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

    /** Below API 34: services waiting to be resolved. NsdManager resolves one at a time, so they queue. */
    private val pendingResolves = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** Held only while discovery runs; multicast reception costs battery. */
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Where each remote service was resolved, by service name. A TV also advertises a cast service under
     * a different name, so a lost remote service is matched to its TV by host rather than by name.
     */
    private val resolvedRemoteHosts = ConcurrentHashMap<String, String>()

    // Callback registry (for RemoteRepository)
    private val listeners = mutableSetOf<NsdDiscoveryListener>()

    fun startDiscovery() {
        if (discoveryListeners.isNotEmpty()) {
            Log.w(TAG_NSD, "⚠️ Discovery already active")
            return
        }

        Log.d(TAG_NSD, "🔍 Starting TV discovery...")
        acquireMulticastLock()
        SERVICE_TYPES.forEach { type ->
            val listener = createDiscoveryListener(type)
            discoveryListeners.add(listener)
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stopDiscovery() {
        Log.d(TAG_NSD, "🛑 Stopping discovery...")

        discoveryListeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
                Log.d(TAG_NSD, "✅ Discovery stopped")
            } catch (e: IllegalStateException) {
                Log.w(TAG_NSD, "⚠️ stopServiceDiscovery ignored (expected)", e)
            } catch (e: Exception) {
                Log.w(TAG_NSD, "⚠️ stopServiceDiscovery failed", e)
            }
        }
        discoveryListeners.clear()

        serviceInfoCallbacks.keys.toList().forEach(::unregisterServiceInfoCallback)
        synchronized(pendingResolves) {
            pendingResolves.clear()
            resolving = false
        }
        resolvedRemoteHosts.clear()
        releaseMulticastLock()

        Log.d(TAG_NSD, "🧹 Discovery fully cleaned")
    }

    fun registerListener(listener: NsdDiscoveryListener) {
        listeners.add(listener)
    }

    private fun notifyDiscoveredTv(tv: DiscoveredTv) {
        listeners.forEach { it.onTvDiscovered(tv) }
        Log.d(TAG_NSD, "📡 TV discovered: ${tv.displayName}")
    }

    private fun rememberRemoteHost(serviceType: String, serviceName: String, host: String) {
        if (serviceType.contains(SERVICE_TYPE_REMOTE)) resolvedRemoteHosts[serviceName] = host
    }

    private fun notifyHttpScanNeeded(tv: DiscoveredTv) {
        listeners.forEach { it.onHttpScanNeeded(tv) }
        Log.d(TAG_NSD, "🔍 HTTP scan needed for: ${tv.host}")
    }

    private fun createDiscoveryListener(browseType: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.i(TAG_NSD, "✅ Discovery started for: $regType")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG_NSD, "⏹️ Discovery stopped: $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.i(TAG_NSD, "📡 Service found: ${serviceInfo.serviceName}/${serviceInfo.serviceType}")

            if (!serviceInfo.serviceType.contains(browseType)) {
                Log.d(TAG_NSD, "❌ Skipping non-TV service")
                return
            }

            Log.i(TAG_NSD, "🎉 ANDROID TV DETECTED! Resolving: ${serviceInfo.serviceName}")

            if (Build.VERSION.SDK_INT >= 34) {
                startServiceInfoCallback(serviceInfo)
            } else {
                startResolveLegacy(serviceInfo)
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.w(TAG_NSD, "🔌 Service lost: ${serviceInfo.serviceName}")
            // A TV that comes back is announced again and gets a fresh callback
            unregisterServiceInfoCallback(serviceKey(serviceInfo))
            // Only the remote service matters: the cast one can come and go while the remote still works
            if (browseType == SERVICE_TYPE_REMOTE) {
                val host = resolvedRemoteHosts.remove(serviceInfo.serviceName)
                listeners.forEach { it.onServiceLost(serviceInfo.serviceName, host) }
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG_NSD, "💥 Discovery START failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG_NSD, "💥 Discovery STOP failed: $errorCode")
        }
    }

    /** A service was resolved to an address: list it under its TXT name, or ask for an HTTP name lookup if it has none. */
    private fun handleResolved(discovered: NsdServiceInfo, info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        val port = info.port.takeIf { it > 0 } ?: return

        Log.d(TAG_NSD, "📍 Resolved ${info.serviceName} -> $host:$port")
        rememberRemoteHost(discovered.serviceType, info.serviceName, host)
        Log.d(TAG_NSD, "📋 TXT Records: ${info.attributes.map {
            "${it.key}=${it.value?.toString(Charsets.UTF_8)?.take(50)}"
        }}")

        val friendlyNameFromTxt = (NAME_ATTRIBUTES.firstNotNullOfOrNull { info.attributes[it]?.toString(Charsets.UTF_8) }
            ?: info.serviceName.takeIf { discovered.serviceType.contains(SERVICE_TYPE_REMOTE) })
            ?.cleanDeviceName()

        if (!friendlyNameFromTxt.isNullOrBlank()) {
            Log.i(TAG_NSD, "🎉 TXT friendlyName: $friendlyNameFromTxt")
            val tv = DiscoveredTv(
                name = info.serviceName,
                friendlyName = friendlyNameFromTxt,
                host = host,
                port = port
            )
            scope.launch { notifyDiscoveredTv(tv) }
            return
        }

        Log.d(TAG_NSD, "🔍 No TXT name, trying HTTP scan...")
        val tv = DiscoveredTv(name = info.serviceName, host = host, port = port)
        scope.launch { notifyHttpScanNeeded(tv) }
    }

    // ===== API 34+ path (ServiceInfoCallback) =====
    private fun startServiceInfoCallback(discovered: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT < 34) return
        val key = serviceKey(discovered)
        if (serviceInfoCallbacks.containsKey(key)) return

        val baseInfo = NsdServiceInfo().apply {
            serviceName = discovered.serviceName
            serviceType = discovered.serviceType
        }

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) = handleResolved(discovered, info)
            override fun onServiceLost() {}
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG_NSD, "ServiceInfoCallback registration failed: $errorCode")
                serviceInfoCallbacks.remove(key)
            }
            override fun onServiceInfoCallbackUnregistered() {}
        }

        serviceInfoCallbacks[key] = callback
        nsdManager.registerServiceInfoCallback(baseInfo, executor, callback)
    }

    private fun serviceKey(info: NsdServiceInfo) = "${info.serviceName}|${info.serviceType}"

    private fun unregisterServiceInfoCallback(key: String) {
        val callback = serviceInfoCallbacks.remove(key) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                nsdManager.unregisterServiceInfoCallback(callback)
            }
        } catch (e: Exception) {
            Log.w(TAG_NSD, "ServiceInfoCallback cleanup ignored", e)
        }
    }

    // ===== Legacy path, below API 34 (ResolveListener) =====
    private fun startResolveLegacy(discovered: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= 34) return

        synchronized(pendingResolves) {
            if (pendingResolves.any { serviceKey(it) == serviceKey(discovered) }) return
            pendingResolves.addLast(discovered)
        }
        resolveNextLegacy()
    }

    /** Starts the next queued resolve unless one is already running; each resolve starts the next when it ends. */
    @Suppress("DEPRECATION")
    private fun resolveNextLegacy() {
        val next = synchronized(pendingResolves) {
            if (resolving) return
            val service = pendingResolves.removeFirstOrNull() ?: return
            resolving = true
            service
        }

        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                try {
                    handleResolved(next, info)
                } finally {
                    finishLegacyResolve()
                }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG_NSD, "💥 Resolve failed: $errorCode for ${serviceInfo.serviceName}")
                finishLegacyResolve()
            }
            override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {}
            override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager.resolveService(next, listener)
        } catch (e: Exception) {
            Log.e(TAG_NSD, "resolveService threw for ${next.serviceName}", e)
            finishLegacyResolve()
        }
    }

    private fun finishLegacyResolve() {
        synchronized(pendingResolves) { resolving = false }
        resolveNextLegacy()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("airmousetv.nsd").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG_NSD, "🔒 Multicast lock acquired")
        } catch (e: Exception) {
            Log.e(TAG_NSD, "Failed to acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.takeIf { it.isHeld }?.release()
            Log.d(TAG_NSD, "🔓 Multicast lock released")
        } catch (e: Exception) {
            Log.w(TAG_NSD, "Failed to release multicast lock", e)
        }
        multicastLock = null
    }

    fun releaseResources() {
        releaseMulticastLock()
    }
}
