package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.NearbyDevice

/**
 * The audio/video device classes a TV or a TV stick announces itself as: set-top box, video
 * monitor, and display with loudspeaker. Headphones and speakers are audio/video devices too, with
 * other values in these bits, which is why the major class alone isn't enough.
 */
private val TV_DEVICE_CLASSES = setOf(0x0424, 0x0438, 0x043C)

/** The bits of a class-of-device number that say what kind of device it is, without the service bits above them. */
private const val DEVICE_CLASS_MASK = 0x1FFC

/** True if a Bluetooth class-of-device number is one a TV announces itself with. */
fun isTvClass(classOfDevice: Int): Boolean = (classOfDevice and DEVICE_CLASS_MASK) in TV_DEVICE_CLASSES

/** A name with case, spaces and punctuation taken out, so "Living-Room TV" and "living room tv" compare equal. */
private fun String.plain() = filter { it.isLetterOrDigit() }.lowercase()

/**
 * True if the Bluetooth device called [deviceName] goes by one of [tvNames], the names the TV has
 * on the network. Most TVs use one name for both, which is the only thing that ties the TV found
 * over Bluetooth to the one the remote is connected to: the two protocols share no address.
 */
fun matchesTvName(deviceName: String?, tvNames: List<String>): Boolean {
    val plain = deviceName?.plain()?.takeIf { it.isNotEmpty() } ?: return false
    return tvNames.any { it.plain() == plain }
}

/**
 * The devices found nearby that could be the TV, likeliest first: those carrying the TV's name,
 * then others that announce themselves as a TV. Everything else found in a scan (phones, laptops,
 * headphones, nameless beacons) is left out, so the user is never offered a device the pointer
 * shouldn't drive.
 */
fun tvCandidates(found: List<NearbyDevice>, tvNames: List<String>): List<NearbyDevice> {
    val (named, others) = found.partition { matchesTvName(it.name, tvNames) }
    return named + others.filter { it.looksLikeTv && !it.name.isNullOrBlank() }
}

/**
 * The device to pair with without asking: the one found nearby that carries the TV's name, if there
 * is exactly one. Two TVs of the same name (two of one model, both left on the factory name) can't
 * be told apart, so then the user picks.
 */
fun tvToPairUnasked(found: List<NearbyDevice>, tvNames: List<String>): NearbyDevice? =
    found.filter { matchesTvName(it.name, tvNames) }.singleOrNull()
