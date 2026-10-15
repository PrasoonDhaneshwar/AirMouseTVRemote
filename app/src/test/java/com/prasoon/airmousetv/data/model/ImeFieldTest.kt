package com.prasoon.airmousetv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImeFieldTest {

    @Test
    fun matchingReport_isNoMismatch() {
        val field = ImeField(value = "hello", reports = 3)
        assertNull(field.reportedMismatch(expected = "hello", reportsBefore = 2))
    }

    @Test
    fun differentReport_returnsTheTvsText() {
        val field = ImeField(value = "hellohello", reports = 3)
        assertEquals("hellohello", field.reportedMismatch(expected = "hello", reportsBefore = 2))
    }

    @Test
    fun noReportSinceTheEdit_cannotBeCompared() {
        // The value is old: the TV said nothing after our edit, so it must not be taken as the TV's text
        val field = ImeField(value = "old", reports = 2)
        assertNull(field.reportedMismatch(expected = "new", reportsBefore = 2))
    }

    @Test
    fun noFieldAtAll_cannotBeCompared() {
        val field: ImeField? = null
        assertNull(field.reportedMismatch(expected = "x", reportsBefore = 0))
    }

    @Test
    fun emptyReportAgainstText_isAMismatch() {
        val field = ImeField(value = "", reports = 1)
        assertEquals("", field.reportedMismatch(expected = "abc", reportsBefore = 0))
    }

    @Test
    fun newlyReportedField_countsAsReportedFromTheStart() {
        val field = ImeField(value = "abc", reports = 1)
        assertNull(field.reportedMismatch(expected = "abc", reportsBefore = 0))
    }
}
