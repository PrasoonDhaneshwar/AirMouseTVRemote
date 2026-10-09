package com.prasoon.airmousetv.data.api

import com.prasoon.airmousetv.proto.remote.RemoteKeyCode

/** Maps typed characters to the remote key presses that produce them on a US-layout TV keyboard. */
object TextKeyMapper {

    /** One key press, with Shift held when [shift] is true. */
    data class Stroke(val code: RemoteKeyCode, val shift: Boolean = false)

    private val symbols: Map<Char, Stroke> = mapOf(
        ' ' to Stroke(RemoteKeyCode.KEYCODE_SPACE),
        '.' to Stroke(RemoteKeyCode.KEYCODE_PERIOD),
        ',' to Stroke(RemoteKeyCode.KEYCODE_COMMA),
        '-' to Stroke(RemoteKeyCode.KEYCODE_MINUS),
        '=' to Stroke(RemoteKeyCode.KEYCODE_EQUALS),
        '[' to Stroke(RemoteKeyCode.KEYCODE_LEFT_BRACKET),
        ']' to Stroke(RemoteKeyCode.KEYCODE_RIGHT_BRACKET),
        '\\' to Stroke(RemoteKeyCode.KEYCODE_BACKSLASH),
        ';' to Stroke(RemoteKeyCode.KEYCODE_SEMICOLON),
        '\'' to Stroke(RemoteKeyCode.KEYCODE_APOSTROPHE),
        '/' to Stroke(RemoteKeyCode.KEYCODE_SLASH),
        '`' to Stroke(RemoteKeyCode.KEYCODE_GRAVE),
        '@' to Stroke(RemoteKeyCode.KEYCODE_AT),
        '*' to Stroke(RemoteKeyCode.KEYCODE_STAR),
        '#' to Stroke(RemoteKeyCode.KEYCODE_POUND),
        '+' to Stroke(RemoteKeyCode.KEYCODE_PLUS),
        '_' to Stroke(RemoteKeyCode.KEYCODE_MINUS, shift = true),
        ':' to Stroke(RemoteKeyCode.KEYCODE_SEMICOLON, shift = true),
        '"' to Stroke(RemoteKeyCode.KEYCODE_APOSTROPHE, shift = true),
        '?' to Stroke(RemoteKeyCode.KEYCODE_SLASH, shift = true),
        '!' to Stroke(RemoteKeyCode.KEYCODE_1, shift = true),
        '(' to Stroke(RemoteKeyCode.KEYCODE_9, shift = true),
        ')' to Stroke(RemoteKeyCode.KEYCODE_0, shift = true),
    )

    /** The key press for [c], or null if the remote can't type it. */
    fun toStroke(c: Char): Stroke? = when (c) {
        in 'a'..'z' -> Stroke(RemoteKeyCode.valueOf("KEYCODE_${c.uppercaseChar()}"))
        in 'A'..'Z' -> Stroke(RemoteKeyCode.valueOf("KEYCODE_$c"), shift = true)
        in '0'..'9' -> Stroke(RemoteKeyCode.valueOf("KEYCODE_$c"))
        else -> symbols[c]
    }
}
