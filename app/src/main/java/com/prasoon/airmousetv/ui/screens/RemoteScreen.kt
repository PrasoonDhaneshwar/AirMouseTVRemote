package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.prasoon.airmousetv.data.model.KeyAction
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.presentation.RemoteViewModel

/**
 * The working remote: D-pad, Back/Home, media and volume keys, power, and text entry.
 * A short press taps the key; holding it sends a long press. While a dropped session is being re-established the keys are
 * disabled and a banner says so.
 */
@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val enabled = !uiState.isReconnecting

    var padMode by rememberSaveable { mutableStateOf(PadMode.DPAD) }
    var showTextEntry by remember { mutableStateOf(false) }
    // The TV asks for its keyboard by bumping showRequests; open text entry on each new request
    val showRequests = uiState.imeField?.showRequests ?: 0
    var seenRequests by remember { mutableIntStateOf(showRequests) }
    LaunchedEffect(showRequests) {
        if (showRequests > seenRequests) showTextEntry = true
        seenRequests = showRequests
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        if (uiState.isReconnecting) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Reconnecting…", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text("Connected", style = MaterialTheme.typography.titleLarge)
        }
        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        PadModeToggle(padMode) { padMode = it }

        when (padMode) {
            PadMode.DPAD -> {
                KeyRow {
                    Spacer(Modifier.weight(1f))
                    RemoteKey("▲", TvKey.UP, viewModel, enabled, DPad, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
                KeyRow {
                    RemoteKey("◀", TvKey.LEFT, viewModel, enabled, DPad, Modifier.weight(1f))
                    RemoteKey("OK", TvKey.OK, viewModel, enabled, Ok, Modifier.weight(1f))
                    RemoteKey("▶", TvKey.RIGHT, viewModel, enabled, DPad, Modifier.weight(1f))
                }
                KeyRow {
                    Spacer(Modifier.weight(1f))
                    RemoteKey("▼", TvKey.DOWN, viewModel, enabled, DPad, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
            PadMode.TOUCHPAD -> Touchpad(viewModel, enabled)
        }

        KeyRow {
            RemoteKey("Back", TvKey.BACK, viewModel, enabled, Nav, Modifier.weight(1f))
            RemoteKey("Home", TvKey.HOME, viewModel, enabled, Nav, Modifier.weight(1f))
        }
        KeyRow {
            RemoteKey("⏮", TvKey.PREVIOUS, viewModel, enabled, Media, Modifier.weight(1f))
            RemoteKey("⏯", TvKey.PLAY_PAUSE, viewModel, enabled, Media, Modifier.weight(1f))
            RemoteKey("⏭", TvKey.NEXT, viewModel, enabled, Media, Modifier.weight(1f))
        }
        KeyRow {
            RemoteKey("Vol −", TvKey.VOLUME_DOWN, viewModel, enabled, Volume, Modifier.weight(1f))
            RemoteKey("Mute", TvKey.MUTE, viewModel, enabled, MuteOrange, Modifier.weight(1f))
            RemoteKey("Vol +", TvKey.VOLUME_UP, viewModel, enabled, Volume, Modifier.weight(1f))
        }
        KeyRow {
            ActionKey("Keyboard", enabled, Teal, Modifier.weight(1f)) { showTextEntry = true }
            RemoteKey("Power", TvKey.POWER, viewModel, enabled, PowerRed, Modifier.weight(1f))
        }

        TextButton(onClick = { viewModel.switchTv() }) { Text("Switch TV") }
    }

    if (showTextEntry) {
        TextEntryDialog(viewModel = viewModel, onDismiss = { showTextEntry = false })
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF263238)),
    ) {
        PadMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (active) Ok else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(mode.label, color = Color.White, style = MaterialTheme.typography.titleSmall)
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
private fun Touchpad(viewModel: RemoteViewModel, enabled: Boolean) {
    val stepPx = with(LocalDensity.current) { SWIPE_STEP_DP.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) Color(0xFF37474F) else Color(0xFF37474F).copy(alpha = 0.3f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { viewModel.sendKey(TvKey.OK) })
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
                            viewModel.sendKey(if (dx > 0) TvKey.RIGHT else TvKey.LEFT)
                            dx -= stepPx * sign(dx)
                            dy = 0f
                        } else if (abs(dy) > abs(dx) && abs(dy) >= stepPx) {
                            viewModel.sendKey(if (dy > 0) TvKey.DOWN else TvKey.UP)
                            dy -= stepPx * sign(dy)
                            dx = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text("Swipe to move · tap to select", color = Color.White.copy(alpha = 0.6f))
    }
}

private val DPad = Color(0xFF37474F)
private val Ok = Color(0xFF3F51B5)
private val Nav = Color(0xFF546E7A)
private val Media = Color(0xFF7B1FA2)
private val Volume = Color(0xFF2E7D32)
private val MuteOrange = Color(0xFFEF6C00)
private val Teal = Color(0xFF00838F)
private val PowerRed = Color(0xFFC62828)

/** How long a key must be held before it counts as a long press instead of a tap. */
private const val HOLD_THRESHOLD_MS = 400L

/** A full-width row of keys with even gaps between them. */
@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * A coloured key that taps on a short press. Held past [HOLD_THRESHOLD_MS] it sends a long press
 * (key down on the TV), released when the finger lifts, so volume repeats and power opens its menu.
 */
@Composable
private fun RemoteKey(
    label: String,
    key: TvKey,
    viewModel: RemoteViewModel,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    KeySurface(
        label = label,
        color = color,
        enabled = enabled,
        pressed = pressed,
        modifier = modifier.pointerInput(key, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                pressed = true
                // null means the hold timeout expired before release; true = released, false = cancelled
                val released = withTimeoutOrNull(HOLD_THRESHOLD_MS) { tryAwaitRelease() }
                when (released) {
                    true -> viewModel.sendKey(key)
                    null -> {
                        viewModel.sendKey(key, KeyAction.LONG_START)
                        tryAwaitRelease()
                        viewModel.sendKey(key, KeyAction.LONG_END)
                    }
                    false -> Unit
                }
                pressed = false
            })
        }
    )
}

/** A coloured key that runs [onClick] instead of sending a TV key. */
@Composable
private fun ActionKey(label: String, enabled: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    KeySurface(
        label = label,
        color = color,
        enabled = enabled,
        pressed = false,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun KeySurface(label: String, color: Color, enabled: Boolean, pressed: Boolean, modifier: Modifier) {
    val fill = when {
        !enabled -> color.copy(alpha = 0.3f)
        pressed -> color.copy(alpha = 0.65f)
        else -> color
    }
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
