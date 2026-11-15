package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.airmouse.api.CALIBRATION_SECONDS
import com.prasoon.airmousetv.airmouse.model.AirMouseSettings
import com.prasoon.airmousetv.airmouse.model.AirMouseUiState
import com.prasoon.airmousetv.airmouse.model.ScrollWith
import com.prasoon.airmousetv.airmouse.model.VolumeButtons
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Faint
import com.prasoon.airmousetv.ui.theme.PadSurface
import kotlinx.coroutines.delay

/** What the settings sheet tells the rest of the app. */
internal class AirSettingsActions(
    val onChange: (AirMouseSettings) -> Unit = {},
    val onCalibrate: () -> Unit = {},
    val onUnlink: () -> Unit = {},
)

/**
 * How the air mouse feels, what the volume buttons do, and the way to let go of the TV. A sheet
 * over the pad, so a slider can be moved and tried at once: the pointer keeps working underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AirMouseSettingsSheet(air: AirMouseUiState, tvName: String, actions: AirSettingsActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(36.dp, 4.dp).background(Faint, CircleShape)) }
    ) {
        AirMouseSettingsContent(air, tvName, actions)
    }
}

@Composable
private fun AirMouseSettingsContent(air: AirMouseUiState, tvName: String, actions: AirSettingsActions) {
    val settings = air.settings
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Air mouse settings", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))

        SettingSlider(
            title = "Sensitivity",
            value = settings.sensitivity,
            valueLabel = when {
                settings.sensitivity < 0.34f -> "Precise"
                settings.sensitivity > 0.66f -> "Fast"
                else -> "Medium"
            },
            lowLabel = "Precise",
            highLabel = "Fast",
            onChange = { actions.onChange(settings.copy(sensitivity = it)) }
        )
        // Only the gyroscope is smoothed; a finger on a mouse pad is steady enough by itself
        if (air.hasGyroscope) {
            SettingSlider(
                title = "Smooth Air",
                value = settings.smoothAir,
                valueLabel = when {
                    settings.smoothAir < 0.34f -> "Quicker"
                    settings.smoothAir > 0.66f -> "More steady"
                    else -> "Balanced"
                },
                lowLabel = "Quicker",
                highLabel = "Steadier",
                onChange = { actions.onChange(settings.copy(smoothAir = it)) }
            )
            Calibrate(air.calibrating, air.calibrated, actions.onCalibrate)
            VolumeButtonsChoice(settings.volumeButtons) { actions.onChange(settings.copy(volumeButtons = it)) }
        }
        ScrollWithChoice(settings.scrollWith) { actions.onChange(settings.copy(scrollWith = it)) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Linked TV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$tvName · Bluetooth", style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = actions.onUnlink) {
                Text("Unlink", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/**
 * A slider with its name, what the current position is called, and what each end means. The value
 * is applied when the thumb is let go, not while it moves: every change rebuilds the pointer's
 * smoothing, and a pointer that changes its feel under a moving thumb can't be judged.
 */
@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueLabel: String,
    lowLabel: String,
    highLabel: String,
    onChange: (Float) -> Unit
) {
    var dragged by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged) },
            modifier = Modifier.semantics { contentDescription = title }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val ends = MaterialTheme.typography.bodySmall
            Text(lowLabel, style = ends, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(highLabel, style = ends, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The calibration row. While it runs, the row becomes the instruction and a countdown, since the
 * one thing the user has to do is leave the phone alone.
 */
@Composable
private fun Calibrate(calibrating: Boolean, calibrated: Boolean?, onCalibrate: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PadSurface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (calibrating) {
            var secondsLeft by remember { mutableIntStateOf(CALIBRATION_SECONDS) }
            LaunchedEffect(Unit) {
                while (secondsLeft > 1) {
                    delay(1000)
                    secondsLeft--
                }
            }
            Text(
                "$secondsLeft",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text("Lay the phone flat", style = MaterialTheme.typography.titleMedium)
                Text(
                    "On a table, screen up, and don't touch it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(Modifier.weight(1f)) {
                Text("Calibrate", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (calibrated) {
                        true -> "Done. The pointer should stay put now."
                        false -> "The phone moved. Lay it flat on a table and try again."
                        null -> "Use if the pointer drifts on its own"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (calibrated == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onCalibrate, modifier = Modifier.height(40.dp), shape = MaterialTheme.shapes.small) {
                Text(if (calibrated == false) "Try again" else "Start", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun VolumeButtonsChoice(selected: VolumeButtons, onSelect: (VolumeButtons) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Volume buttons", style = MaterialTheme.typography.titleMedium)
            Text(
                "· while air pointing is on",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.selectableGroup()) {
            VolumeOption("Vol + clicks, Vol − is Back", selected == VolumeButtons.CLICK_AND_BACK) { onSelect(VolumeButtons.CLICK_AND_BACK) }
            VolumeOption("Swapped: Vol − clicks, Vol + is Back", selected == VolumeButtons.SWAPPED) { onSelect(VolumeButtons.SWAPPED) }
            VolumeOption("Change the TV's volume", selected == VolumeButtons.TV_VOLUME) { onSelect(VolumeButtons.TV_VOLUME) }
        }
    }
}

@Composable
private fun ScrollWithChoice(selected: ScrollWith, onSelect: (ScrollWith) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Scroll with", style = MaterialTheme.typography.titleMedium)
            Text(
                "· two fingers and the strips",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.selectableGroup()) {
            VolumeOption("TV keys: smooth in most TV apps", selected == ScrollWith.TV_KEYS) { onSelect(ScrollWith.TV_KEYS) }
            VolumeOption("Mouse wheel: for browsers and maps", selected == ScrollWith.MOUSE_WHEEL) { onSelect(ScrollWith.MOUSE_WHEEL) }
        }
    }
}

@Composable
private fun VolumeOption(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The row is the control; a second click target inside it would only confuse TalkBack
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
    }
}

private val previewAir = AirMouseUiState(available = true, linked = true, hasGyroscope = true)

@Composable
private fun SettingsPreview(air: AirMouseUiState) {
    AirMouseTVRemoteTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 16.dp)) {
            AirMouseSettingsContent(air, "Living Room TV", AirSettingsActions())
        }
    }
}

@Preview(showBackground = true, widthDp = 390, name = "Air mouse settings")
@Composable
private fun AirSettingsPreview() = SettingsPreview(previewAir)

@Preview(showBackground = true, widthDp = 390, name = "Air mouse settings: calibrating")
@Composable
private fun AirSettingsCalibratingPreview() = SettingsPreview(previewAir.copy(calibrating = true))

@Preview(showBackground = true, widthDp = 390, name = "Air mouse settings: the phone moved")
@Composable
private fun AirSettingsMovedPreview() = SettingsPreview(previewAir.copy(calibrated = false))

@Preview(showBackground = true, widthDp = 390, name = "Air mouse settings: no gyroscope")
@Composable
private fun AirSettingsNoGyroscopePreview() = SettingsPreview(previewAir.copy(hasGyroscope = false))
