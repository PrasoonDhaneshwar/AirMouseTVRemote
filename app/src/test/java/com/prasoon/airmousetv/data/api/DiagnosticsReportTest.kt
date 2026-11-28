package com.prasoon.airmousetv.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private val header = DiagnosticsHeader(
        appVersion = "3.0", appBuild = 7, phone = "Google Pixel 8", android = "15",
        tv = "Hisense 65A6K at 192.168.1.20, remote service 5.2"
    )

    @Test
    fun `puts the versions above the events`() {
        assertEquals(
            "App 3.0 (7)\nPhone: Google Pixel 8, Android 15\nTV: Hisense 65A6K at 192.168.1.20, remote service 5.2\n\n14:02:11 TV pings",
            diagnosticsReport(header, "14:02:11 TV pings")
        )
    }

    @Test
    fun `says when no TV has connected`() {
        assertEquals(
            "App 3.0 (7)\nPhone: Google Pixel 8, Android 15\nTV: not connected yet",
            diagnosticsReport(header.copy(tv = null), "")
        )
    }

    @Test
    fun `the email leaves room for the user above the report`() {
        val body = diagnosticsEmailBody("App 3.0 (7)")
        assertTrue(body.startsWith("What happened?"))
        assertTrue(body.endsWith("\n\nApp 3.0 (7)"))
    }

    @Test
    fun `the subject names the version`() {
        assertEquals("Air Mouse TV Remote 3.0 diagnostics", diagnosticsSubject("3.0"))
    }
}
