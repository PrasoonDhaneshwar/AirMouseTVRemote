package com.prasoon.airmousetv.data.model

data class DiscoveredTv(
    val name: String,
    val friendlyName: String? = null,
    val host: String,
    val port: Int
) {
    val displayName: String get() = friendlyName ?: run {
        // Upper-case first: filtering to A-Z would otherwise throw away every letter of a lower-case name
        val model = name.split("-").first()
            .uppercase()
            .replace(Regex("[^A-Z0-9]"), "")
            .take(8)
        if (model.isEmpty()) "TV" else "$model TV"
    }
}