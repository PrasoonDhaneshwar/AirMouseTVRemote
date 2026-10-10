package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.theme.Offline
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

/**
 * The working remote: D-pad, Back/Home, media and volume keys, power, and text entry.
 * A short press taps the key; holding it sends a long press. While a dropped session is being re-established the keys are
 * disabled and a banner says so.
 */
@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var padMode by rememberSaveable { mutableStateOf(PadMode.DPAD) }
    var showTextEntry by remember { mutableStateOf(false) }
    // The TV asks for its keyboard by bumping showRequests; open text entry on each new request
    val showRequests = uiState.imeField?.showRequests ?: 0
    var seenRequests by remember { mutableIntStateOf(showRequests) }
    LaunchedEffect(showRequests) {
        if (showRequests > seenRequests) showTextEntry = true
        seenRequests = showRequests
    }

    RemoteContent(
        uiState = uiState,
        padMode = padMode,
        onPadModeChange = { padMode = it },
        onKey = viewModel::sendKey,
        onShowKeyboard = { showTextEntry = true },
        onSwitchTv = viewModel::switchTv
    )

    if (showTextEntry) {
        TextEntryDialog(viewModel = viewModel, onDismiss = { showTextEntry = false })
    }
}

@Composable
private fun Header(tvName: String, reconnecting: Boolean, enabled: Boolean, onKey: (TvKey, KeyAction) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val statusColor = if (reconnecting) Offline else MaterialTheme.colorScheme.primary
                Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                Text(
                    text = if (reconnecting) "DISCONNECTED · RECONNECTING…" else "CONNECTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
            Text(tvName.ifBlank { "TV" }, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
        RemoteKey(
            key = TvKey.POWER,
            onKey = onKey,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier.size(44.dp)
        ) { KeyIcon(Icons.Default.PowerSettingsNew, "Power", tint = MaterialTheme.colorScheme.secondary) }
    }
}

/** Previous / play-pause / next in a rounded pill. */
@Composable
private fun MediaPill(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteKey(TvKey.PREVIOUS, onKey, enabled, Modifier.size(48.dp), CircleShape, Color.Transparent) {
            KeyIcon(Icons.Default.SkipPrevious, "Previous")
        }
        RemoteKey(
            TvKey.PLAY_PAUSE, onKey, enabled, Modifier.size(width = 96.dp, height = 48.dp), CircleShape,
            MaterialTheme.colorScheme.surfaceContainerHigh
        ) { KeyIcon(Icons.Default.PlayArrow, "Play or pause") }
        RemoteKey(TvKey.NEXT, onKey, enabled, Modifier.size(48.dp), CircleShape, Color.Transparent) {
            KeyIcon(Icons.Default.SkipNext, "Next")
        }
    }
}

/** Circular directional pad with OK in the middle. */
@Composable
private fun DPad(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean) {
    val keyShape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .size(256.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        RemoteKey(TvKey.UP, onKey, enabled, Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(64.dp, 56.dp), keyShape) {
            KeyIcon(Icons.Default.KeyboardArrowUp, "Up")
        }
        RemoteKey(TvKey.DOWN, onKey, enabled, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(64.dp, 56.dp), keyShape) {
            KeyIcon(Icons.Default.KeyboardArrowDown, "Down")
        }
        RemoteKey(TvKey.LEFT, onKey, enabled, Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(56.dp, 64.dp), keyShape) {
            KeyIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left")
        }
        RemoteKey(TvKey.RIGHT, onKey, enabled, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(56.dp, 64.dp), keyShape) {
            KeyIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right")
        }
        RemoteKey(
            TvKey.OK, onKey, enabled, Modifier.size(88.dp), CircleShape,
            MaterialTheme.colorScheme.background
        ) { Text("OK", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
    }
}

/** Minus, mute and plus keys. The TV protocol has volume steps rather than an absolute level, so there is no slider. */
@Composable
private fun VolumeBar(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteKey(TvKey.VOLUME_DOWN, onKey, enabled, Modifier.size(56.dp), MaterialTheme.shapes.medium) {
            KeyIcon(Icons.Default.Remove, "Volume down")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteKey(TvKey.MUTE, onKey, enabled, Modifier.size(48.dp), CircleShape) {
                KeyIcon(Icons.Default.VolumeOff, "Mute", tint = MaterialTheme.colorScheme.secondary)
            }
            Text("VOLUME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RemoteKey(TvKey.VOLUME_UP, onKey, enabled, Modifier.size(56.dp), MaterialTheme.shapes.medium) {
            KeyIcon(Icons.Default.Add, "Volume up")
        }
    }
}

/**
 * A key with a small caption underneath. Pass [key] to send a TV key (with tap/hold handling),
 * or [onClick] to run a local action.
 */
@Composable
private fun LabeledKey(
    label: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    key: TvKey? = null,
    onKey: ((TvKey, KeyAction) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val size = Modifier.size(56.dp)
        if (key != null && onKey != null) {
            RemoteKey(key, onKey, enabled, size, MaterialTheme.shapes.medium, content = content)
        } else {
            KeySurface(
                enabled = enabled,
                pressed = false,
                shape = MaterialTheme.shapes.medium,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = size.clickable(enabled = enabled && onClick != null) { onClick?.invoke() },
                content = content
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyIcon(icon: ImageVector, description: String, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(26.dp))
}

@Composable
private fun RemoteContent(
    uiState: RemoteUiState,
    padMode: PadMode,
    onPadModeChange: (PadMode) -> Unit,
    onKey: (TvKey, KeyAction) -> Unit,
    onShowKeyboard: () -> Unit,
    onSwitchTv: () -> Unit
) {
    val enabled = !uiState.isReconnecting
    val tvName = (uiState.mode as? RemoteMode.Connected)?.tv?.displayName.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Header(tvName, reconnecting = uiState.isReconnecting, enabled = enabled, onKey = onKey)

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        MediaPill(onKey, enabled)

        PadModeToggle(padMode, onPadModeChange)

        when (padMode) {
            PadMode.DPAD -> DPad(onKey, enabled)
            PadMode.TOUCHPAD -> Touchpad(onKey, enabled)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledKey("BACK", enabled, onClick = null, key = TvKey.BACK, onKey = onKey) {
                KeyIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            LabeledKey("HOME", enabled, onClick = null, key = TvKey.HOME, onKey = onKey) {
                KeyIcon(Icons.Default.Home, "Home")
            }
            LabeledKey("KEYBOARD", enabled, onClick = onShowKeyboard) {
                KeyIcon(Icons.Default.Keyboard, "Keyboard")
            }
        }

        VolumeBar(onKey, enabled)

        TextButton(onClick = { onSwitchTv() }) {
            Text("SWITCH TV", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Types into whatever text field is focused on the TV. Each change is sent as the difference
 * from the previous text, so edits and deletions work: through the TV's text input session, or as
 * key presses if none is open. The keyboard's send action sends Enter to the TV.
 */
@Composable
private fun TextEntryDialog(viewModel: RemoteViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val field = uiState.imeField
    var text by remember { mutableStateOf(field?.value.orEmpty()) }
    val focusRequester = remember { FocusRequester() }

    // Wait for the field to be laid out before asking for focus, or there is nothing to focus yet
    LaunchedEffect(Unit) {
        viewModel.beginTextEntry(text)
        withFrameNanos { }
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field?.label?.takeIf { it.isNotBlank() } ?: "Type on TV") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.typeText(it)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.submitText()
                    onDismiss()
                })
            )
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.submitText()
                onDismiss()
            }) { Text("Enter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** How the directional keys are presented. */
private enum class PadMode(val label: String) { DPAD("D-pad"), TOUCHPAD("Touchpad") }

/** Two-way switch between the D-pad and the touchpad. */
@Composable
private fun PadModeToggle(selected: PadMode, onSelect: (PadMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        PadMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

/** Distance dragged, in dp, that counts as one D-pad step. */
private const val SWIPE_STEP_DP = 40

/**
 * A surface that turns swipes into D-pad presses (one per [SWIPE_STEP_DP] dragged, so a long swipe
 * keeps scrolling) and a tap into OK.
 */
@Composable
private fun Touchpad(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean) {
    val stepPx = with(LocalDensity.current) { SWIPE_STEP_DP.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { onKey(TvKey.OK, KeyAction.TAP) })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { change, delta ->
                        change.consume()
                        dx += delta.x
                        dy += delta.y
                        // Step along the dominant axis and drop the other, so a diagonal wobble doesn't misfire
                        if (abs(dx) >= abs(dy) && abs(dx) >= stepPx) {
                            onKey(if (dx > 0) TvKey.RIGHT else TvKey.LEFT, KeyAction.TAP)
                            dx -= stepPx * sign(dx)
                            dy = 0f
                        } else if (abs(dy) > abs(dx) && abs(dy) >= stepPx) {
                            onKey(if (dy > 0) TvKey.DOWN else TvKey.UP, KeyAction.TAP)
                            dy -= stepPx * sign(dy)
                            dx = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Swipe to move · tap to select",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** How long a key must be held before it counts as a long press instead of a tap. */
private const val HOLD_THRESHOLD_MS = 400L

/**
 * A key that taps on a short press. Held past [HOLD_THRESHOLD_MS] it sends a long press
 * (key down on the TV), released when the finger lifts, so volume repeats and power opens its menu.
 */
@Composable
private fun RemoteKey(
    key: TvKey,
    onKey: (TvKey, KeyAction) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    KeySurface(
        enabled = enabled,
        pressed = pressed,
        shape = shape,
        container = container,
        modifier = modifier.pointerInput(key, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                pressed = true
                // null means the hold timeout expired before release; true = released, false = cancelled
                val released = withTimeoutOrNull(HOLD_THRESHOLD_MS) { tryAwaitRelease() }
                when (released) {
                    true -> onKey(key, KeyAction.TAP)
                    null -> {
                        onKey(key, KeyAction.LONG_START)
                        tryAwaitRelease()
                        onKey(key, KeyAction.LONG_END)
                    }
                    false -> Unit
                }
                pressed = false
            })
        },
        content = content
    )
}

@Composable
private fun KeySurface(
    enabled: Boolean,
    pressed: Boolean,
    shape: Shape,
    container: Color,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val teal = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (pressed) teal.copy(alpha = 0.25f) else container)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private val previewMode = RemoteMode.Connected(
    DiscoveredTv(name = "living-room-tv", friendlyName = "Living Room TV", host = "192.168.1.15", port = 6466)
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "D-pad")
@Composable
private fun RemoteDPadPreview() {
    AirMouseTVRemoteTheme {
        RemoteContent(RemoteUiState(mode = previewMode), PadMode.DPAD, {}, { _, _ -> }, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Touchpad")
@Composable
private fun RemoteTouchpadPreview() {
    AirMouseTVRemoteTheme {
        RemoteContent(RemoteUiState(mode = previewMode), PadMode.TOUCHPAD, {}, { _, _ -> }, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Reconnecting")
@Composable
private fun RemoteReconnectingPreview() {
    AirMouseTVRemoteTheme {
        RemoteContent(
            RemoteUiState(mode = previewMode, isReconnecting = true, error = "Connection lost"),
            PadMode.DPAD, {}, { _, _ -> }, {}, {}
        )
    }
}
