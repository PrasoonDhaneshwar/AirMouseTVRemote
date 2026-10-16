package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {

    @Test
    fun aValidCode_isKept() {
        assertEquals("4F2A9C", normalizePairingCode("4F2A9C"))
    }

    @Test
    fun lowerCase_becomesUpperCase() {
        assertEquals("4F2A9C", normalizePairingCode("4f2a9c"))
    }

    @Test
    fun charactersThatAreNotHex_areDropped() {
        assertEquals("128230", normalizePairingCode("12 82-30"))
        // The G is not a hex digit and goes too, so only five characters are left
        assertEquals("ABCDE", normalizePairingCode("AB!CD?EG"))
        assertEquals("ABCDEF", normalizePairingCode("AB!CD?E-F G"))
    }

    @Test
    fun theCode_isCutToSixCharacters() {
        assertEquals("123456", normalizePairingCode("1234567890"))
    }

    @Test
    fun emptyInput_givesAnEmptyCode() {
        assertEquals("", normalizePairingCode(""))
        assertEquals("", normalizePairingCode("xyz"))
    }

    @Test
    fun otherScriptDigits_areNotAccepted() {
        // Arabic-Indic and full-width digits look like digits but are not 0-9
        assertEquals("", normalizePairingCode("٣１"))
    }

    @Test
    fun normalizing_isIdempotent() {
        val once = normalizePairingCode("a1-b2 c3d4e5")
        assertEquals(once, normalizePairingCode(once))
    }

    @Test
    fun theCodeCharacters_areExactlyZeroToNineAndAToF() {
        assertTrue("0123456789ABCDEF".all(::isPairingCodeChar))
        assertFalse(isPairingCodeChar('a'))
        assertFalse(isPairingCodeChar('G'))
        assertEquals(6, PAIRING_CODE_LENGTH)
    }
}
