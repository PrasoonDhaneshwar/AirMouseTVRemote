package com.prasoon.airmousetv.data.repository

import android.util.Log
import com.prasoon.airmousetv.data.api.scanCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_SUBNET = "SubnetScanner"
/** Port the TV's remote service listens on; also the one a scan looks for. */
const val TV_REMOTE_PORT = 6466
/** A host on the same Wi-Fi answers within a few milliseconds, so waiting longer only slows the sweep. */
private const val CONNECT_TIMEOUT_MS = 400
/** Probes in flight at once; enough to finish a /24 in ~2 s without flooding a small router. */
private const val MAX_PARALLEL_PROBES = 64

/**
 * Finds TVs by connecting to the remote port on every address of the phone's network. This is the
 * fallback for networks that drop multicast, where the mDNS discovery sees nothing.
 */
@Singleton
class SubnetScanner @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {

    /** Addresses on the phone's network that accept a connection on [TV_REMOTE_PORT]; empty if there is no IPv4 Wi-Fi. */
    suspend fun findTvHosts(): List<String> = withContext(Dispatchers.IO) {
        val (own, prefix) = networkMonitor.localIpv4() ?: return@withContext emptyList()
        val candidates = scanCandidates(own, prefix)
        Log.d(TAG_SUBNET, "Probing ${candidates.size} addresses for port $TV_REMOTE_PORT")

        val gate = Semaphore(MAX_PARALLEL_PROBES)
        coroutineScope {
            candidates
                .map { host -> async { gate.withPermit { host.takeIf { accepts(it) } } } }
                .awaitAll()
                .filterNotNull()
        }
    }

    private fun accepts(host: String): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, TV_REMOTE_PORT), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: Exception) {
        false
    }
}
