package com.prasoon.airmousetv.data.model

data class DiscoveredTv(
    val name: String,
    val friendlyName: String? = null,
    val host: String,
    val port: Int,
    val macAddress: String? = null,
    val serviceType: String = ""
) {
    val displayName: String get() = friendlyName ?: run {
        val model = name.split("-").first()
            .replace(Regex("[^A-Z0-9]"), "")
            .take(8)
            .uppercase()
        "$model TV"
    }
}