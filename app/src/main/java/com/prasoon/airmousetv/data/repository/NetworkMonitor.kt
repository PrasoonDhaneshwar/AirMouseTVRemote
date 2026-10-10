package com.prasoon.airmousetv.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NetworkMonitor"

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Exposed flows
    val isNetworkAvailable: StateFlow<Boolean> =
        networkAvailabilityFlow().stateIn(
            scope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = currentNetworkConnected()
        )

    val isWifiEnabled: StateFlow<Boolean> =
        wifiEnabledFlow().stateIn(
            scope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = wifiManager.isWifiEnabled
        )

    init {
        // Log the starting network state, to help debug connectivity problems
        try {
            Log.d(TAG, "📶 WiFi enabled: ${wifiManager.isWifiEnabled}")
            Log.d(TAG, "🌐 Network connected: ${currentNetworkConnected()}")
        } catch (e: Exception) {
            Log.e(TAG, "Network check failed", e)
        }
    }

    private fun currentNetworkConnected(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            // Any local connectivity is enough: the TV is on the LAN, internet isn't required
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    private fun networkAvailabilityFlow(): Flow<Boolean> = callbackFlow {
        trySend(currentNetworkConnected())

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "🌐 Network available")
                trySend(true)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "🌐 Network lost")
                trySend(currentNetworkConnected())
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private fun wifiEnabledFlow(): Flow<Boolean> = callbackFlow {
        // There is no simple callback for Wi‑Fi enable/disable that is stable across versions,
        // so this is kept simple. If you need real‑time Wi‑Fi state, add a BroadcastReceiver
        // for WIFI_STATE_CHANGED_ACTION and forward its values into this flow.
        trySend(wifiManager.isWifiEnabled)
        awaitClose {
            // no‑op for now
        }
    }
}
