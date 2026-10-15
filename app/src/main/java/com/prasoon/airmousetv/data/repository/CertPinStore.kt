package com.prasoon.airmousetv.data.repository

import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Remembers the certificate each TV presented on the remote port, so a different device answering
 * at the same address later is rejected. The first certificate seen is trusted (trust on first
 * use), which is right after pairing, when the TV has just proved itself with the code.
 */
class CertPinStore(private val prefs: SharedPreferences) {

    /** True if [fingerprint] is the one remembered for [key], or if nothing was remembered yet (it is then remembered). */
    @Synchronized
    fun verifyOrRemember(key: String, fingerprint: String): Boolean {
        val known = prefs.getString(key, null)
        if (known == null) {
            prefs.edit().putString(key, fingerprint).apply()
            return true
        }
        return known == fingerprint
    }

    /** Forgets the pin for [key], e.g. after pairing again, so the TV's current certificate is learned afresh. */
    @Synchronized
    fun forget(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        /** Lower-case hex SHA-256 of the certificate's DER encoding. */
        fun fingerprint(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }
}
