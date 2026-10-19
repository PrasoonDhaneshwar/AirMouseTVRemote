package com.prasoon.airmousetv.data.repository

import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

private const val TAG = "TvTls"
/** TCP connect timeout; a TV on the same LAN answers (or refuses) well within this. */
private const val CONNECT_TIMEOUT_MS = 4000
/** Alias of the client key/certificate inside the PKCS12 file. */
private const val CERT_ALIAS = "androidtv-remote"
/** App-private file holding the client certificate; the TV pairs with this exact certificate. */
private const val KEYSTORE_FILE = "atv_client.p12"
/** Protects only the app-private keystore file, so it is not a secret worth rotating. */
private val KEYSTORE_PASSWORD = "atv-remote".toCharArray()

/** The TV's address didn't accept a connection: it is off, still booting, or has a new IP. */
class TvUnreachableException(host: String, cause: Throwable) : Exception(
    "TV not reachable at $host. Make sure it is on and on the same Wi-Fi, and that a VPN isn't blocking local network access.", cause
)

/**
 * Opens TLS connections to the TV that present our client certificate, which is kept in
 * [KEYSTORE_FILE] under [filesDir]. Knows nothing about the protocols spoken over them.
 */
internal class TvTlsConnector(private val filesDir: File) {

    /** Our certificate and private key, loaded or created on first use. */
    private val clientIdentity: Pair<X509Certificate, PrivateKey> by lazy { loadOrCreateClientIdentity() }

    /** The certificate the TV pairs with; its public key goes into the pairing secret. */
    val clientCertificate: X509Certificate get() = clientIdentity.first

    /**
     * Connects to [host]:[port] and completes the TLS handshake, blocking the calling thread.
     * [checkServer] sees the TV's certificate chain and throws to refuse it. The socket is closed
     * if connecting or the handshake fails.
     */
    fun open(host: String, port: Int, checkServer: (Array<X509Certificate>?) -> Unit): SSLSocket {
        Log.d(TAG, "Connecting to $host:$port with TLS")
        val (clientCert, privateKey) = clientIdentity
        val socket = createSslContext(clientCert, privateKey, checkServer).socketFactory.createSocket() as SSLSocket
        socket.useClientMode = true
        socket.keepAlive = true
        socket.tcpNoDelay = true
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = 0
            socket.startHandshake()
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) {
                TvUnreachableException(host, e)
            } else e
        }

        val session = socket.session
        Log.d(TAG, "TLS handshake complete on $port: ${session.protocol} ${session.cipherSuite}")
        Log.d(TAG, "Local certificates count: ${session.localCertificates?.size ?: 0}")
        return socket
    }

    /**
     * Loads our certificate from [KEYSTORE_FILE], or creates a 2048-bit RSA self-signed one
     * (valid 10 years) and saves it. It must persist: the TV only accepts the certificate it paired with.
     */
    private fun loadOrCreateClientIdentity(): Pair<X509Certificate, PrivateKey> {
        val file = File(filesDir, KEYSTORE_FILE)
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

    /**
     * Builds a TLS context that presents [clientCert] and accepts the TV's self-signed certificate,
     * after [checkServer] has looked at it (it throws to refuse the connection).
     */
    private fun createSslContext(
        clientCert: X509Certificate,
        privateKey: PrivateKey,
        checkServer: (Array<X509Certificate>?) -> Unit
    ): SSLContext {
        // The TV uses a self-signed cert, so there is no CA chain to validate; callers pin it instead
        val serverTrust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = checkServer(chain)
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
            init(arrayOf(keyManager), arrayOf(serverTrust), SecureRandom())
        }
    }
}
