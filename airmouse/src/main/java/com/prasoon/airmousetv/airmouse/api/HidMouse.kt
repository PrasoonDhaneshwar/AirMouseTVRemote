package com.prasoon.airmousetv.airmouse.api

import com.prasoon.airmousetv.airmouse.model.PointerButton
import kotlin.math.abs
import kotlin.math.roundToInt

/** The report ID of the mouse report in [HID_MOUSE_DESCRIPTOR]. */
const val HID_MOUSE_REPORT_ID = 1

/** The furthest one report can move an axis or turn the wheel: the descriptor declares -127..127. */
const val HID_AXIS_MAX = 127

/**
 * The most reports one movement is split into. A movement is one sensor frame, so anything beyond
 * this is a glitch or a stall catching up, and sending it late would throw the pointer across the
 * screen after the hand has stopped.
 */
const val MAX_REPORTS_PER_MOVE = 8

/**
 * What the phone tells the TV it is: a three-button mouse with a wheel, reporting relative movement.
 *
 * It is a mouse only, on purpose. A descriptor that also declares a keyboard makes Android treat
 * the phone as a hardware keyboard, and Android hides its on-screen keyboard while one is connected.
 * The TV reads this table once, when it pairs, so adding a keyboard later means pairing again.
 */
val HID_MOUSE_DESCRIPTOR: ByteArray = intArrayOf(
    0x05, 0x01,        // Usage Page (Generic Desktop)
    0x09, 0x02,        // Usage (Mouse)
    0xA1, 0x01,        // Collection (Application)
    0x85, HID_MOUSE_REPORT_ID,
    0x09, 0x01,        //   Usage (Pointer)
    0xA1, 0x00,        //   Collection (Physical)
    0x05, 0x09,        //     Usage Page (Button)
    0x19, 0x01,        //     Usage Minimum (Button 1)
    0x29, 0x03,        //     Usage Maximum (Button 3)
    0x15, 0x00,        //     Logical Minimum (0)
    0x25, 0x01,        //     Logical Maximum (1)
    0x95, 0x03,        //     Report Count (3)
    0x75, 0x01,        //     Report Size (1)
    0x81, 0x02,        //     Input (Data, Variable, Absolute): the three buttons
    0x95, 0x01,        //     Report Count (1)
    0x75, 0x05,        //     Report Size (5)
    0x81, 0x03,        //     Input (Constant): fills the byte
    0x05, 0x01,        //     Usage Page (Generic Desktop)
    0x09, 0x30,        //     Usage (X)
    0x09, 0x31,        //     Usage (Y)
    0x09, 0x38,        //     Usage (Wheel)
    0x15, 0x81,        //     Logical Minimum (-127)
    0x25, 0x7F,        //     Logical Maximum (127)
    0x75, 0x08,        //     Report Size (8)
    0x95, 0x03,        //     Report Count (3)
    0x81, 0x06,        //     Input (Data, Variable, Relative): X, Y, wheel
    0xC0,              //   End Collection
    0xC0               // End Collection
).map { it.toByte() }.toByteArray()

/** The bit of [button] in the first byte of a mouse report. */
private val PointerButton.mask: Int
    get() = when (this) {
        PointerButton.PRIMARY -> 0x01
        PointerButton.SECONDARY -> 0x02
    }

/**
 * Turns pointer movement, buttons and scrolling into mouse reports, each four bytes: buttons, X, Y,
 * wheel.
 *
 * A report carries whole counts, while slow pointing produces fractions of a count per frame. The
 * fractions are carried over to the next movement, so a slow sweep still adds up to the distance
 * the hand moved. A report also repeats which buttons are held, so the held buttons are remembered
 * here and go out with every movement: that is what makes a drag.
 */
class HidMouse {
    private var buttons = 0
    private var carryX = 0f
    private var carryY = 0f
    private var carryWheel = 0f

    /** The reports that move the pointer by [dx], [dy] counts; empty while the movement is still under one count. */
    @Synchronized
    fun move(dx: Float, dy: Float): List<ByteArray> {
        carryX += dx
        carryY += dy
        var x = carryX.roundToInt()
        var y = carryY.roundToInt()
        carryX -= x
        carryY -= y
        val reports = ArrayList<ByteArray>(1)
        while ((x != 0 || y != 0) && reports.size < MAX_REPORTS_PER_MOVE) {
            // Both axes shrink by the same factor, so a long diagonal stays a straight line
            val longest = maxOf(abs(x), abs(y), HID_AXIS_MAX)
            val stepX = x * HID_AXIS_MAX / longest
            val stepY = y * HID_AXIS_MAX / longest
            reports += report(stepX, stepY, 0)
            x -= stepX
            y -= stepY
        }
        return reports
    }

    /** The report that presses or releases [button], or null if it is already in that state. */
    @Synchronized
    fun button(button: PointerButton, down: Boolean): ByteArray? {
        val changed = if (down) buttons or button.mask else buttons and button.mask.inv()
        if (changed == buttons) return null
        buttons = changed
        return report(0, 0, 0)
    }

    /** The reports that turn the wheel by [notches], up being positive; empty while under one notch. */
    @Synchronized
    fun scroll(notches: Float): List<ByteArray> {
        carryWheel += notches
        var wheel = carryWheel.roundToInt()
        carryWheel -= wheel
        val reports = ArrayList<ByteArray>(1)
        while (wheel != 0 && reports.size < MAX_REPORTS_PER_MOVE) {
            val step = wheel.coerceIn(-HID_AXIS_MAX, HID_AXIS_MAX)
            reports += report(0, 0, step)
            wheel -= step
        }
        return reports
    }

    /**
     * Forgets carried fractions and held buttons. Returns the report that lets go of the buttons,
     * or null if none was held: a link that closes with a button down would leave the TV dragging.
     */
    @Synchronized
    fun reset(): ByteArray? {
        carryX = 0f
        carryY = 0f
        carryWheel = 0f
        if (buttons == 0) return null
        buttons = 0
        return report(0, 0, 0)
    }

    private fun report(x: Int, y: Int, wheel: Int) = byteArrayOf(buttons.toByte(), x.toByte(), y.toByte(), wheel.toByte())
}
