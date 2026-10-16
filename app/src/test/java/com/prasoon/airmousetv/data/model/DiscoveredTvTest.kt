package com.prasoon.airmousetv.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveredTvTest {

    private fun tv(name: String, friendlyName: String? = null) =
        DiscoveredTv(name = name, friendlyName = friendlyName, host = "192.168.1.5", port = 6466)

    @Test
    fun theFriendlyName_isUsedWhenThereIsOne() {
        assertEquals("Living Room TV", tv("bravia-4k", "Living Room TV").displayName)
    }

    @Test
    fun withoutAFriendlyName_theModelPartOfTheNameIsShown() {
        assertEquals("BRAVIA TV", tv("BRAVIA-4K-GB").displayName)
    }

    @Test
    fun aLowerCaseName_isUpperCasedRatherThanEmptied() {
        assertEquals("LIVING TV", tv("living-room-tv").displayName)
    }

    @Test
    fun theModelIsCutToEightCharacters() {
        assertEquals("ABCDEFGH TV", tv("abcdefghij-1").displayName)
    }

    @Test
    fun aNameWithNothingUsable_isJustTv() {
        assertEquals("TV", tv("???-1").displayName)
    }
}
