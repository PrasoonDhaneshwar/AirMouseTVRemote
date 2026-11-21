package com.prasoon.airmousetv.airmouse.api

/**
 * The names a TV's air mouse link is saved and looked up under, most telling first: its network
 * name, the name it shows to people, then its address.
 *
 * One name isn't enough, because the remote doesn't always know a TV by the same one. A TV added by
 * its address is known by that address alone; the same TV found by discovery after a restart is
 * known by its long network name (seen on a Hisense Android TV 9: "HiSmartTV-A4-4a58…"), and the
 * link saved under the address was no longer found. The address comes last because it is the one
 * that can change hands: a new TV given an old TV's address would otherwise inherit its link.
 */
fun tvLinkKeys(name: String, friendlyName: String?, host: String): List<String> =
    listOfNotNull(name, friendlyName, host).map(String::trim).filter(String::isNotEmpty).distinct()

/** The Bluetooth address saved under the first of [keys] that has one, looked up with [saved]; null if none has. */
fun linkedAddress(keys: List<String>, saved: (String) -> String?): String? = keys.firstNotNullOfOrNull(saved)
