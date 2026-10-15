package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class PairingSecretTest {

    companion object {
        private lateinit var client: RSAPublicKey
        private lateinit var server: RSAPublicKey

        @BeforeClass
        @JvmStatic
        fun generateKeys() {
            val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            client = generator.generateKeyPair().public as RSAPublicKey
            server = generator.generateKeyPair().public as RSAPublicKey
        }
    }

    /** The hash the protocol specifies, written out independently of the code under test. */
    private fun expectedDigest(lastFourHex: String): ByteArray {
        fun BigInteger.stripped() = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return MessageDigest.getInstance("SHA-256").run {
            update(client.modulus.stripped())
            update(client.publicExponent.stripped())
            update(server.modulus.stripped())
            update(server.publicExponent.stripped())
            update(lastFourHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            digest()
        }
    }

    /** A code the TV could show for these keys: the first byte of the hash, then the four digits it covers. */
    private fun validCode(lastFourHex: String = "A1B2"): String =
        "%02X".format(expectedDigest(lastFourHex)[0].toInt() and 0xFF) + lastFourHex

    @Test
    fun validCode_givesTheSpecifiedHash() {
        val secret = computePairingSecret(validCode(), client, server)

        assertNotNull(secret)
        assertArrayEquals(expectedDigest("A1B2"), secret)
    }

    @Test
    fun lowerCaseCode_isAccepted() {
        assertNotNull(computePairingSecret(validCode().lowercase(), client, server))
    }

    @Test
    fun differentCodeDigits_giveADifferentHash() {
        val first = computePairingSecret(validCode("A1B2"), client, server)
        val second = computePairingSecret(validCode("0000"), client, server)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(false, first!!.contentEquals(second!!))
    }

    @Test
    fun swappedKeys_giveADifferentHash() {
        // Hashing both keys in order binds the code to this client and this TV
        val normal = computePairingSecret(validCode(), client, server)
        val swapped = computePairingSecret(validCode(), server, client)

        assertNotNull(normal)
        // The checksum byte is for the normal order, so the swapped one fails it (or at least differs)
        if (swapped != null) assertEquals(false, normal!!.contentEquals(swapped))
    }

    @Test
    fun wrongChecksumByte_isRejected() {
        val right = validCode()
        val wrongFirstByte = "%02X".format((right.take(2).toInt(16) + 1) and 0xFF) + right.drop(2)

        assertNull(computePairingSecret(wrongFirstByte, client, server))
    }

    @Test
    fun malformedCodes_areRejected() {
        assertNull(computePairingSecret("", client, server))
        assertNull(computePairingSecret("ABCDE", client, server))
        assertNull(computePairingSecret("ABCDEFA", client, server))
        assertNull(computePairingSecret("ABCDEG", client, server))
        assertNull(computePairingSecret("12 456", client, server))
    }

    @Test
    fun unsignedBytes_dropsTheSignByteOfAHighBitNumber() {
        // A 2048-bit modulus has its top bit set, so BigInteger adds a leading 0x00
        assertEquals(257, client.modulus.toByteArray().size)
        assertEquals(256, client.modulus.unsignedBytes().size)
        assertEquals(0x00.toByte(), client.modulus.toByteArray()[0])
    }

    @Test
    fun unsignedBytes_keepsSmallNumbersAndZero() {
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x01), BigInteger.valueOf(65537).unsignedBytes())
        assertArrayEquals(byteArrayOf(0), BigInteger.ZERO.unsignedBytes())
    }
}
