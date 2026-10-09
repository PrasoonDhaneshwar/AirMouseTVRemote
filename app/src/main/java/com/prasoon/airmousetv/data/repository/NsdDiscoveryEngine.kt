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
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_NSD = "NsdDiscoveryEngine"
private const val SERVICE_TYPE_CAST = "_googlecast._tcp."
// Advertised by the TV's remote service itself (port 6466), so it is present whenever the remote works
private const val SERVICE_TYPE_REMOTE = "_androidtvremote2._tcp."
private val SERVICE_TYPES = listOf(SERVICE_TYPE_REMOTE, SERVICE_TYPE_CAST)

/**
 * Callback interface for discovery events - RemoteRepository implements this
 */
interface NsdDiscoveryListener {
    fun onTvDiscovered(tv: DiscoveredTv)
    fun onHttpScanNeeded(tv: DiscoveredTv)
}

@Singleton
class NsdDiscoveryEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val executor = Executors.newSingleThreadExecutor()

    // Original listener properties (moved from RemoteRepository)
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val serviceInfoCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
    private var resolveListener: NsdManager.ResolveListener? = null

    private var multicastLock: WifiManager.MulticastLock? = null

    // Callback registry (for RemoteRepository)
    private val listeners = mutableSetOf<NsdDiscoveryListener>()

    init {
        acquireMulticastLock()
    }

    fun startDiscovery() {
        if (discoveryListeners.isNotEmpty()) {
            Log.w(TAG_NSD, "⚠️ Discovery already active")
            return
        }

        Log.d(TAG_NSD, "🔍 Starting TV discovery...")
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

        serviceInfoCallbacks.forEach {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    nsdManager.unregisterServiceInfoCallback(it)
                }
            } catch (e: Exception) {
                Log.w(TAG_NSD, "ServiceInfoCallback cleanup ignored", e)
            }
        }
        serviceInfoCallbacks.clear()
        resolveListener = null

        Log.d(TAG_NSD, "🧹 Discovery fully cleaned")
    }

    fun registerListener(listener: NsdDiscoveryListener) {
        listeners.add(listener)
    }

    private fun notifyDiscoveredTv(tv: DiscoveredTv) {
        listeners.forEach { it.onTvDiscovered(tv) }
        Log.d(TAG_NSD, "📡 TV discovered: ${tv.displayName}")
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
                startServiceInfoCallback(serviceInfo)  // YOUR EXACT ORIGINAL LOGIC
            } else {
                startResolveLegacy(serviceInfo)        // YOUR EXACT ORIGINAL LOGIC
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.w(TAG_NSD, "🔌 Service lost: ${serviceInfo.serviceName}")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG_NSD, "💥 Discovery START failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG_NSD, "💥 Discovery STOP failed: $errorCode")
        }
    }

    // ===== API 34+ path ===== (YOUR 100% ORIGINAL LOGIC)
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

                Log.d(TAG_NSD, "📍 Resolved ${info.serviceName} -> $host:$port")
                Log.d(TAG_NSD, "📋 TXT Records: ${info.attributes.map {
                    "${it.key}=${it.value?.toString(Charsets.UTF_8)?.take(50)}"
                }}")

                val friendlyNameFromTxt = info.attributes["fn"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["friendlyName"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["name"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["deviceName"]?.toString(Charsets.UTF_8)
                    ?: info.serviceName.takeIf { discovered.serviceType.contains(SERVICE_TYPE_REMOTE) }

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

            override fun onServiceLost() {}
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG_NSD, "ServiceInfoCallback registration failed: $errorCode")
            }
            override fun onServiceInfoCallbackUnregistered() {}
        }

        serviceInfoCallbacks.add(callback)
        nsdManager.registerServiceInfoCallback(baseInfo, executor, callback)
    }

    // ===== Legacy path (<34) ===== (YOUR 100% ORIGINAL LOGIC)
    private fun startResolveLegacy(discovered: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= 34) return

        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val port = info.port.takeIf { it > 0 } ?: return
                Log.d(TAG_NSD, "📍 Resolved ${info.serviceName} -> $host:$port")

                Log.d(TAG_NSD, "📋 TXT Records: ${info.attributes.map {
                    "${it.key}=${it.value?.toString(Charsets.UTF_8)?.take(50)}"
                }}")

                val friendlyNameFromTxt = info.attributes["fn"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["friendlyName"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["name"]?.toString(Charsets.UTF_8)
                    ?: info.attributes["deviceName"]?.toString(Charsets.UTF_8)
                    ?: info.serviceName.takeIf { discovered.serviceType.contains(SERVICE_TYPE_REMOTE) }

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

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG_NSD, "💥 Resolve failed: $errorCode for ${serviceInfo.serviceName}")
            }
            override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {}
            override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        resolveListener = listener
        nsdManager.resolveService(discovered, listener)
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("airmousetv.nsd")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            Log.d(TAG_NSD, "🔒 Multicast lock acquired")
        } catch (e: Exception) {
            Log.e(TAG_NSD, "Failed to acquire multicast lock", e)
        }
    }

    fun releaseResources() {
        multicastLock?.release()
        Log.d(TAG_NSD, "🔓 Multicast lock RELEASED")
        multicastLock = null
    }
}
