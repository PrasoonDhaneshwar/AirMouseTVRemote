package com.prasoon.airmousetv.data.api

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * The Polo pairing secret: SHA-256(client modulus, client exponent, server modulus, server
 * exponent, code[2..6] as bytes). Hashing both public keys binds the code to this exact pair of
 * certificates.
 *
 * The first byte of the hash must equal code[0..2], which lets a typo be rejected on the phone.
 * Returns null for a code that is not 6 hex digits or fails that check.
 */
internal fun computePairingSecret(code: String, client: RSAPublicKey, server: RSAPublicKey): ByteArray? {
    val hex = code.uppercase()
    if (hex.length != PAIRING_CODE_LENGTH || !hex.all(::isPairingCodeChar)) return null
    val digest = MessageDigest.getInstance("SHA-256").run {
        update(client.modulus.unsignedBytes())
        update(client.publicExponent.unsignedBytes())
        update(server.modulus.unsignedBytes())
        update(server.publicExponent.unsignedBytes())
        update(hex.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        digest()
    }
    if (digest[0] != hex.substring(0, 2).toInt(16).toByte()) return null
    return digest
}

/** Big-endian bytes without the sign byte Java adds, which is what the secret's hash is computed over. */
internal fun BigInteger.unsignedBytes(): ByteArray =
    toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
