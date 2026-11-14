package com.prasoon.airmousetv.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Amber
import com.prasoon.airmousetv.ui.theme.Faint
import com.prasoon.airmousetv.ui.theme.Online
import com.prasoon.airmousetv.ui.theme.PadSurface
import com.prasoon.airmousetv.ui.theme.SurfaceHigh
import kotlin.math.abs

/** Distance dragged, in dp, that turns the TV's wheel by one notch: about a finger's width, like a notch on a real wheel. */
private const val SCROLL_DP_PER_NOTCH = 24f

/**
 * The longest a touch can last and still be a tap, on a pad that clicks on taps. Shorter than
 * Android's own tap timeout, since anything slower is a thumb coming to rest.
 */
private const val TAP_MAX_MS = 200L

/** What the air mouse pad tells the rest of the app; all optional, so previews need none of them. */
internal class AirPadActions(
    /** The thumb came down on the pad (true) or left it. */
    val onThumb: (Boolean) -> Unit = {},
    /** The thumb slid by this many dp, over this many seconds. */
    val onSlide: (dxDp: Float, dyDp: Float, dtSeconds: Float) -> Unit = { _, _, _ -> },
    val onButton: (PointerButton, down: Boolean) -> Unit = { _, _ -> },
    /** Scroll by this many wheel notches, up being positive. */
    val onScroll: (Float) -> Unit = {},
    val onAllowBluetooth: () -> Unit = {},
    val onTurnOnBluetooth: () -> Unit = {},
    val onSetUpAgain: () -> Unit = {},
)

/**
 * The air mouse: a surface to rest the thumb on, with Click and Right underneath.
 *
 * While the thumb rests on the surface the TV's pointer follows the phone, and sliding the thumb
 * nudges it like a laptop trackpad; both add up. Lifting the thumb lets go of the pointer, so the
 * phone can be brought back to a comfortable position. Two fingers, or the strip at the edge,
 * scroll. On a phone without a gyroscope sliding is the only way to point, and a tap clicks.
 *
 * With a gyroscope a tap does not click: the thumb comes down and lifts all the time just to re-aim,
 * and each of those would be a click. Click is its own key, which also keeps the pointer still while
 * it is pressed, because the thumb has left the surface.
 *
 * The surface works only while the Bluetooth [link] is up. Until then it says what is missing; the
 * keys around it keep working, since they travel over Wi-Fi.
 */
@Composable
internal fun AirMousePad(
    link: PointerLinkState,
    hasGyroscope: Boolean,
    actions: AirPadActions,
    modifier: Modifier = Modifier
) {
    val ready = link == PointerLinkState.READY
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val shape = MaterialTheme.shapes.extraLarge
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(PadSurface)
                .border(1.dp, SurfaceHigh, shape)
        ) {
            if (ready) {
                PointingSurface(hasGyroscope, actions)
            } else {
                LinkNotice(link, actions)
            }
            LinkStatus(link, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val teal = MaterialTheme.colorScheme.primary
            PointerKey(PointerButton.PRIMARY, "Click", ready, actions.onButton, Modifier.weight(3f), container = teal) {
                Text(
                    "Click",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            PointerKey(PointerButton.SECONDARY, "Right click", ready, actions.onButton, Modifier.weight(1f)) {
                Text("Right", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

/** The working surface: follows the thumb, and holds the scroll strip. */
@Composable
private fun BoxScope.PointingSurface(hasGyroscope: Boolean, actions: AirPadActions) {
    val currentActions by rememberUpdatedState(actions)
    val view = LocalView.current
    var pointing by remember { mutableStateOf(false) }
    val teal = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = if (hasGyroscope) {
                    "Air mouse pad. Hold a thumb here and point the phone to move the pointer on the TV."
                } else {
                    "Air mouse pad. Slide a thumb here to move the pointer on the TV. Double-tap to click."
                }
                onClick {
                    currentActions.onButton(PointerButton.PRIMARY, true)
                    currentActions.onButton(PointerButton.PRIMARY, false)
                    true
                }
            }
            .pointerInput(hasGyroscope) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var travelled = Offset.Zero
                    var sliding = false
                    var scrolling = false
                    var lastMs = down.uptimeMillis
                    pointing = true
                    currentActions.onThumb(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.filter { it.pressed }
                            if (fingers.isEmpty()) break
                            // Fingers that only just landed have no movement yet, and would halve the average
                            val moving = fingers.filter { it.previousPressed }
                            val delta = moving.fold(Offset.Zero) { sum, change -> sum + change.positionChange() } /
                                moving.size.coerceAtLeast(1).toFloat()
                            val nowMs = fingers.first().uptimeMillis
                            val dt = (nowMs - lastMs) / 1000f
                            lastMs = nowMs

                            if (fingers.size >= 2 && !scrolling) {
                                // A second finger means scrolling, and the page should scroll under a still
                                // pointer. It stays let go until every finger has lifted, or the finger that
                                // lifts last would drag the pointer along
                                scrolling = true
                                pointing = false
                                currentActions.onThumb(false)
                            }
                            if (scrolling) {
                                // Like a touch screen: the page follows the fingers, so fingers moving down turn the wheel up
                                if (fingers.size >= 2) currentActions.onScroll(delta.y / SCROLL_DP_PER_NOTCH.dp.toPx())
                            } else {
                                travelled += delta
                                // A resting thumb is never perfectly still. Until it has clearly moved it is
                                // only holding the pointer, and its wobble must not reach the TV
                                if (!sliding && travelled.getDistance() > slop) sliding = true
                                if (sliding) currentActions.onSlide(delta.x.toDp().value, delta.y.toDp().value, dt)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (!hasGyroscope && !sliding && !scrolling && lastMs - down.uptimeMillis <= TAP_MAX_MS) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            currentActions.onButton(PointerButton.PRIMARY, true)
                            currentActions.onButton(PointerButton.PRIMARY, false)
                        }
                    } finally {
                        // Also runs when the gesture is cancelled (the link dropped, the screen went away):
                        // the pointer must never be left following a thumb that is no longer there
                        pointing = false
                        currentActions.onThumb(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasGyroscope) {
                ThumbRing(pointing)
                Text(
                    if (pointing) "Pointing" else "Rest your thumb and point,\nor slide it to fine-tune",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (pointing) FontWeight.Medium else FontWeight.Normal),
                    color = if (pointing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                // Always laid out, so the ring doesn't jump when the line appears
                Text(
                    "Lift your thumb to re-aim",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pointing) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent
                )
            } else {
                Text("Slide your thumb to move the pointer", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Tap to click · two fingers to scroll",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (pointing) {
            // A faint wash over the whole surface: with the phone pointed at the TV, the ring alone is easy to miss
            Box(Modifier.fillMaxSize().background(teal.copy(alpha = 0.04f)))
        }
    }
    ScrollStrip(
        onScroll = { currentActions.onScroll(it) },
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 16.dp, bottom = 40.dp)
    )
}

/** Where the thumb goes: a dashed outline at rest, filled teal with a halo while the pointer is following. */
@Composable
private fun ThumbRing(pointing: Boolean) {
    val teal = MaterialTheme.colorScheme.primary
    val tint by animateColorAsState(if (pointing) teal else MaterialTheme.colorScheme.onSurfaceVariant, tween(120), label = "thumb")
    Box(
        modifier = Modifier
            .size(132.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                if (pointing) {
                    drawCircle(teal.copy(alpha = 0.06f), radius = size.minDimension / 2 + 22.dp.toPx())
                    drawCircle(teal.copy(alpha = 0.16f))
                    drawCircle(teal, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                } else {
                    val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    drawCircle(Faint, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke, pathEffect = dashes))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.TouchApp, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp))
    }
}

/**
 * A strip to drag up and down, for scrolling with the one thumb that is already on the pad. Like a
 * mouse wheel, dragging up scrolls up. Each notch sent ticks, so it can be used without looking.
 */
@Composable
private fun ScrollStrip(onScroll: (Float) -> Unit, modifier: Modifier = Modifier) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    val view = LocalView.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = "Scroll strip. Drag up or down to scroll on the TV." }
            .pointerInput(Unit) {
                val notchPx = SCROLL_DP_PER_NOTCH.dp.toPx()
                awaitEachGesture {
                    // Consumed, so the pad underneath doesn't take this finger for the thumb
                    awaitFirstDown().consume()
                    var dragged = 0f
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: break
                        dragged += change.positionChange().y
                        change.consume()
                        // Whole notches only, so each one sent can be felt
                        while (abs(dragged) >= notchPx) {
                            val up = dragged < 0
                            currentOnScroll(if (up) 1f else -1f)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            dragged += if (up) notchPx else -notchPx
                        }
                    }
                }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val hint = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = hint, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.size(4.dp).background(Faint, CircleShape)) }
        }
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = hint, modifier = Modifier.size(18.dp))
    }
}

/** What stands in for the surface while the pointer can't be used, with the way out where there is one. */
@Composable
private fun BoxScope.LinkNotice(link: PointerLinkState, actions: AirPadActions) {
    val (title, body) = when (link) {
        PointerLinkState.CONNECTING -> "Connecting the pointer…" to
            "Waiting for the TV's Bluetooth. The keys below still work over Wi-Fi."
        PointerLinkState.BLUETOOTH_OFF -> "Bluetooth is off" to
            "The pointer travels over Bluetooth. The keys below still work over Wi-Fi."
        PointerLinkState.BLOCKED -> "The pointer is in use elsewhere" to
            "Another app is using this phone as a Bluetooth mouse or keyboard. Close it, then come back."
        PointerLinkState.IDLE -> "Bluetooth isn't allowed" to
            "The pointer needs the Nearby devices permission to reach the TV."
        PointerLinkState.UNSUPPORTED, PointerLinkState.NEEDS_PAIRING, PointerLinkState.READY ->
            "The pointer isn't set up" to "Set the air mouse up again to use it with this TV."
    }
    Column(
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        when (link) {
            PointerLinkState.BLUETOOTH_OFF -> TextButton(onClick = actions.onTurnOnBluetooth) { Text("Turn on Bluetooth") }
            PointerLinkState.IDLE -> TextButton(onClick = actions.onAllowBluetooth) { Text("Allow") }
            PointerLinkState.BLOCKED -> Unit
            // Also the way out when the TV has forgotten the phone, which the phone can't tell from a TV that is off
            else -> TextButton(onClick = actions.onSetUpAgain) { Text("Set up again") }
        }
    }
}

/** One line in the pad's corner saying how the Bluetooth link is doing. */
@Composable
private fun LinkStatus(link: PointerLinkState, modifier: Modifier = Modifier) {
    val (dot, text) = when (link) {
        PointerLinkState.READY -> Online to "Bluetooth linked"
        PointerLinkState.CONNECTING -> Amber to "Bluetooth connecting"
        else -> return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One of the pointer's buttons. Unlike a remote key it has no tap or long press of its own: the
 * button goes down on the TV when the finger does and comes up with it, so holding it and moving
 * the pointer is a drag, and how long counts as a long press is left to the TV.
 */
@Composable
private fun PointerKey(
    button: PointerButton,
    description: String,
    enabled: Boolean,
    onButton: (PointerButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnButton by rememberUpdatedState(onButton)
    val view = LocalView.current
    KeySurface(
        enabled = enabled,
        pressed = pressed,
        held = false,
        shape = RoundedCornerShape(18.dp),
        container = container,
        border = null,
        modifier = modifier
            .height(58.dp)
            .semantics {
                contentDescription = description
                if (enabled) {
                    onClick {
                        currentOnButton(button, true)
                        currentOnButton(button, false)
                        true
                    }
                }
            }
            .pointerInput(button, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentOnButton(button, true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        // Also when the press is cancelled, or the TV would be left with the button held
                        currentOnButton(button, false)
                        pressed = false
                    }
                })
            },
        content = content
    )
}

@Composable
private fun PadPreview(link: PointerLinkState, hasGyroscope: Boolean = true) {
    AirMouseTVRemoteTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            AirMousePad(link, hasGyroscope, AirPadActions(), Modifier.fillMaxWidth().height(380.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, name = "Air mouse: ready")
@Composable
private fun AirMouseReadyPreview() = PadPreview(PointerLinkState.READY)

@Preview(showBackground = true, widthDp = 390, name = "Air mouse: no gyroscope")
@Composable
private fun AirMouseNoGyroscopePreview() = PadPreview(PointerLinkState.READY, hasGyroscope = false)

@Preview(showBackground = true, widthDp = 390, name = "Air mouse: connecting")
@Composable
private fun AirMouseConnectingPreview() = PadPreview(PointerLinkState.CONNECTING)

@Preview(showBackground = true, widthDp = 390, name = "Air mouse: Bluetooth off")
@Composable
private fun AirMouseBluetoothOffPreview() = PadPreview(PointerLinkState.BLUETOOTH_OFF)
