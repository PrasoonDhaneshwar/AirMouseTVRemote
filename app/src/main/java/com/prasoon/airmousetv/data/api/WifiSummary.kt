package com.prasoon.airmousetv.data.api

/** Frequencies below this are the 2.4 GHz band; Wi-Fi 6E's 6 GHz band starts at 5925 MHz. */
private const val FIRST_5_GHZ_MHZ = 4900
private const val FIRST_6_GHZ_MHZ = 5925
/** What Android reports as the signal strength when it has none (WifiInfo.INVALID_RSSI). */
private const val INVALID_RSSI_DBM = -127

/**
 * One line describing the phone's Wi-Fi link, for the diagnostics report. Values the system doesn't
 * report are left out. Numbers only: the network name needs the location permission, and a failed
 * connection is easier to read next to the band and signal than next to a name.
 */
fun wifiSummary(frequencyMhz: Int?, rssiDbm: Int?, linkSpeedMbps: Int?): String {
    val band = when {
        frequencyMhz == null || frequencyMhz <= 0 -> null
        frequencyMhz >= FIRST_6_GHZ_MHZ -> "6 GHz"
        frequencyMhz >= FIRST_5_GHZ_MHZ -> "5 GHz"
        else -> "2.4 GHz"
    }
    val parts = listOfNotNull(
        band?.let { "$it ($frequencyMhz MHz)" },
        rssiDbm?.takeIf { it < 0 && it > INVALID_RSSI_DBM }?.let { "$it dBm" },
        linkSpeedMbps?.takeIf { it > 0 }?.let { "$it Mbps" }
    )
    return if (parts.isEmpty()) "Wi-Fi state unknown" else "Wi-Fi " + parts.joinToString(", ")
}
