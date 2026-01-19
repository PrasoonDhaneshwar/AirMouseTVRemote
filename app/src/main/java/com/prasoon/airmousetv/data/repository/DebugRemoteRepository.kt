package com.prasoon.airmousetv.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.prasoon.airmousetv.data.model.DiscoveredTv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import javax.inject.Singleton


private const val TAG = "DebugRemoteRepository"


@Singleton
class DebugRemoteRepository(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val executor = Executors.newSingleThreadExecutor()

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // For API < 34
    private var resolveListener: NsdManager.ResolveListener? = null

    // For API >= 34
    private var serviceInfoCallback: NsdManager.ServiceInfoCallback? = null

    //private val SERVICE_TYPE = "_androidtvremote._tcp."
    private val SERVICE_TYPE = "_http._tcp."  // Should find routers, printers
//    private val SERVICE_TYPE = "_googlecast._tcp."


    // Detect ALL common services + Chromecast TVs
    private val SERVICE_TYPES = listOf(
        "_googlecast._tcp.",      // ✅ Your Android TV ✓
        "_http._tcp.",           // WLED, routers, etc.
        "_spotify-connect._tcp.",
        "_raop._tcp.",           // Apple AirPlay
        "_airplay._tcp.",
        "_services._dns-sd._udp." // ALL services
    )

    private var currentServiceIndex = 0

//    fun startDiscovery() {
//        scanNextServiceType()
//    }


    /// JUST A TEST

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                val isWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
                Log.d(TAG, "📡 WiFi network: $isWifi")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network check failed", e)
        }
    }

    private fun acquireMulticastLock() {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("airmousetv.nsd")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()
        Log.d(TAG, "🔒 Multicast lock ACQUIRED")
        Log.d(TAG, "🔒 Multicast lock acquired")
    }

    fun startDiscovery() {
        checkNetwork()
        Log.d(TAG, "🔍 SIMPLE SINGLE SCAN TEST - _googlecast._tcp.")

        // SINGLE service type only - NO multi-scan complexity
        val SERVICE_TYPE = "_googlecast._tcp."

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "✅ STARTED $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "🎉 FOUND: ${serviceInfo.serviceName} (${serviceInfo.serviceType})")
                // DON'T resolve yet - just log
            }

            override fun onDiscoveryStopped(regType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                Log.e(TAG, "💥 START FAILED: $errorCode")
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                Log.e(TAG, "💥 STOP FAILED: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener!!)
    }

    /// JUST A TEST


    private fun scanNextServiceType() {
        if (currentServiceIndex >= SERVICE_TYPES.size) {
            Log.i(TAG, "✅ All service types scanned! Total devices: ${_discoveredTvs.value.size}")
            scope.launch(Dispatchers.Main) {
                _discoveredTvs.value = _discoveredTvs.value.distinctBy { it.host }.sortedBy { it.name }
            }
            return
        }

        // Stop previous discovery first
        stopDiscovery()

        val serviceType = SERVICE_TYPES[currentServiceIndex]
        Log.i(TAG, "🔍 [$currentServiceIndex/${SERVICE_TYPES.size}] Scanning: $serviceType")

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "✅ Started scanning $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "📡 FOUND via $serviceType: ${serviceInfo.serviceName}")
                resolveService(serviceInfo, serviceType) // ✅ 2 arguments
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.w(TAG, "🔌 Lost: ${serviceInfo.serviceName}")
                scope.launch(Dispatchers.Main) {
                    _discoveredTvs.value = _discoveredTvs.value.filterNot { it.name == serviceInfo.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "⏹️ Finished scanning $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "💥 Start failed for $serviceType: $errorCode")
                // Continue to next service type
                proceedToNextService()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "💥 Stop failed for $serviceType: $errorCode")
                proceedToNextService()
            }
        }

        try {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener!!
            )
            Log.i(TAG, "🚀 Discovery started for $serviceType")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to start discovery for $serviceType", e)
            proceedToNextService()
        }

        // Auto-advance to next service type after 8 seconds
        scope.launch {
            delay(8000)
            Log.i(TAG, "⏰ Timeout for $serviceType, moving to next...")
            proceedToNextService()
        }
    }

    private fun proceedToNextService() {
        currentServiceIndex++
        if (currentServiceIndex < SERVICE_TYPES.size) {
            scope.launch {
                delay(1000) // Brief pause between scans
                scanNextServiceType()
            }
        }
    }

    // 🔥 NEW: Universal resolveService() method
    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        serviceType: String
    ) {
        Log.i(TAG, "🔄 Resolving: ${serviceInfo.serviceName}")

        if (Build.VERSION.SDK_INT >= 34) {
            resolveWithCallback(serviceInfo, serviceType)
        } else {
            resolveLegacy(serviceInfo, serviceType)
        }
    }

    private fun resolveWithCallback(serviceInfo: NsdServiceInfo, serviceType: String) {
        if (Build.VERSION.SDK_INT < 34) return

        Log.d(TAG, "🌐 API34+ Resolving with ServiceInfoCallback: ${serviceInfo.serviceName}")

        val baseInfo = NsdServiceInfo().apply {
            setServiceName(serviceInfo.serviceName)
            setServiceType(serviceInfo.serviceType)
        }

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) {
                Log.i(TAG, "📍 Service UPDATED: ${info.serviceName}")
                val host = info.host?.hostAddress
                val port = info.port

                Log.i(TAG, "🌐 Host: $host, Port: $port")

                if (host == null) {
                    Log.w(TAG, "⚠️ No host available yet, waiting for update...")
                    return
                }
                if (port <= 0) {
                    Log.w(TAG, "⚠️ Invalid port: $port")
                    return
                }

                val mac = getMacAddress(host)
                val tv = DiscoveredTv(
                    name = info.serviceName,
                    host = host,
                    port = port,
                    macAddress = mac,
                    serviceType = serviceType
                )

                Log.i(TAG, "✅ RESOLVED: ${tv.name} @ ${tv.host}:${tv.port} [${tv.serviceType}]")
                addDiscoveredDevice(tv)
            }

            override fun onServiceLost() {
                Log.w(TAG, "🔌 Service LOST: ${baseInfo.serviceName}")
                scope.launch(Dispatchers.Main) {
                    _discoveredTvs.value = _discoveredTvs.value.filterNot { it.name == baseInfo.serviceName }
                }
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG, "💥 Callback REGISTRATION FAILED: $errorCode for ${serviceInfo.serviceName}")
                when (errorCode) {
                    NsdManager.FAILURE_ALREADY_ACTIVE -> Log.e(TAG, "❌ Already active")
                    NsdManager.FAILURE_BAD_PARAMETERS -> Log.e(TAG, "❌ Bad parameters")
                    else -> Log.e(TAG, "❌ Unknown error: $errorCode")
                }
            }

            override fun onServiceInfoCallbackUnregistered() {
                Log.d(TAG, "✅ Callback UNREGISTERED for ${serviceInfo.serviceName}")
            }
        }

        serviceInfoCallback = callback
        try {
            nsdManager.registerServiceInfoCallback(baseInfo, executor, callback)
            Log.d(TAG, "🔗 Callback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to register callback", e)
        }
    }

    private fun resolveLegacy(serviceInfo: NsdServiceInfo, serviceType: String) {
        if (Build.VERSION.SDK_INT >= 34) return

        Log.d(TAG, "🔧 Legacy resolveService: ${serviceInfo.serviceName}")

        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                Log.i(TAG, "✅ Legacy RESOLVED: ${info.serviceName}")
                val host = info.host?.hostAddress
                val port = info.port

                Log.i(TAG, "🌐 Legacy - Host: $host, Port: $port")

                if (host == null || port <= 0) {
                    Log.w(TAG, "⚠️ Legacy resolve incomplete: host=$host, port=$port")
                    return
                }

                val mac = getMacAddress(host)
                val tv = DiscoveredTv(
                    name = info.serviceName,
                    host = host,
                    port = port,
                    macAddress = mac,
                    serviceType = serviceType
                )

                Log.i(TAG, "✅ Legacy TV ADDED: ${tv.name} @ ${tv.host}:${tv.port}")
                addDiscoveredDevice(tv)
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "💥 Legacy RESOLVE FAILED: ${serviceInfo.serviceName}, code: $errorCode")
                when (errorCode) {
                    NsdManager.FAILURE_ALREADY_ACTIVE -> Log.e(TAG, "❌ Resolve already active")
                    NsdManager.FAILURE_INTERNAL_ERROR -> Log.e(TAG, "❌ Internal error")
                    else -> Log.e(TAG, "❌ Unknown resolve error: $errorCode")
                }
            }

            // API 34+ only methods - safe to implement for compatibility
            override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "⏹️ Legacy resolution STOPPED: ${serviceInfo.serviceName}")
            }

            override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "💥 Legacy stop resolution FAILED: $errorCode")
            }
        }

        resolveListener = listener
        try {
            nsdManager.resolveService(serviceInfo, listener)
            Log.d(TAG, "🔗 Legacy resolve started")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Legacy resolve failed to start", e)
        }
    }

    private fun addDiscoveredDevice(tv: DiscoveredTv) {
        scope.launch(Dispatchers.Main) {
            val list = _discoveredTvs.value.toMutableList()
            val existingIndex = list.indexOfFirst { it.host == tv.host }
            if (existingIndex >= 0) {
                list[existingIndex] = tv // Update with better info
                Log.d(TAG, "🔄 Updated: ${tv.name}")
            } else {
                list.add(tv)
                Log.i(TAG, "➕ NEW: ${tv.name} (${tv.serviceType})")
            }
            _discoveredTvs.value = list
        }
    }

    // 🔥 MAC Address lookup
    private fun getMacAddress(ip: String): String? {
        return try {
            val p = Runtime.getRuntime().exec("ping -c1 -w1 $ip")
            p.waitFor()

            val arp = Runtime.getRuntime().exec("cat /proc/net/arp | grep $ip")
            val reader = BufferedReader(InputStreamReader(arp.inputStream))
            val line = reader.readLine()
            reader.close()

            line?.split("\\s+".toRegex())?.getOrNull(3)
        } catch (e: Exception) {
            Log.w(TAG, "MAC lookup failed for $ip", e)
            null
        }
    }


//    fun startDiscovery() {
//        Log.d(TAG, "🔍 Starting TV discovery...")
//        if (discoveryListener != null) {
//            Log.w(TAG, "⚠️ Discovery already running, stopping first...")
//            stopDiscovery()
//        }
//
//        discoveryListener = object : NsdManager.DiscoveryListener {
//            override fun onDiscoveryStarted(regType: String) {
//                Log.i(TAG, "✅ Discovery started for: $regType")
//                Log.i(TAG, "🎯 Looking for service: $SERVICE_TYPE")
//            }
//
//            override fun onDiscoveryStopped(serviceType: String) {
//                Log.i(TAG, "⏹️ Discovery stopped: $serviceType")
//            }
//
//            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
//                Log.i(TAG, "🔍 ALL SERVICES:")
//                Log.i(TAG, "  Name: ${serviceInfo.serviceName}")
//                Log.i(TAG, "  Type: ${serviceInfo.serviceType}")
//                Log.i(TAG, "  ----")
//
//
//                Log.i(TAG, "📡 Service found: ${serviceInfo.serviceName}")
//                Log.i(TAG, "📋 Service type: ${serviceInfo.serviceType}")
//                Log.i(TAG, "🏷️ Service details: ${serviceInfo.serviceName}/${serviceInfo.serviceType}")
//
//                if (serviceInfo.serviceType != SERVICE_TYPE) {
//                    Log.d(TAG, "❌ Skipping non-TV service: ${serviceInfo.serviceType}")
//                    return
//                }
//
//                Log.i(TAG, "🎉 ANDROID TV DETECTED! Resolving: ${serviceInfo.serviceName}")
//
//                if (Build.VERSION.SDK_INT >= 34) {
//                    startServiceInfoCallback(serviceInfo)
//                } else {
//                    startResolveLegacy(serviceInfo)
//                }
//            }
//
//            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
//                Log.w(TAG, "🔌 Service lost: ${serviceInfo.serviceName}")
//                val name = serviceInfo.serviceName
//                scope.launch(Dispatchers.Main) {
//                    _discoveredTvs.value =
//                        _discoveredTvs.value.filterNot { it.name == name }
//                    Log.i(TAG, "📊 TVs after removal: ${_discoveredTvs.value.size}")
//                }
//            }
//
//            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
//                Log.e(TAG, "💥 Discovery START failed for $serviceType, code: $errorCode")
//                when (errorCode) {
//                    NsdManager.FAILURE_ALREADY_ACTIVE -> Log.e(TAG, "❌ Already active")
//                    NsdManager.FAILURE_INTERNAL_ERROR -> Log.e(TAG, "❌ Internal error")
//                    else -> Log.e(TAG, "❌ Unknown error: $errorCode")
//                }
//                stopDiscovery()
//            }
//
//            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
//                Log.e(TAG, "💥 Discovery STOP failed for $serviceType, code: $errorCode")
//                stopDiscovery()
//            }
//        }
//
//        nsdManager.discoverServices(
//            SERVICE_TYPE,
//            NsdManager.PROTOCOL_DNS_SD,
//            discoveryListener!!
//        )
//    }

    fun stopDiscovery() {
        Log.d(TAG, "🛑 Stopping discovery...")

        discoveryListener?.let {
            runCatching {
                nsdManager.stopServiceDiscovery(it)
                Log.d(TAG, "✅ Discovery listener stopped")
            }.onFailure { e ->
                Log.e(TAG, "❌ Failed to stop discovery", e)
            }
        }
        discoveryListener = null

        if (Build.VERSION.SDK_INT >= 34) {
            serviceInfoCallback?.let {
                runCatching {
                    nsdManager.unregisterServiceInfoCallback(it)
                    Log.d(TAG, "✅ ServiceInfoCallback unregistered")
                }.onFailure { e ->
                    Log.e(TAG, "❌ Failed to unregister callback", e)
                }
            }
            serviceInfoCallback = null
        } else {
            resolveListener?.let {
                runCatching {
                    nsdManager.stopServiceResolution(it)
                    Log.d(TAG, "✅ Resolve listener stopped")
                }.onFailure { e ->
                    Log.e(TAG, "❌ Failed to stop resolution", e)
                }
            }
            resolveListener = null
        }

        Log.d(TAG, "📊 Final TV count: ${_discoveredTvs.value.size}")
    }

    // --- API 34+ path using ServiceInfoCallback ---

    private fun startServiceInfoCallback(discovered: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT < 34) return

        Log.d(TAG, "🌐 API34+ Using ServiceInfoCallback for ${discovered.serviceName}")

        val baseInfo = NsdServiceInfo().apply {
            serviceName = discovered.serviceName
            serviceType = discovered.serviceType
        }

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) {
                val host = info.host
                val port = info.port
                Log.i(TAG, "📍 Service updated - Host: ${host?.hostAddress}, Port: $port")

                if (host == null) {
                    Log.w(TAG, "⚠️ No host available yet, waiting...")
                    return
                }
                if (port <= 0) {
                    Log.w(TAG, "⚠️ Invalid port: $port")
                    return
                }

                val tv = DiscoveredTv(
                    name = info.serviceName,
                    host = host.hostAddress ?: "unknown",
                    port = port
                )

                Log.i(TAG, "✅ TV RESOLVED: ${tv.name} @ ${tv.host}:${tv.port}")

                scope.launch(Dispatchers.Main) {
                    val list = _discoveredTvs.value.toMutableList()
                    val idx = list.indexOfFirst { it.name == tv.name }
                    if (idx >= 0) {
                        Log.d(TAG, "🔄 Updating existing TV: ${tv.name}")
                        list[idx] = tv
                    } else {
                        Log.i(TAG, "➕ Adding NEW TV: ${tv.name}")
                        list.add(tv)
                    }
                    _discoveredTvs.value = list
                    Log.i(TAG, "📊 Total TVs now: ${list.size}")
                }
            }

            override fun onServiceLost() {
                Log.w(TAG, "🔌 ServiceInfoCallback: Service lost")
                val name = baseInfo.serviceName
                scope.launch(Dispatchers.Main) {
                    _discoveredTvs.value = _discoveredTvs.value.filterNot { it.name == name }
                }
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG, "💥 ServiceInfoCallback registration failed: $errorCode")
            }

            override fun onServiceInfoCallbackUnregistered() {
                Log.d(TAG, "✅ ServiceInfoCallback unregistered")
            }
        }

        serviceInfoCallback = callback
        nsdManager.registerServiceInfoCallback(baseInfo, executor, callback)
    }

    // Legacy resolveService
    private fun startResolveLegacy(discovered: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= 34) return

        Log.d(TAG, "🔧 Legacy resolveService for ${discovered.serviceName}")

        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host
                val port = info.port
                Log.i(TAG, "📍 Legacy resolved - Host: ${host?.hostAddress}, Port: $port")

                if (host == null || port <= 0) {
                    Log.w(TAG, "⚠️ Legacy resolve failed: host=$host, port=$port")
                    return
                }

                val tv = DiscoveredTv(
                    name = info.serviceName,
                    host = host.hostAddress ?: "unknown",
                    port = port
                )

                Log.i(TAG, "✅ Legacy TV RESOLVED: ${tv.name} @ ${tv.host}:${tv.port}")

                scope.launch(Dispatchers.Main) {
                    val list = _discoveredTvs.value.toMutableList()
                    val idx = list.indexOfFirst { it.name == tv.name }
                    if (idx >= 0) {
                        list[idx] = tv
                    } else {
                        list.add(tv)
                    }
                    _discoveredTvs.value = list
                    Log.i(TAG, "📊 Total TVs now: ${list.size}")
                }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "💥 Legacy resolve FAILED for ${serviceInfo.serviceName}, code: $errorCode")
            }

            override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "⏹️ Legacy resolution stopped: ${serviceInfo.serviceName}")
            }

            override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "💥 Legacy stop resolution failed: $errorCode")
            }
        }

        resolveListener = listener
        nsdManager.resolveService(discovered, listener)
    }
}