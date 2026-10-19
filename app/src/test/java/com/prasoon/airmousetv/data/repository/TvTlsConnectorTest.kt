package com.prasoon.airmousetv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TvTlsConnectorTest {

    @get:Rule
    val dir = TemporaryFolder()

    @Test
    fun clientCertificate_isKeptAcrossRestarts() {
        // The TV only accepts the certificate it paired with, so a restart must not make a new one
        val first = TvTlsConnector(dir.root).clientCertificate

        assertEquals(first, TvTlsConnector(dir.root).clientCertificate)
    }

    @Test
    fun unreadableKeystore_isReplacedWithANewCertificate() {
        val first = TvTlsConnector(dir.root).clientCertificate
        File(dir.root, "atv_client.p12").writeText("not a keystore")

        val second = TvTlsConnector(dir.root).clientCertificate

        assertNotEquals(first, second)
        assertEquals(second, TvTlsConnector(dir.root).clientCertificate)
    }
}
