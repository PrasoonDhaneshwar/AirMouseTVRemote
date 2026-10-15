package com.prasoon.airmousetv.data.repository

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertPinStoreTest {

    private val store = CertPinStore(FakePrefs())

    @Test
    fun firstCertificateSeen_isTrustedAndRemembered() {
        assertTrue(store.verifyOrRemember("tv:6466", "aa"))
        assertTrue(store.verifyOrRemember("tv:6466", "aa"))
    }

    @Test
    fun differentCertificate_isRefused() {
        store.verifyOrRemember("tv:6466", "aa")

        assertFalse(store.verifyOrRemember("tv:6466", "bb"))
        // The refused one must not replace the pin
        assertTrue(store.verifyOrRemember("tv:6466", "aa"))
    }

    @Test
    fun pinsAreKeptPerTv() {
        store.verifyOrRemember("tv1:6466", "aa")

        assertTrue(store.verifyOrRemember("tv2:6466", "bb"))
        assertFalse(store.verifyOrRemember("tv1:6466", "bb"))
    }

    @Test
    fun forgetting_letsTheNextCertificateBeLearned() {
        store.verifyOrRemember("tv:6466", "aa")
        store.forget("tv:6466")

        assertTrue(store.verifyOrRemember("tv:6466", "bb"))
        assertFalse(store.verifyOrRemember("tv:6466", "aa"))
    }
}

/** In-memory stand-in: only the calls CertPinStore makes do anything. */
private class FakePrefs : SharedPreferences {
    private val data = mutableMapOf<String, String>()

    override fun getString(key: String?, defValue: String?): String? = data[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        override fun putString(key: String, value: String?) = also { pending[key] = value }
        override fun remove(key: String) = also { pending[key] = null }
        override fun apply() {
            pending.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
        }
        override fun commit(): Boolean { apply(); return true }

        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun clear() = also { data.keys.forEach { pending[it] = null } }
    }

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int) = defValue
    override fun getLong(key: String?, defValue: Long) = defValue
    override fun getFloat(key: String?, defValue: Float) = defValue
    override fun getBoolean(key: String?, defValue: Boolean) = defValue
    override fun contains(key: String?) = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
