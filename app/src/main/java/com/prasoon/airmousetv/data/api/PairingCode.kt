package com.prasoon.airmousetv.data.api

/** A pairing code is six hex digits: the TV shows it, the user enters it, and it goes into the secret. */
const val PAIRING_CODE_LENGTH = 6

/** True for the characters a pairing code is made of: 0-9 and upper-case A-F. */
internal fun isPairingCodeChar(c: Char): Boolean = c in '0'..'9' || c in 'A'..'F'

/** [raw] as a pairing code: upper-cased, anything that isn't a hex digit dropped, cut to [PAIRING_CODE_LENGTH]. */
fun normalizePairingCode(raw: String): String =
    raw.uppercase().filter(::isPairingCodeChar).take(PAIRING_CODE_LENGTH)
