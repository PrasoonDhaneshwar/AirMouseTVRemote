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
/**
 * Timeout of the second pass, used only when the first found nothing. Hosts that are absent or refuse
 * answer at once, so only silently dropped probes cost this long, and a congested router drops some.
 */
private const val PATIENT_CONNECT_TIMEOUT_MS = 1500
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
    suspend fun findTvHosts(patient: Boolean = true): List<String> = withContext(Dispatchers.IO) {
        val (own, prefix) = networkMonitor.localIpv4() ?: return@withContext emptyList()
        val candidates = scanCandidates(own, prefix)
        Log.d(TAG_SUBNET, "Probing ${candidates.size} addresses for port $TV_REMOTE_PORT")

        val started = System.currentTimeMillis()
        // A busy Wi-Fi drops probe packets, and a dropped one looks like "no TV here". So if the quick
        // pass finds nothing, go over the addresses once more, patiently.
        // The patient pass is skipped for the quiet repeat sweeps: with no TV on the network it made each
        // sweep ~7.6 s long, so the phone was probing 253 addresses for most of every 18 s, for as long as the list was empty.
        val found = probe(candidates, CONNECT_TIMEOUT_MS)
            .ifEmpty { if (patient) probe(candidates, PATIENT_CONNECT_TIMEOUT_MS) else emptyList() }
        Log.d(TAG_SUBNET, "Port $TV_REMOTE_PORT open on $found after ${System.currentTimeMillis() - started} ms")
        found
    }

    private suspend fun probe(candidates: List<String>, timeoutMs: Int): List<String> {
        val gate = Semaphore(MAX_PARALLEL_PROBES)
        return coroutineScope {
            candidates
                .map { host -> async { gate.withPermit { host.takeIf { accepts(it, timeoutMs) } } } }
                .awaitAll()
                .filterNotNull()
        }
    }

    private fun accepts(host: String, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, TV_REMOTE_PORT), timeoutMs) }
        true
    } catch (e: Exception) {
        false
    }
}
