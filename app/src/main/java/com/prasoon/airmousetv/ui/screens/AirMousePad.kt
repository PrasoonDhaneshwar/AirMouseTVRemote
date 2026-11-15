package com.prasoon.airmousetv.ui.screens

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.airmouse.model.AirMouseSettings
import com.prasoon.airmousetv.airmouse.model.AirMouseUiState
import com.prasoon.airmousetv.airmouse.model.PointerButton
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.airmouse.model.VolumeButtons
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Amber
import com.prasoon.airmousetv.ui.theme.Faint
import com.prasoon.airmousetv.ui.theme.Hairline
import com.prasoon.airmousetv.ui.theme.PadSurface
import com.prasoon.airmousetv.ui.theme.ScreenBlack
import com.prasoon.airmousetv.ui.theme.SheetSurface
import com.prasoon.airmousetv.ui.theme.SurfaceHigh
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Distance dragged, in dp, that turns the TV's wheel by one notch: about a finger's width, like a notch on a real wheel. */
private const val SCROLL_DP_PER_NOTCH = 24f

/**
 * The longest a touch can last and still be a tap. Longer than that with air pointing on, and the
 * finger is holding the pointer still to re-aim, which must not end in a click.
 */
private const val TAP_MAX_MS = 250L

/**
 * How long a linked TV gets to reconnect before the pad offers to set it up again. A TV that is on
 * and in range connects within a few seconds; one that is off never will, and the user doesn't need
 * a button for that. The offer is for the rare TV that has forgotten the phone.
 */
private const val SET_UP_AGAIN_AFTER_MS = 30_000L

/** One full ripple of the reconnecting waves, and the gap between one ring starting and the next. */
private const val WAVE_MS = 2400
private const val WAVE_STAGGER_MS = 800

/** What the air mouse pad tells the rest of the app; all optional, so previews need none of them. */
internal class AirPadActions(
    /** A finger came down on the pad (true) or left it, with air pointing on: the pointer holds still meanwhile. */
    val onPress: (Boolean) -> Unit = {},
    /** The finger slid by this many dp, over this many seconds, with the pad used as a mouse pad. */
    val onSlide: (dxDp: Float, dyDp: Float, dtSeconds: Float) -> Unit = { _, _, _ -> },
    val onButton: (PointerButton, down: Boolean) -> Unit = { _, _ -> },
    /** Scroll by this many wheel notches, up being positive. */
    val onScroll: (Float) -> Unit = {},
    val onRecenter: () -> Unit = {},
    /** The Air pointing switch was turned on or off. */
    val onAirPointing: (Boolean) -> Unit = {},
    val onAllowBluetooth: () -> Unit = {},
    val onTurnOnBluetooth: () -> Unit = {},
    val onSetUpAgain: () -> Unit = {},
    /** The offer shown before setup was accepted. */
    val onSetUp: () -> Unit = {},
)

/**
 * The air mouse: one pad, with Recenter underneath.
 *
 * With Air pointing on, the TV's pointer follows the phone and no finger has to be on the screen.
 * A tap clicks; a finger held down freezes the pointer, so the phone can be brought back to a
 * comfortable position; pointing pauses by itself when the phone is put down. With the switch off
 * the pad is a laptop-style mouse pad: slide to move, tap to click. In both, two fingers or the
 * strip at the edge scroll. A phone without a gyroscope has no switch and is always a mouse pad.
 *
 * There is no Click key. The click belongs where the hand already is: a tap, or the volume button
 * under a finger. Back stays on the remote's Back key.
 *
 * Before the TV has been set up for it, the pad's place is taken by the offer to do that. While the
 * Bluetooth link is down it says what is missing; the keys around it keep working, since they
 * travel over Wi-Fi.
 */
@Composable
internal fun AirMousePad(air: AirMouseUiState, tvName: String, actions: AirPadActions, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    val surface = Modifier.fillMaxWidth().clip(shape).background(PadSurface).border(1.dp, SurfaceHigh, shape)
    if (!air.linked) {
        Box(modifier.then(surface)) { Offer(actions.onSetUp) }
        return
    }
    val ready = air.link == PointerLinkState.READY
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).then(surface)) {
            when (air.link) {
                PointerLinkState.READY -> PointingSurface(air.settings, air.hasGyroscope, actions)
                PointerLinkState.CONNECTING -> Reconnecting(tvName, actions.onSetUpAgain)
                else -> LinkNotice(air.link, actions)
            }
        }
        RecenterKey(enabled = ready, onClick = actions.onRecenter)
    }
}

/** The working surface: takes the taps, holds and slides, and carries the switch, the scroll strip and the volume hint. */
@Composable
private fun BoxScope.PointingSurface(settings: AirMouseSettings, hasGyroscope: Boolean, actions: AirPadActions) {
    val currentActions by rememberUpdatedState(actions)
    val view = LocalView.current
    val airPointing = settings.airPointing && hasGyroscope
    var holding by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = if (airPointing) {
                    "Air mouse pad. Move the phone to move the pointer on the TV. Double-tap to click."
                } else {
                    "Mouse pad. Slide a finger to move the pointer on the TV. Double-tap to click."
                }
                onClick {
                    currentActions.onButton(PointerButton.PRIMARY, true)
                    currentActions.onButton(PointerButton.PRIMARY, false)
                    true
                }
            }
            .pointerInput(airPointing) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var travelled = Offset.Zero
                    var sliding = false
                    var scrolling = false
                    var lastMs = down.uptimeMillis
                    holding = true
                    // Before anything else: the finger landing has already jolted the phone
                    if (airPointing) currentActions.onPress(true)
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

                            // A second finger means scrolling, and stays scrolling until every finger has
                            // lifted, or the finger that lifts last would drag the pointer along
                            if (fingers.size >= 2) scrolling = true
                            if (scrolling) {
                                // Like a touch screen: the page follows the fingers, so fingers moving down turn the wheel up
                                if (fingers.size >= 2) currentActions.onScroll(delta.y / SCROLL_DP_PER_NOTCH.dp.toPx())
                            } else {
                                travelled += delta
                                // A resting finger is never perfectly still. Until it has clearly moved it is
                                // a tap or a hold, and its wobble must not reach the TV
                                if (!sliding && travelled.getDistance() > slop) sliding = true
                                if (sliding && !airPointing) currentActions.onSlide(delta.x.toDp().value, delta.y.toDp().value, dt)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (!sliding && !scrolling && lastMs - down.uptimeMillis <= TAP_MAX_MS) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            currentActions.onButton(PointerButton.PRIMARY, true)
                            currentActions.onButton(PointerButton.PRIMARY, false)
                        }
                    } finally {
                        // Also runs when the gesture is cancelled (the link dropped, the screen went away):
                        // the pointer must never be left frozen by a finger that is no longer there
                        holding = false
                        if (airPointing) currentActions.onPress(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (airPointing) {
                val frozen = holding
                PointerRing(frozen)
                Text(
                    if (frozen) "Holding the pointer still" else "Move the phone, the pointer follows",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center
                )
                Text(
                    if (frozen) "Lift your finger to point again" else "Tap to click · hold to freeze and re-aim",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                // Always laid out, so the ring doesn't jump while a finger is down
                Text(
                    "Pauses when the phone is put down",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (frozen) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text("Slide to move the pointer", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(
                    "Tap to click · two fingers to scroll",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    if (hasGyroscope) {
        AirPointingSwitch(
            on = settings.airPointing,
            onChange = { currentActions.onAirPointing(it) },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )
    }
    val volumeHint = volumeHint(settings).takeIf { airPointing }
    ScrollStrip(
        onScroll = { currentActions.onScroll(it) },
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 60.dp, bottom = if (volumeHint != null) 56.dp else 16.dp)
    )
    if (volumeHint != null) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .background(SheetSurface, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)
            )
            Text(volumeHint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** What the volume buttons do right now, in the pad's words; null when they are left to the TV's volume. */
private fun volumeHint(settings: AirMouseSettings): String? = when (settings.volumeButtons) {
    VolumeButtons.CLICK_AND_BACK -> "Vol + clicks · Vol − is Back"
    VolumeButtons.SWAPPED -> "Vol − clicks · Vol + is Back"
    VolumeButtons.TV_VOLUME -> null
}

/** The pointer in a ring: an outline while the pointer follows the phone, filled while a finger holds it still. */
@Composable
private fun PointerRing(frozen: Boolean) {
    val teal = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(teal.copy(alpha = if (frozen) 0.16f else 0f), tween(120), label = "ring")
    Box(
        modifier = Modifier
            .size(132.dp)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawCircle(teal.copy(alpha = 0.06f), radius = size.minDimension / 2 + 14.dp.toPx())
                drawCircle(fill)
                drawCircle(teal.copy(alpha = if (frozen) 1f else 0.5f), radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.NearMe, contentDescription = null, tint = teal, modifier = Modifier.size(38.dp).graphicsLayer { rotationZ = -90f })
    }
}

/**
 * The switch between pointing with the phone and using the pad as a mouse pad. It sits on the pad
 * because it is flipped mid-use: pointing from the sofa, a mouse pad with the phone on the armrest.
 */
@Composable
private fun AirPointingSwitch(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val teal = MaterialTheme.colorScheme.primary
    val track by animateColorAsState(if (on) teal else SurfaceHigh, tween(120), label = "switch")
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(SheetSurface)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(44.dp, 26.dp).background(track, CircleShape).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(20.dp).background(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
        Text("Air pointing", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
    }
}

/**
 * A strip to drag up and down, for scrolling with one finger. Like a mouse wheel, dragging up
 * scrolls up. Each notch sent ticks, so it can be used without looking.
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
                    // Consumed, so the pad underneath doesn't take this finger for a tap or a hold
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

/**
 * The pad's one key: brings a pointer lost at an edge back to about the middle. A mouse can only be
 * told "move by this much", so the pointer is pushed into a corner and walked back from there.
 */
@Composable
private fun RecenterKey(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    KeySurface(
        enabled = enabled,
        pressed = pressed,
        held = false,
        shape = RoundedCornerShape(18.dp),
        container = MaterialTheme.colorScheme.surfaceVariant,
        border = null,
        // The key draws its own pressed look, so the ripple would only double it
        modifier = Modifier.fillMaxWidth().height(58.dp).clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Recenter", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
        }
    }
}

/** True if the user has switched animations off in Android's settings, where a looping animation should hold still. */
@Composable
private fun animationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * A linked TV that isn't connected: it is off, out of range, or just slow. Nothing here is the
 * user's to fix by pairing again, so there is no button for that until the TV has stayed away for
 * [SET_UP_AGAIN_AFTER_MS], and then only a quiet one.
 */
@Composable
private fun BoxScope.Reconnecting(tvName: String, onSetUpAgain: () -> Unit) {
    var waitedLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SET_UP_AGAIN_AFTER_MS)
        waitedLong = true
    }
    Column(
        modifier = Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Waves()
        Text("Reconnecting to $tvName…", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "Move closer to the TV, or check it's on. The keys below still work over Wi-Fi.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (waitedLong) {
            Text(
                "Still not connecting?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = onSetUpAgain) {
                Text(
                    "Set it up again",
                    style = MaterialTheme.typography.titleSmall.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    LinkStatus(Amber, "Bluetooth reconnecting", Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp))
}

/** Three rings spreading from a Bluetooth mark, one after another: something is being reached for. Still when animations are off. */
@Composable
private fun Waves() {
    val teal = MaterialTheme.colorScheme.primary
    val still = animationsOff()
    val transition = rememberInfiniteTransition(label = "waves")
    val phases = List(3) { ring ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(WAVE_MS, easing = LinearEasing), RepeatMode.Restart, StartOffset(ring * WAVE_STAGGER_MS)),
            label = "wave $ring"
        )
    }
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val reach = size.minDimension / 2
            phases.forEachIndexed { ring, phase ->
                // Frozen at three even sizes when animations are off, so it still reads as waves
                val t = if (still) (ring + 1) / 3f else phase.value
                drawCircle(
                    teal.copy(alpha = if (still) 0.25f else 0.75f * (1f - t)),
                    radius = reach * (0.35f + 0.65f * t),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = teal, modifier = Modifier.size(26.dp))
        }
    }
}

/** What stands in for the surface while the pointer can't be used for a reason the user can do something about. */
@Composable
private fun BoxScope.LinkNotice(link: PointerLinkState, actions: AirPadActions) {
    val (title, body) = when (link) {
        PointerLinkState.BLUETOOTH_OFF -> "Bluetooth is off" to
            "The pointer travels over Bluetooth. The keys below still work over Wi-Fi."
        PointerLinkState.BLOCKED -> "The pointer is in use elsewhere" to
            "Another app is using this phone as a Bluetooth mouse or keyboard. Close it, then come back."
        PointerLinkState.IDLE -> "Bluetooth isn't allowed" to
            "The pointer needs the Nearby devices permission to reach the TV."
        else -> "The pointer isn't set up" to "Set the air mouse up again to use it with this TV."
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
            else -> TextButton(onClick = actions.onSetUpAgain) { Text("Set up again") }
        }
    }
}

/** One line in the pad's corner saying how the Bluetooth link is doing. */
@Composable
private fun LinkStatus(dot: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * What the pad area shows for a TV that hasn't been set up: what the air mouse is, and the way in.
 * It replaces the pad rather than opening over it, so the keys around it stay in reach and a tap
 * on the mode switch never leads to a dead end.
 */
@Composable
private fun Offer(onSetUp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PointAtTvDrawing(Modifier.size(220.dp, 110.dp))
        ProChip(Modifier.padding(top = 12.dp))
        Text(
            "Point your phone at the TV",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "A pointer on the TV follows your hand. Uses Bluetooth, nothing to install on the TV.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        PrimaryAction("Set up air mouse", onSetUp, Modifier.padding(top = 16.dp))
        Text(
            "One-time setup",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** The small outlined "PRO" mark on everything that offers the air mouse before it is set up. */
@Composable
internal fun ProChip(modifier: Modifier = Modifier) {
    val teal = MaterialTheme.colorScheme.primary
    Text(
        "PRO",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = teal,
        modifier = modifier.border(1.dp, teal, CircleShape).padding(horizontal = 6.dp)
    )
}

/** A phone tilted towards a TV with a pointer on its screen, and the dotted line between them: the air mouse in one picture. */
@Composable
internal fun PointAtTvDrawing(modifier: Modifier = Modifier) {
    val teal = MaterialTheme.colorScheme.primary
    val phoneEdge = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // The TV, to the right
        val tvSize = Size(w * 0.64f, h * 0.75f)
        val tvAt = Offset(w - tvSize.width, h * 0.04f)
        val corner = CornerRadius(8.dp.toPx())
        drawRoundRect(ScreenBlack, tvAt, tvSize, corner)
        drawRoundRect(Hairline, tvAt, tvSize, corner, style = Stroke(3.dp.toPx()))
        // Its pointer
        val tip = Offset(tvAt.x + tvSize.width * 0.5f, tvAt.y + tvSize.height * 0.32f)
        val unit = h * 0.012f
        val pointer = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + 6 * unit, tip.y + 16 * unit)
            lineTo(tip.x + 8.5f * unit, tip.y + 9.5f * unit)
            lineTo(tip.x + 15 * unit, tip.y + 7 * unit)
            close()
        }
        drawPath(pointer, Color.White)
        // The phone, bottom left, leaning towards the TV
        val phoneSize = Size(w * 0.155f, h * 0.58f)
        val phoneAt = Offset(w * 0.03f, h * 0.36f)
        rotate(-24f, pivot = Offset(phoneAt.x + phoneSize.width / 2, phoneAt.y + phoneSize.height / 2)) {
            drawRoundRect(SheetSurface, phoneAt, phoneSize, corner)
            drawRoundRect(phoneEdge, phoneAt, phoneSize, corner, style = Stroke(2.5.dp.toPx()))
        }
        // And what joins them
        val beam = Path().apply {
            moveTo(w * 0.18f, h * 0.5f)
            cubicTo(w * 0.3f, h * 0.2f, w * 0.45f, h * 0.16f, w * 0.6f, h * 0.25f)
        }
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
        drawPath(beam, teal, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes))
    }
}

private val previewAir = AirMouseUiState(available = true, linked = true, link = PointerLinkState.READY, hasGyroscope = true)

@Composable
private fun PadPreview(air: AirMouseUiState) {
    AirMouseTVRemoteTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            AirMousePad(air, "Living Room TV", AirPadActions(), Modifier.fillMaxWidth().height(420.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, name = "Air pointing on")
@Composable
private fun AirPointingOnPreview() = PadPreview(previewAir)

@Preview(showBackground = true, widthDp = 390, name = "Air pointing off: mouse pad")
@Composable
private fun AirPointingOffPreview() = PadPreview(previewAir.copy(settings = AirMouseSettings(airPointing = false)))

@Preview(showBackground = true, widthDp = 390, name = "Volume buttons left to the TV")
@Composable
private fun AirVolumeForTvPreview() = PadPreview(previewAir.copy(settings = AirMouseSettings(volumeButtons = VolumeButtons.TV_VOLUME)))

@Preview(showBackground = true, widthDp = 390, name = "No gyroscope: mouse pad only")
@Composable
private fun AirNoGyroscopePreview() = PadPreview(previewAir.copy(hasGyroscope = false))

@Preview(showBackground = true, widthDp = 390, name = "Reconnecting")
@Composable
private fun AirReconnectingPreview() = PadPreview(previewAir.copy(link = PointerLinkState.CONNECTING))

@Preview(showBackground = true, widthDp = 390, name = "Bluetooth off")
@Composable
private fun AirBluetoothOffPreview() = PadPreview(previewAir.copy(link = PointerLinkState.BLUETOOTH_OFF))

@Preview(showBackground = true, widthDp = 390, name = "Offer before setup")
@Composable
private fun AirOfferPreview() = PadPreview(previewAir.copy(linked = false, link = PointerLinkState.IDLE))
