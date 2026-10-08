package com.prasoon.airmousetv.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.prasoon.airmousetv.data.api.FrameCodec
import com.prasoon.airmousetv.data.api.PairingPayloadFactory
import com.prasoon.airmousetv.data.api.proto.RemoteMessageEncoder
import com.prasoon.airmousetv.data.model.ConnectionState
import com.prasoon.airmousetv.proto.polo.OuterMessage
import com.prasoon.airmousetv.proto.remote.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

private const val TAG = "RemoteSession"
/** Polo pairing port on the TV. */
private const val PAIRING_PORT = 6467
/** Remote-control port on the TV; only accepts clients it has paired with. */
private const val REMOTE_PORT = 6466
/** Feature bitmask advertised in RemoteConfigure/RemoteSetActive, copied from other clients. */
private const val REMOTE_FEATURES = 622
/** How long to wait for an already-paired TV to activate the session before pairing instead. */
private const val TRUSTED_CHECK_TIMEOUT_MS = 3000L
/** Alias of the client key/certificate inside the PKCS12 file. */
private const val CERT_ALIAS = "androidtv-remote"
/** App-private file holding the client certificate; the TV pairs with this exact certificate. */
private const val KEYSTORE_FILE = "atv_client.p12"
/** Protects only the app-private keystore file, so it is not a secret worth rotating. */
private val KEYSTORE_PASSWORD = "atv-remote".toCharArray()

/**
 * Polo pairing over TLS on port 6467:
 *   C PairingRequest -> S PairingRequestAck
 *   C Options        -> S Options
 *   C Configuration  -> S ConfigurationAck   (TV now shows the 6-hex-digit code)
 *   C Secret         -> S SecretAck          (paired; TV remembers our client cert)
 *
 * Then a second TLS connection on port 6466 carries the remote-control protocol:
 *   S RemoteConfigure -> C RemoteConfigure, S RemoteSetActive -> C RemoteSetActive(622),
 *   then pings are answered and RemoteKeyInject messages are sent.
 */
class RemoteSessionManager(private val context: Context) {

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

    /** Our certificate and private key, loaded or created on first use. */
    private val clientIdentity: Pair<X509Certificate, PrivateKey> by lazy { loadOrCreateClientIdentity() }

    /**
     * Connects to the TV. If the TV already trusts our saved certificate the remote session on
     * 6466 comes up immediately; otherwise it drops us and we fall back to Polo pairing.
     */
    suspend fun connect(host: String, clientName: String = "AirMouseTV") {
        closeConn(pairingConn)
        closeConn(remoteConn)
        pairingConn = null
        remoteConn = null
        phase = Phase.IDLE
        this.host = host
        _connectionState.value = ConnectionState.Idle

        if (tryRemoteSession(host)) return
        startPairing(host, clientName)
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
        _connectionState.value = ConnectionState.TcpConnected

        phase = Phase.AWAIT_REQUEST_ACK
        send(PairingPayloadFactory.createPairingRequest(clientName), conn)
        _connectionState.value = ConnectionState.PairingRequested

        startReaderLoop(conn, ::handlePoloFrame)
    }

    /** Connects to the remote-control port and starts reading from it. */
    private suspend fun openRemoteConn(host: String): Conn {
        val conn = openTlsSocket(host, REMOTE_PORT)
        remoteConn = conn
        startReaderLoop(conn, ::handleRemoteFrame)
        return conn
    }

    /**
     * Opens a TLS connection to [host]:[port], offering our client certificate.
     * Runs on the IO dispatcher; the socket is closed if connecting or the handshake fails.
     */
    private suspend fun openTlsSocket(host: String, port: Int): Conn =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to $host:$port with TLS")
            val (clientCert, privateKey) = clientIdentity

            val socket = createSslContext(clientCert, privateKey).socketFactory.createSocket() as SSLSocket
            socket.useClientMode = true
            socket.keepAlive = true
            socket.tcpNoDelay = true
            try {
                socket.connect(InetSocketAddress(host, port), 10000)
                socket.soTimeout = 0
                socket.startHandshake()
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }

            val session = socket.session
            Log.d(TAG, "TLS handshake complete on $port: ${session.protocol} ${session.cipherSuite}")
            Log.d(TAG, "Local certificates count: ${session.localCertificates?.size ?: 0}")

            Conn(
                socket,
                DataInputStream(BufferedInputStream(socket.getInputStream())),
                DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            )
        }

    // The TV pairs with this exact certificate, so it must survive app restarts
    /**
     * Loads our certificate from [KEYSTORE_FILE], or creates a 2048-bit RSA self-signed one
     * (valid 10 years) and saves it. It must persist: the TV only accepts the certificate it paired with.
     */
    private fun loadOrCreateClientIdentity(): Pair<X509Certificate, PrivateKey> {
        val file = File(context.filesDir, KEYSTORE_FILE)
        val keyStore = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            try {
                file.inputStream().use { keyStore.load(it, KEYSTORE_PASSWORD) }
                val cert = keyStore.getCertificate(CERT_ALIAS) as X509Certificate
                val key = keyStore.getKey(CERT_ALIAS, KEYSTORE_PASSWORD) as PrivateKey
                Log.d(TAG, "Loaded persisted client certificate")
                return cert to key
            } catch (e: Exception) {
                Log.w(TAG, "Stored client certificate unreadable, regenerating", e)
            }
        }

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        val now = Date()
        val subject = X500Name("CN=atvremote/AirMouseTV")
        val holder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(System.currentTimeMillis()),
            now,
            Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000),
            subject,
            keyPair.public
        ).build(JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)

        keyStore.load(null, null)
        keyStore.setKeyEntry(CERT_ALIAS, keyPair.private, KEYSTORE_PASSWORD, arrayOf(cert))
        file.outputStream().use { keyStore.store(it, KEYSTORE_PASSWORD) }
        Log.d(TAG, "Generated and persisted new client certificate")
        return cert to keyPair.private
    }

    /** Builds a TLS context that presents [clientCert] and accepts the TV's self-signed certificate. */
    private fun createSslContext(clientCert: X509Certificate, privateKey: PrivateKey): SSLContext {
        // The TV uses a self-signed cert; trust is established by the pairing code instead
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        // Always offer our cert, even if the TV's CertificateRequest lists issuers we don't match
        val keyManager = object : X509ExtendedKeyManager() {
            override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?) = CERT_ALIAS
            override fun chooseEngineClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, engine: SSLEngine?) = CERT_ALIAS
            override fun getClientAliases(keyType: String?, issuers: Array<Principal>?) = arrayOf(CERT_ALIAS)
            override fun getCertificateChain(alias: String?) = arrayOf(clientCert)
            override fun getPrivateKey(alias: String?) = privateKey
            override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? = null
        }

        return SSLContext.getInstance("TLS").apply {
            init(arrayOf(keyManager), arrayOf(trustAll), SecureRandom())
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
        Log.e(TAG, "Connection lost in phase $phase", e)
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
    private suspend fun handlePoloFrame(bytes: ByteArray) {
        logFrame("Received", bytes)
        val message = try {
            OuterMessage.parseFrom(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Polo frame", e)
            return
        }
        val conn = pairingConn ?: return

        if (message.status != OuterMessage.Status.STATUS_OK) {
            Log.e(TAG, "TV returned ${message.status} in phase $phase")
            val reason = when (message.status) {
                OuterMessage.Status.STATUS_BAD_SECRET -> "Wrong pairing code"
                OuterMessage.Status.STATUS_BAD_CONFIGURATION -> "TV rejected pairing configuration"
                else -> "TV rejected pairing (${message.status})"
            }
            _connectionState.value = ConnectionState.Error(reason)
            return
        }

        when {
            message.hasPairingRequestAck() && phase == Phase.AWAIT_REQUEST_ACK -> {
                Log.d(TAG, "PairingRequestAck from '${message.pairingRequestAck.serverName}'")
                phase = Phase.AWAIT_OPTIONS
                send(PairingPayloadFactory.createOptions(), conn)
            }
            message.hasOptions() && phase == Phase.AWAIT_OPTIONS -> {
                Log.d(TAG, "Server Options received, sending Configuration")
                phase = Phase.AWAIT_CONFIG_ACK
                send(PairingPayloadFactory.createConfiguration(), conn)
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
        val clientKey = clientIdentity.first.publicKey as RSAPublicKey

        val secret = computeSecret(code.uppercase(), clientKey, serverKey)
        if (secret == null) {
            _connectionState.value = ConnectionState.Error("Wrong pairing code")
            return
        }
        phase = Phase.AWAIT_SECRET_ACK
        send(PairingPayloadFactory.createSecret(secret), conn)
    }

    /**
     * SHA-256(client modulus, client exponent, server modulus, server exponent, code[2..6] as bytes).
     * The first byte of the hash must equal code[0..2], which lets us reject typos locally.
     */
    private fun computeSecret(code: String, client: RSAPublicKey, server: RSAPublicKey): ByteArray? {
        if (code.length != 6 || !code.all { it in '0'..'9' || it in 'A'..'F' }) return null
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(client.modulus.unsignedBytes())
            update(client.publicExponent.unsignedBytes())
            update(server.modulus.unsignedBytes())
            update(server.publicExponent.unsignedBytes())
            update(code.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            digest()
        }
        if (digest[0] != code.substring(0, 2).toInt(16).toByte()) {
            Log.w(TAG, "Code checksum mismatch")
            return null
        }
        return digest
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
    private suspend fun handleRemoteFrame(bytes: ByteArray) {
        val conn = remoteConn ?: return
        val message = try {
            RemoteMessage.parseFrom(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse remote frame (${bytes.size} bytes)", e)
            return
        }

        when {
            message.hasRemotePingRequest() ->
                send(RemoteMessageEncoder.encodePingResponse(message.remotePingRequest.val1), conn)
            message.hasRemoteConfigure() -> {
                Log.d(TAG, "RemoteConfigure from TV: ${message.remoteConfigure.deviceInfo.model}")
                send(RemoteMessageEncoder.encodeConfigure(), conn)
            }
            message.hasRemoteSetActive() -> {
                Log.d(TAG, "RemoteSetActive from TV: ${message.remoteSetActive.active}")
                send(RemoteMessageEncoder.encodeSetActive(REMOTE_FEATURES), conn)
                _connectionState.value = ConnectionState.Connected
            }
            message.hasRemoteStart() -> Log.d(TAG, "RemoteStart: ${message.remoteStart.started}")
            message.hasRemoteError() -> Log.w(TAG, "RemoteError from TV: ${message.remoteError}")
            else -> Log.d(TAG, "Unhandled RemoteMessage (${bytes.size} bytes)")
        }
    }

    /** Big-endian bytes without the sign byte Java adds, which is what the secret's hash is computed over. */
    private fun BigInteger.unsignedBytes(): ByteArray =
        toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    private fun logFrame(direction: String, bytes: ByteArray) {
        if (debuggable) {
            Log.d(TAG, "$direction ${bytes.size} bytes: ${bytes.joinToString("") { "%02x".format(it) }}")
        } else {
            Log.d(TAG, "$direction ${bytes.size} bytes")
        }
    }

    /** Marks [conn] closed first, then closes the socket, so the reader loop doesn't report it as a failure. */
    private fun closeConn(conn: Conn?) {
        if (conn == null || conn.closed) return
        conn.closed = true
        try {
            conn.socket.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error while closing socket", e)
        }
    }

    /** Closes everything and reports Disconnected. */
    fun close() {
        Log.d(TAG, "Closing session")
        closeConn(pairingConn)
        closeConn(remoteConn)
        pairingConn = null
        remoteConn = null
        phase = Phase.IDLE
        _connectionState.value = ConnectionState.Disconnected
    }
}
