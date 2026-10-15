package com.prasoon.airmousetv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendlyNameTest {

    @Test
    fun json_friendlyName() {
        assertEquals("Living Room TV", """{"friendlyName":"Living Room TV","port":8009}""".findFriendlyName())
    }

    @Test
    fun json_friendlyNameWins_overNameAndDeviceName() {
        val body = """{"name":"other","deviceName":"dev","friendlyName":"Best"}"""
        assertEquals("Best", body.findFriendlyName())
    }

    @Test
    fun json_deviceNameWins_overGenericName() {
        assertEquals("dev", """{"name":"other","deviceName":"dev"}""".findFriendlyName())
    }

    @Test
    fun json_doesNotReachAcrossFields() {
        // A null value must not make the match jump to the next field's quoted value
        assertNull("""{"friendlyName": null, "model": "Bravia"}""".findFriendlyName())
    }

    @Test
    fun xml_friendlyName() {
        assertEquals("Bedroom TV", "<root><friendlyName>Bedroom TV</friendlyName></root>".findFriendlyName())
    }

    @Test
    fun xml_isCaseInsensitive() {
        assertEquals("Den", "<FriendlyName>Den</FriendlyName>".findFriendlyName())
    }

    @Test
    fun header_valueEndsAtTheEndOfTheLine() {
        val response = "HTTP/1.1 200 OK\r\nFRIENDLY: Kitchen TV\r\nContent-Length: 5\r\n\r\nhello"
        assertEquals("Kitchen TV", response.findFriendlyName())
    }

    @Test
    fun unknownPlaceholder_isIgnoredInAnyCase() {
        assertNull("FRIENDLY: UNKNOWN\r\n".findFriendlyName())
        assertNull("""{"friendlyName":"unknown"}""".findFriendlyName())
    }

    @Test
    fun noName_returnsNull() {
        assertNull("HTTP/1.1 404 Not Found\r\n\r\n".findFriendlyName())
        assertNull("".findFriendlyName())
    }

    @Test
    fun namesFromTheNetwork_loseControlCharacters_andAreCapped() {
        assertEquals("BadName", "{\"friendlyName\":\"Bad\u0007Name\"}".findFriendlyName())

        val long = "x".repeat(500)
        assertEquals(64, """{"friendlyName":"$long"}""".findFriendlyName()!!.length)
    }

    @Test
    fun cleanDeviceName_trimsAndCaps() {
        assertEquals("TV", "  TV\n".cleanDeviceName())
        assertEquals(64, "y".repeat(100).cleanDeviceName().length)
    }
}
