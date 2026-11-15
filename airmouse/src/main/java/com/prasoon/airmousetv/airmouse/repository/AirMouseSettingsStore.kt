package com.prasoon.airmousetv.airmouse.repository

import android.content.Context
import androidx.core.content.edit
import com.prasoon.airmousetv.airmouse.api.Vec3
import com.prasoon.airmousetv.airmouse.model.AirMouseSettings
import com.prasoon.airmousetv.airmouse.model.VolumeButtons

private const val PREFS = "air_mouse_settings"

/**
 * Keeps what the user chose for the air mouse, and what the app has learned about this phone: the
 * gyroscope's resting drift from the last calibration, and whether the phone has the Bluetooth
 * mouse role at all.
 */
class AirMouseSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AirMouseSettings {
        val defaults = AirMouseSettings()
        return AirMouseSettings(
            sensitivity = prefs.getFloat("sensitivity", defaults.sensitivity),
            smoothAir = prefs.getFloat("smooth_air", defaults.smoothAir),
            volumeButtons = VolumeButtons.entries.firstOrNull { it.name == prefs.getString("volume_buttons", null) }
                ?: defaults.volumeButtons,
            airPointing = prefs.getBoolean("air_pointing", defaults.airPointing)
        )
    }

    fun save(settings: AirMouseSettings) {
        prefs.edit {
            putFloat("sensitivity", settings.sensitivity)
            putFloat("smooth_air", settings.smoothAir)
            putString("volume_buttons", settings.volumeButtons.name)
            putBoolean("air_pointing", settings.airPointing)
        }
    }

    /** The gyroscope's resting drift in rad/s; zero until the user has calibrated. */
    var gyroBias: Vec3
        get() = Vec3(prefs.getFloat("bias_x", 0f), prefs.getFloat("bias_y", 0f), prefs.getFloat("bias_z", 0f))
        set(value) = prefs.edit {
            putFloat("bias_x", value.x)
            putFloat("bias_y", value.y)
            putFloat("bias_z", value.z)
        }

    /**
     * Whether this phone has the Bluetooth mouse role: null until it has been found out. It is
     * remembered because finding out needs Bluetooth to be on, and the answer decides whether the
     * air mouse is advertised to the user at all.
     */
    var hasMouseRole: Boolean?
        get() = if (prefs.contains("mouse_role")) prefs.getBoolean("mouse_role", false) else null
        set(value) = prefs.edit { if (value == null) remove("mouse_role") else putBoolean("mouse_role", value) }

    /** True once the air mouse has been offered right after a TV was paired; that offer is made once. */
    var offeredAfterPairing: Boolean
        get() = prefs.getBoolean("offered_after_pairing", false)
        set(value) = prefs.edit { putBoolean("offered_after_pairing", value) }
}
