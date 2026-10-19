package com.prasoon.airmousetv.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.prasoon.airmousetv.data.api.FrameCodec
import com.prasoon.airmousetv.data.api.computePairingSecret
import com.prasoon.airmousetv.data.api.proto.RemoteMessageEncoder
import com.prasoon.airmousetv.data.model.ConnectionState
import com.prasoon.airmousetv.data.model.ImeField
import com.prasoon.airmousetv.proto.polo.OuterMessage
import com.prasoon.airmousetv.proto.remote.RemoteMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLSocket

private const val TAG = "RemoteSession"
/** Polo pairing port on the TV. */
private const val PAIRING_PORT = 6467
/** Remote-control port on the TV; only accepts clients it has paired with. */
private const val REMOTE_PORT = 6466
/**
 * Features we can use, as a bitmask: PING 1, KEY 2, IME 4, POWER 32, VOLUME 64, APP_LINK 512.
 * The session activates only those the TV also offers (voice is deliberately left out).
 */
private const val APP_LINK_FEATURE = 512
private const val SUPPORTED_FEATURES = 1 or 2 or 4 or 32 or 64 or 512
/**
 * How long to wait for an already-paired TV to activate the session before pairing instead. A TV
 * that doesn't trust us closes the connection at once, so this only has to cover a slow trusted
 * TV, such as a stick waking from standby.
 */
private const val TRUSTED_CHECK_TIMEOUT_MS = 10_000L
/** No bytes from the TV for this long on the remote connection means it is gone; most TVs ping every ~5 s, but a waipu stick went quiet for over 15 s. */
private const val REMOTE_READ_TIMEOUT_MS = 40_000
/** How many recent events [RemoteSessionManager.diagnosticsReport] keeps. */
private const val MAX_DIAGNOSTIC_LINES = 60
/** Silence allowed on a remote connection whose TV hasn't pinged yet, so quiet sticks aren't dropped while idle. */
private const val UNPINGED_READ_TIMEOUT_MS = 90_000
/** Shorter timeout for the pre-connect reachability probe, so an off TV is reported quickly. */
private const val REACHABILITY_TIMEOUT_MS = 1500

/**
 * Polo pairing over TLS on port 6467:
 *   C PairingRequest -> S PairingRequestAck
 *   C Options        -> S Options
 *   C Configuration  -> S ConfigurationAck   (TV now shows the 6-hex-digit code)
 *   C Secret         -> S SecretAck          (paired; TV remembers our client cert)
 *
 * Then a second TLS connection on port 6466 carries the remote-control protocol:
 *   S RemoteConfigure -> C RemoteConfigure, S RemoteSetActive -> C RemoteSetActive(features),
 *   then pings are answered and RemoteKeyInject messages are sent.
 */
@Singleton
class RemoteSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Where the Polo handshake is; each incoming message is only accepted in its expected phase. */
    private enum class Phase { IDLE, AWAIT_REQUEST_ACK, AWAIT_OPTIONS, AWAIT_CONFIG_ACK, AWAIT_CODE, AWAIT_SECRET_ACK, PAIRED }

    /** One open TLS connection with its streams. [writeLock] keeps frames from interleaving. */
    private class Conn(val socket: SSLSocket, val input: DataInputStream, val output: DataOutputStream) {
        /** Set before closing so the reader loop can tell a deliberate close from a failure. */
        @Volatile var closed = false
        val writeLock = Mutex()
    }

    /** Backing flow for [connectionState]. */
    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    /** Progress of the connection, observed by the ViewModel. */
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /** Backing flow for [imeField]. */
    private val _imeField = MutableStateFlow<ImeField?>(null)
    /** The text field focused on the TV, if it has reported one this session. */
    val imeField: StateFlow<ImeField?> = _imeField

    /** Backing flow for [foregroundApp]. */
    private val _foregroundApp = MutableStateFlow<String?>(null)
    /** Package of the app in front on the TV, as last reported by it; null until it has reported one. */
    val foregroundApp: StateFlow<String?> = _foregroundApp

    private val diagnostics = ArrayDeque<String>()

    /** Remembers [event] (most recent [MAX_DIAGNOSTIC_LINES]) so a user without adb can share what the TV did. */
    @Synchronized
    fun note(event: String) {
        if (diagnostics.size >= MAX_DIAGNOSTIC_LINES) diagnostics.removeFirst()
        diagnostics.addLast("%tT %s".format(System.currentTimeMillis(), event))
    }

    /** Recent handshake and text-input events, as plain text to paste into a bug report. */
    @Synchronized
    fun diagnosticsReport(): String = diagnostics.joinToString("\n")

    /** True once the TV has pinged in this remote session, which tells us it keeps the line busy while idle. */
    @Volatile private var pingSeen = false

    /** Features agreed with the TV in the current remote session. */
    private var activeFeatures = SUPPORTED_FEATURES

    /** TV address, kept so the remote session can be opened right after pairing. */
    private var host: String? = null
    /** Connection to port 6467; only exists while pairing. */
    private var pairingConn: Conn? = null
    /** Connection to port 6466; the one key presses go through. */
    private var remoteConn: Conn? = null
    /** TV certificate from the pairing handshake; its public key goes into the secret. */
    private var serverCert: X509Certificate? = null
    /** Written from the reader coroutine, read from the UI thread. */
    @Volatile private var phase = Phase.IDLE
    /** Owns the reader loops; never cancelled so the singleton can reconnect after a failure. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Frame hex is only logged in debuggable builds: it includes the pairing Secret
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Opens the TLS sockets with our client certificate. */
    private val tls = TvTlsConnector(context.filesDir)

    /** The TV certificate seen on the remote port, per address, so a different device at that address is refused. */
    private val pinStore by lazy {
        CertPinStore(context.getSharedPreferences("tv_cert_pins", Context.MODE_PRIVATE))
    }

    private fun pinKey(host: String) = "$host:$REMOTE_PORT"

    /**
     * Connects to the TV. If the TV already trusts our saved certificate the remote session on
     * 6466 comes up immediately; otherwise it drops us and we fall back to Polo pairing.
     */
    suspend fun connect(host: String, clientName: String = "AirMouseTV") {
        resetConnections(host)

        if (tryRemoteSession(host)) return
        startPairing(host, clientName)
    }

    /**
     * Re-opens the remote session with a TV that has already paired with us. Unlike [connect]
     * this never falls back to pairing; it returns false if the TV is unreachable or no longer
     * trusts our certificate.
     */
    suspend fun reconnect(host: String): Boolean {
        resetConnections(host)
        return tryRemoteSession(host)
    }

    /** Drops any open connection and returns to a clean idle state aimed at [host]. */
    private fun resetConnections(host: String) {
        dropConnections()
        this.host = host
        _connectionState.value = ConnectionState.Idle
    }

    /** Closes both connections and forgets the pairing progress and the TV's text field. */
    private fun dropConnections() {
        closeConn(pairingConn)
        closeConn(remoteConn)
        pairingConn = null
        remoteConn = null
        phase = Phase.IDLE
        _imeField.value = null
    }

    /**
     * Quick check that something is listening on the TV's remote or pairing port, before the slower
     * TLS connect. A plain TCP connect to both ports in parallel; true if either accepts within
     * [REACHABILITY_TIMEOUT_MS]. A TV that is off or asleep times out or refuses on both.
     */
    suspend fun isReachable(host: String): Boolean = coroutineScope {
        listOf(REMOTE_PORT, PAIRING_PORT)
            .map { port ->
                async(Dispatchers.IO) {
                    try {
                        Socket().use { it.connect(InetSocketAddress(host, port), REACHABILITY_TIMEOUT_MS) }
                        true
                    } catch (e: Exception) {
                        Log.d(TAG, "Port $port on $host not reachable: ${e.message}")
                        false
                    }
                }
            }
            .awaitAll()
            .any { it }
    }

    /**
     * Opens the remote session and waits briefly for the TV to activate it.
     * Returns true if the TV already trusts our certificate; false if it refused, dropped us
     * or stayed silent, in which case pairing is needed.
     */
    private suspend fun tryRemoteSession(host: String): Boolean {
        val conn = try {
            openRemoteConn(host)
        } catch (e: Exception) {
            Log.d(TAG, "Remote session unavailable, will pair: ${e.message}")
            return false
        }
        val state = withTimeoutOrNull(TRUSTED_CHECK_TIMEOUT_MS) {
            connectionState.first { it is ConnectionState.Connected || it is ConnectionState.Disconnected }
        }
        if (state is ConnectionState.Connected) return true

        Log.d(TAG, "TV did not accept saved certificate, pairing instead")
        closeConn(conn)
        if (remoteConn === conn) remoteConn = null
        _connectionState.value = ConnectionState.Idle
        return false
    }

    /** Opens the pairing connection and begins the Polo handshake; replies are handled in [handlePoloFrame]. */
    private suspend fun startPairing(host: String, clientName: String) {
        Log.d(TAG, "Starting pairing with $host")
        val conn = openTlsSocket(host, PAIRING_PORT)
        pairingConn = conn
        serverCert = conn.socket.session.peerCertificates.firstOrNull() as? X509Certificate

        phase = Phase.AWAIT_REQUEST_ACK
        send(RemoteMessageEncoder.encodePairingRequest(clientName), conn)

        startReaderLoop(conn) { handlePoloFrame(conn, it) }
    }

    /** Connects to the remote-control port and starts reading from it. */
    private suspend fun openRemoteConn(host: String): Conn {
        val conn = openTlsSocket(host, REMOTE_PORT)
        // A TV switched off abruptly never closes the socket, so a blocking read would wait for hours.
        // Most TVs ping every few seconds, so once one has pinged, silence for REMOTE_READ_TIMEOUT_MS
        // means it is gone: the read throws and the session reports Disconnected, which starts the
        // reconnect. Until a first ping, wait longer: some sticks stay quiet when idle, and cutting
        // them off after 15 s made the session reconnect over and over.
        pingSeen = false
        conn.socket.soTimeout = UNPINGED_READ_TIMEOUT_MS
        remoteConn = conn
        startReaderLoop(conn) { handleRemoteFrame(conn, it) }
        return conn
    }

    /** Opens a TLS connection to [host]:[port] on the IO dispatcher; the remote port's certificate must match its pin. */
    private suspend fun openTlsSocket(host: String, port: Int): Conn =
        withContext(Dispatchers.IO) {
            // Pairing (6467) trusts whatever answers, since the code proves the TV. The remote port must
            // keep presenting the certificate it first showed.
            val socket = tls.open(host, port) { chain ->
                if (port == REMOTE_PORT) verifyPinnedCert(host, chain)
            }
            Conn(
                socket,
                DataInputStream(BufferedInputStream(socket.getInputStream())),
                DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            )
        }

    /** Throws unless the TV at [host] presents the same certificate as the first time it was seen on the remote port. */
    private fun verifyPinnedCert(host: String, chain: Array<X509Certificate>?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("TV sent no certificate")
        if (!pinStore.verifyOrRemember(pinKey(host), CertPinStore.fingerprint(leaf))) {
            note("TV certificate at $host changed, refusing the connection")
            throw CertificateException("The certificate of the device at $host is not the one it paired with")
        }
    }

    /** Sends on the remote-control connection once it exists, otherwise on the pairing connection. */
    suspend fun send(payload: ByteArray) {
        val conn = remoteConn ?: pairingConn
        if (conn == null) {
            Log.w(TAG, "send() with no open connection")
            return
        }
        send(payload, conn)
    }

    /** Writes one length-prefixed frame to [conn]; a no-op if it has been closed. */
    private suspend fun send(payload: ByteArray, conn: Conn) {
        withContext(Dispatchers.IO) {
            conn.writeLock.withLock {
                if (conn.closed) return@withLock
                logFrame("Sending", payload)
                FrameCodec.writeFrame(conn.output, payload)
            }
        }
    }

    /** Reads frames from [conn] and passes each to [handler] until the connection closes or fails. */
    private fun startReaderLoop(conn: Conn, handler: suspend (ByteArray) -> Unit) {
        // Each loop serves only its own connection, so a late frame from a replaced one is never acted on as the current one
        scope.launch {
            try {
                while (isActive && !conn.closed) {
                    val frame = FrameCodec.readFrame(conn.input)
                    if (frame.isNotEmpty()) handler(frame)
                }
            } catch (e: Exception) {
                if (!conn.closed) onConnectionLost(conn, e)
            }
        }
    }

    /** Cleans up after an unexpected read failure and reports it: Disconnected for the remote, Error while pairing. */
    private fun onConnectionLost(conn: Conn, e: Exception) {
        if (e is java.net.SocketTimeoutException) {
            Log.w(TAG, "No data from the TV for ${(if (pingSeen) REMOTE_READ_TIMEOUT_MS else UNPINGED_READ_TIMEOUT_MS) / 1000}s, treating it as gone")
        }
        Log.e(TAG, "Connection lost in phase $phase", e)
        note("connection lost in phase $phase: ${e.javaClass.simpleName} ${e.message}")
        closeConn(conn)
        if (conn === remoteConn) {
            remoteConn = null
            _connectionState.value = ConnectionState.Disconnected
        } else {
            _connectionState.value = ConnectionState.Error(e.message ?: "Read failed")
        }
    }

    // ---------------- Polo pairing (port 6467) ----------------

    /**
     * Advances the pairing handshake. A non-OK status from the TV is reported as an error;
     * messages that arrive out of order for the current [phase] are ignored.
     */
    private suspend fun handlePoloFrame(conn: Conn, bytes: ByteArray) {
        if (conn !== pairingConn) return
        logFrame("Received", bytes)
        val message = try {
            OuterMessage.parseFrom(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Polo frame", e)
            return
        }

        if (message.status != OuterMessage.Status.STATUS_OK) {
            Log.e(TAG, "TV returned ${message.status} in phase $phase")
            if (message.status == OuterMessage.Status.STATUS_BAD_SECRET) {
                // The user can type the code again on the same connection
                phase = Phase.AWAIT_CODE
                _connectionState.value = ConnectionState.Error("Wrong pairing code")
            } else {
                // Retrying the same handshake can't help; end this attempt
                val reason = when (message.status) {
                    OuterMessage.Status.STATUS_BAD_CONFIGURATION -> "TV rejected pairing configuration"
                    else -> "TV rejected pairing (${message.status})"
                }
                closeConn(conn)
                pairingConn = null
                phase = Phase.IDLE
                _connectionState.value = ConnectionState.Error(reason)
            }
            return
        }

        when {
            message.hasPairingRequestAck() && phase == Phase.AWAIT_REQUEST_ACK -> {
                Log.d(TAG, "PairingRequestAck from '${message.pairingRequestAck.serverName}'")
                phase = Phase.AWAIT_OPTIONS
                send(RemoteMessageEncoder.encodeOptions(), conn)
            }
            message.hasOptions() && phase == Phase.AWAIT_OPTIONS -> {
                Log.d(TAG, "Server Options received, sending Configuration")
                phase = Phase.AWAIT_CONFIG_ACK
                send(RemoteMessageEncoder.encodeConfiguration(), conn)
            }
            message.hasConfigurationAck() && phase == Phase.AWAIT_CONFIG_ACK -> {
                Log.d(TAG, "ConfigurationAck received - TV should now display the pairing code")
                phase = Phase.AWAIT_CODE
                _connectionState.value = ConnectionState.AwaitingCode
            }
            message.hasSecretAck() && phase == Phase.AWAIT_SECRET_ACK -> {
                Log.d(TAG, "SecretAck received - pairing complete, opening remote session")
                phase = Phase.PAIRED
                closeConn(conn)
                pairingConn = null
                // A TV that is paired afresh may present a new certificate; learn it on the next remote connection
                host?.let { pinStore.forget(pinKey(it)) }
                scope.launch { startRemoteSession() }
            }
            else -> Log.w(TAG, "Unexpected Polo message in phase $phase")
        }
    }

    /**
     * Sends the proof of the code the user typed from the TV. A code that fails the local
     * checksum is reported as wrong without contacting the TV, and the user can retry.
     */
    suspend fun submitPairingCode(code: String) {
        if (phase != Phase.AWAIT_CODE) {
            Log.w(TAG, "Not waiting for a code, current phase: $phase")
            return
        }
        val conn = pairingConn ?: return
        val serverKey = serverCert?.publicKey as? RSAPublicKey
        if (serverKey == null) {
            _connectionState.value = ConnectionState.Error("TV certificate missing or not RSA")
            return
        }
        val clientKey = tls.clientCertificate.publicKey as RSAPublicKey

        val secret = computePairingSecret(code, clientKey, serverKey)
        if (secret == null) {
            Log.w(TAG, "Pairing code rejected locally (length, characters or checksum)")
            _connectionState.value = ConnectionState.Error("Wrong pairing code")
            return
        }
        phase = Phase.AWAIT_SECRET_ACK
        send(RemoteMessageEncoder.encodeSecret(secret), conn)
    }

    // ---------------- Remote control (port 6466) ----------------

    /** Called once pairing succeeds: opens the remote-control connection with the now-trusted certificate. */
    private suspend fun startRemoteSession() {
        val tvHost = host ?: return
        try {
            openRemoteConn(tvHost)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open remote session", e)
            _connectionState.value = ConnectionState.Error("Paired, but couldn't open remote session: ${e.message}")
        }
    }

    /** Handles the remote-control handshake (configure, set-active), ping replies and TV notices. */
    private suspend fun handleRemoteFrame(conn: Conn, bytes: ByteArray) {
        if (conn !== remoteConn) return
        logFrame("Received", bytes)
        val message = try {
            RemoteMessage.parseFrom(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse remote frame (${bytes.size} bytes)", e)
            return
        }

        when {
            message.hasRemotePingRequest() -> {
                if (!pingSeen) {
                    pingSeen = true
                    conn.socket.soTimeout = REMOTE_READ_TIMEOUT_MS
                    note("TV pings, using ${REMOTE_READ_TIMEOUT_MS / 1000}s silence timeout")
                }
                send(RemoteMessageEncoder.encodePingResponse(message.remotePingRequest.val1), conn)
            }
            message.hasRemoteConfigure() -> {
                val offered = message.remoteConfigure.code1
                activeFeatures = offered and SUPPORTED_FEATURES
                val info = message.remoteConfigure.deviceInfo
                val textOffered = activeFeatures and 4 != 0
                Log.d(TAG, "RemoteConfigure from TV: ${info.model} offers $offered, using $activeFeatures")
                note("TV ${info.vendor} ${info.model}, remote service ${info.appVersion}; offers $offered, using $activeFeatures, text input ${if (textOffered) "offered" else "NOT offered"}")
                send(RemoteMessageEncoder.encodeConfigure(activeFeatures), conn)
            }
            message.hasRemoteSetActive() -> {
                Log.d(TAG, "RemoteSetActive from TV: ${message.remoteSetActive.active}")
                send(RemoteMessageEncoder.encodeSetActive(activeFeatures), conn)
                _connectionState.value = ConnectionState.Connected
            }
            message.hasRemoteImeKeyInject() -> message.remoteImeKeyInject.let {
                // Sent when the foreground app changes (no field) and when a text field takes focus
                Log.d(TAG, "ImeKeyInject: app=${it.appInfo.appPackage} hasField=${it.hasTextFieldStatus()}")
                _foregroundApp.value = it.appInfo.appPackage
                note("ImeKeyInject app=${it.appInfo.appPackage} textField=${it.hasTextFieldStatus()}")
                if (it.hasTextFieldStatus()) updateIme(it.textFieldStatus, focused = true)
                else _imeField.value = null // the app changed or its text field closed
            }
            message.hasRemoteImeShowRequest() -> message.remoteImeShowRequest.let {
                // Also sent after every edit we make, with the field's full text, so it isn't a focus signal
                Log.d(TAG, "ImeShowRequest: field=${it.remoteTextFieldStatus.counterField} len=${it.remoteTextFieldStatus.value.length}")
                // Only the length: the field can hold a password or other private text
                note("ImeShowRequest (${it.remoteTextFieldStatus.value.length} chars)")
                updateIme(it.remoteTextFieldStatus, focused = false)
            }
            message.hasRemoteImeBatchEdit() -> message.remoteImeBatchEdit.let { edit ->
                Log.d(TAG, "ImeBatchEdit from TV: ime=${edit.imeCounter} field=${edit.fieldCounter}")
                note("ImeBatchEdit ime=${edit.imeCounter}")
                // Observed: ime=1 when a text field is activated on the TV, ime=0 when it is closed
                _imeField.update { old ->
                    (old ?: ImeField()).copy(
                        imeCounter = edit.imeCounter,
                        showRequests = (old?.showRequests ?: 0) + if (edit.imeCounter > 0) 1 else 0
                    )
                }
            }
            message.hasRemoteStart() -> Log.d(TAG, "RemoteStart: ${message.remoteStart.started}")
            message.hasRemoteError() -> Log.w(TAG, "RemoteError from TV: ${message.remoteError}")
            else -> Log.d(TAG, "Unhandled RemoteMessage (${bytes.size} bytes)")
        }
    }

    /** Records the TV's report of its focused text field; [focused] counts it as a new field taking focus. */
    private fun updateIme(
        status: com.prasoon.airmousetv.proto.remote.RemoteTextFieldStatus,
        focused: Boolean
    ) {
        _imeField.update { old ->
            (old ?: ImeField()).copy(
                label = status.label,
                value = status.value,
                showRequests = (old?.showRequests ?: 0) + if (focused) 1 else 0,
                reports = (old?.reports ?: 0) + 1,
                focuses = (old?.focuses ?: 0) + if (focused) 1 else 0
            )
        }
    }

    /** True when the TV has an open text input session that accepts text edits. */
    val hasTextSession: Boolean get() = _imeField.value?.acceptsText == true

    /**
     * Sends [text] through the TV's text input session, where it replaces the word at the cursor.
     * Returns false, sending nothing, if the TV has no open text input session to take it.
     */
    suspend fun sendText(text: String): Boolean {
        if (!hasTextSession) return false
        Log.d(TAG, "sendText len=${text.length}")
        note("sent text edit (${text.length} chars)")
        send(RemoteMessageEncoder.encodeTextEdit(text))
        return true
    }

    /**
     * Opens [link] on the TV. Returns false, sending nothing, if the TV didn't offer app links in
     * this session.
     */
    suspend fun launchLink(link: String): Boolean {
        if (activeFeatures and APP_LINK_FEATURE == 0) return false
        note("opening a link on the TV")
        send(RemoteMessageEncoder.encodeAppLink(link))
        return true
    }

    /**
     * Deletes the [count] characters before the cursor through the TV's text input session.
     * Returns false, sending nothing, if the TV has no open text input session.
     */
    suspend fun deleteText(count: Int): Boolean {
        if (!hasTextSession) return false
        Log.d(TAG, "deleteText count=$count")
        send(RemoteMessageEncoder.encodeTextDelete(count))
        return true
    }

    private fun logFrame(direction: String, bytes: ByteArray) {
        if (debuggable) {
            Log.d(TAG, "$direction ${bytes.size} bytes: ${bytes.joinToString("") { "%02x".format(it) }}")
        } else {
            Log.d(TAG, "$direction ${bytes.size} bytes")
        }
    }

    /**
     * Marks [conn] closed first, so the reader loop doesn't report the close as a failure, then
     * closes the socket on the IO scope: closing a TLS socket writes to the network, which must
     * not happen on the caller's (often the main) thread.
     */
    private fun closeConn(conn: Conn?) {
        if (conn == null || conn.closed) return
        conn.closed = true
        scope.launch {
            try {
                conn.socket.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error while closing socket", e)
            }
        }
    }

    /** Closes everything and reports Disconnected. */
    fun close() {
        Log.d(TAG, "Closing session")
        dropConnections()
        _foregroundApp.value = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
