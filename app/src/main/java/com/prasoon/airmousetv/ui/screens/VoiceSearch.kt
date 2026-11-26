package com.prasoon.airmousetv.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.data.model.VoiceError
import com.prasoon.airmousetv.data.model.VoiceState
import com.prasoon.airmousetv.presentation.LEVEL_BARS
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.AmberText
import com.prasoon.airmousetv.ui.theme.Faint
import com.prasoon.airmousetv.ui.theme.LiveRed
import com.prasoon.airmousetv.ui.theme.LiveRedText
import com.prasoon.airmousetv.ui.theme.Muted
import com.prasoon.airmousetv.ui.theme.Online
import com.prasoon.airmousetv.ui.theme.SheetSurface
import com.prasoon.airmousetv.ui.theme.SurfaceHigh
import com.prasoon.airmousetv.ui.theme.TipSurface
import kotlinx.coroutines.launch

/**
 * How long the Ask key must be held before the microphone turns on. A shorter touch counts as a tap
 * and gets a hint, so a stray brush of the thumb never opens the TV's assistant.
 */
private const val VOICE_HOLD_MS = 250L

/** How far the finger may stray outside the Ask key before letting go cancels: a thumb wobbles while its owner talks. */
private val CANCEL_SLOP = 24.dp

/** Set once the microphone permission has been asked for, to tell "never asked" from "turned down for good". */
private const val PREF_MIC_ASKED = "mic_asked"

/** Speech sits far below full scale, so the bars are boosted by this much to move well while someone talks. */
private const val LEVEL_BOOST = 1.8f

/** What the Ask key does on the connected TV. */
internal enum class AskMode {
    /** Hold to talk: the voice streams to the TV's assistant. */
    VOICE,
    /** The TV has no voice: a tap opens the phone's speech to text, which types into the TV's search box. */
    TYPE
}

/**
 * The round Ask key at the right end of the key row. In [AskMode.VOICE] it is held to talk: past
 * [VOICE_HOLD_MS] [onHoldStart] turns the microphone on, sliding off the key arms a cancel
 * ([onCancelArmed]), and letting go calls [onRelease] with whether it was cancelled. A quicker touch is
 * [onShortTap]. Screen readers can't hold, so their double-tap is [onTapToggle]: it starts a search,
 * and on the Stop key it ends one. In [AskMode.TYPE] the key is dimmed with an "Aa" mark and a tap is [onTapToggle].
 */
@Composable
internal fun AskKey(
    mode: AskMode,
    voice: VoiceState,
    enabled: Boolean,
    size: Dp,
    onHoldStart: () -> Unit,
    onCancelArmed: (Boolean) -> Unit,
    onRelease: (cancelled: Boolean) -> Unit,
    onShortTap: () -> Unit,
    onTapToggle: () -> Unit
) {
    val listening = voice as? VoiceState.Listening
    val armed = listening?.cancelArmed == true
    val tapLive = listening?.tapMode == true
    val live = listening != null && !armed
    val view = LocalView.current
    val holdStart by rememberUpdatedState(onHoldStart)
    val cancelArmed by rememberUpdatedState(onCancelArmed)
    val release by rememberUpdatedState(onRelease)
    val shortTap by rememberUpdatedState(onShortTap)
    val toggle by rememberUpdatedState(onTapToggle)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed || live) 0.94f else 1f, tween(if (pressed) 80 else 150), label = "ask")
    val label = when {
        mode == AskMode.TYPE -> "Search by voice. Types into the TV search box"
        tapLive -> "Stop and send"
        armed -> "Voice search, release to cancel"
        listening != null -> "Voice search, listening"
        else -> "Ask your TV. Hold to talk"
    }

    // The outer box doesn't clip, so the live rings and the badges can reach past the key's edge
    Box(
        Modifier
            .size(size)
            .graphicsLayer { alpha = if (!enabled) 0.4f else if (mode == AskMode.TYPE) 0.6f else 1f }
            .drawBehind {
                if (live) {
                    val r = this.size.minDimension / 2 * scale
                    drawCircle(LiveRed.copy(alpha = 0.08f), radius = r + 22.dp.toPx())
                    drawCircle(LiveRed.copy(alpha = 0.22f), radius = r + 10.dp.toPx())
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (live) LiveRed else MaterialTheme.colorScheme.surfaceVariant)
                .drawBehind {
                    if (armed) {
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            Muted, radius = this.size.minDimension / 2 - stroke / 2,
                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                        )
                    }
                }
                // The touch handling below is invisible to accessibility services, so say what a double-tap does
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    if (enabled) onClick { toggle(); true } else disabled()
                }
                .then(
                    when {
                        !enabled -> Modifier
                        // A search started by a tap ends with a tap on Stop; a TV without voice opens the typing sheet
                        mode == AskMode.TYPE || tapLive -> Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { toggle() }
                        else -> Modifier.pointerInput(Unit) {
                            val slop = CANCEL_SLOP.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                pressed = true
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                var holding = false
                                var ended = false
                                try {
                                    val releasedEarly = withTimeoutOrNull(VOICE_HOLD_MS) { awaitRelease(down.id) } != null
                                    if (releasedEarly) {
                                        shortTap()
                                        return@awaitEachGesture
                                    }
                                    holding = true
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    holdStart()
                                    var outside = false
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        val p = change.position
                                        val nowOutside = p.x < -slop || p.y < -slop ||
                                            p.x > this.size.width + slop || p.y > this.size.height + slop
                                        if (nowOutside != outside) {
                                            outside = nowOutside
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            cancelArmed(outside)
                                        }
                                    }
                                    ended = true
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    release(outside)
                                } finally {
                                    pressed = false
                                    // The gesture was cut short (the key went away or got disabled): nothing is sent
                                    if (holding && !ended) release(true)
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (tapLive) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    Icons.Default.Mic, contentDescription = null,
                    tint = when {
                        live -> Color.White
                        armed -> Muted
                        mode == AskMode.TYPE -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (armed) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).size(24.dp).background(Faint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (mode == AskMode.TYPE) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(26.dp).background(SurfaceHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("Aa", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TipSurface, letterSpacing = 0.sp)
            }
        }
    }
}

/** Waits until the pointer [id] lifts or goes away. */
private suspend fun AwaitPointerEventScope.awaitRelease(id: PointerId) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
    }
}

/**
 * What covers the pad while a voice search is going: the listening panel, the "Sent" card or an
 * error. The keys around the pad stay where they are, so the thumb on the Ask key never moves.
 */
@Composable
internal fun VoiceOverlay(
    voice: VoiceState,
    tvName: String,
    onStopAndSend: () -> Unit,
    onCancel: () -> Unit,
    onTryAgain: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (voice) {
        VoiceState.Idle -> Unit
        is VoiceState.Listening -> ListeningPanel(voice, tvName, onStopAndSend, onCancel, modifier)
        VoiceState.Sent -> Box(modifier, contentAlignment = Alignment.TopCenter) { SentCard(tvName, onDismiss) }
        is VoiceState.Failed -> ErrorPanel(voice.error, tvName, onTryAgain, onDismiss, modifier)
    }
}

/** The rounded panel the voice states are drawn on, at most the height the design gives it. */
@Composable
private fun VoicePanel(
    modifier: Modifier,
    dashed: Boolean = false,
    onTap: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 330.dp)
            .fillMaxHeight()
            .shadow(16.dp, shape)
            .clip(shape)
            .background(SheetSurface)
            .then(
                if (dashed) Modifier.drawBehind {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        Faint, topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                    )
                } else Modifier.border(1.dp, SurfaceHigh, shape)
            )
            // Taps land on the panel, not on the pad under it
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        content = content
    )
}

@Composable
private fun ListeningPanel(
    voice: VoiceState.Listening,
    tvName: String,
    onStopAndSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier
) {
    val armed = voice.cancelArmed
    VoicePanel(modifier, dashed = armed) {
        Text(
            if (armed) "CANCEL" else "LISTENING",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = if (armed) MaterialTheme.colorScheme.onSurfaceVariant else LiveRedText
        )
        LevelBars(voice.levels, if (armed) Faint else LiveRedText, if (armed) 0.5f else 1f)
        Text(
            if (armed) "Release to cancel" else "Speak now",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            when {
                armed -> "Nothing will be sent. Slide back onto the key to keep talking."
                voice.tapMode -> "Tap Stop when you're done. Your words go to $tvName."
                else -> "Release to send to $tvName"
            },
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (voice.tapMode) {
            Button(
                onClick = onStopAndSend,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Stop and send", modifier = Modifier.padding(start = 8.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Cancel", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (!armed) {
            Row(
                Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                Text("Slide off the key to cancel", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

/** The latest sound levels as a row of bars, newest on the right; quiet frames still show as dots. */
@Composable
private fun LevelBars(levels: List<Float>, color: Color, heightScale: Float) {
    Canvas(Modifier.width((LEVEL_BARS * 9 - 4).dp).height(64.dp).semantics { }) {
        val bar = 5.dp.toPx()
        val gap = 4.dp.toPx()
        val min = 6.dp.toPx()
        val padded = List((LEVEL_BARS - levels.size).coerceAtLeast(0)) { 0f } + levels.takeLast(LEVEL_BARS)
        padded.forEachIndexed { i, level ->
            val h = (size.height * (level * LEVEL_BOOST).coerceAtMost(1f) * heightScale).coerceAtLeast(min)
            drawRoundRect(
                color, topLeft = Offset(i * (bar + gap), (size.height - h) / 2),
                size = Size(bar, h), cornerRadius = CornerRadius(bar / 2)
            )
        }
    }
}

/** A short note at the top of the pad that the voice went to the TV, where the results appear. */
@Composable
private fun SentCard(tvName: String, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(SheetSurface)
            .border(1.dp, SurfaceHigh, shape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).background(Online.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Online, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Sent to $tvName", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Results on your TV", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Why the search didn't reach the TV, and what to do. A tap anywhere on it closes it. */
@Composable
private fun ErrorPanel(error: VoiceError, tvName: String, onTryAgain: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    val (icon, title, body) = when (error) {
        VoiceError.WIFI_LOST -> Triple(
            Icons.Default.WifiOff, "Lost the TV while you were talking",
            "Nothing was sent. The app is reconnecting; hold the mic again once the TV is back."
        )
        VoiceError.MIC_BUSY -> Triple(
            Icons.Default.MicOff, "The microphone is in use",
            "Another app, like a call or a recorder, is using it. Finish there, then hold the mic again."
        )
        VoiceError.TV_NO_ANSWER -> Triple(
            Icons.Default.Tv, "$tvName didn't answer",
            "Your words may not have reached it. Check the TV is on, then try again."
        )
    }
    VoicePanel(modifier, onTap = onDismiss) {
        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = AmberText, modifier = Modifier.size(26.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (error != VoiceError.MIC_BUSY) {
            Button(
                onClick = onTryAgain,
                modifier = Modifier.padding(top = 6.dp).height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) { Text("Try again", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ===================== Microphone permission =====================

/** Asks for the microphone, with a sheet before Android's own dialog that says where the voice goes. */
internal class MicPermission(
    /** True if the app may record now. */
    val granted: () -> Boolean,
    /** Shows the sheet (or, if the permission was turned down for good, the way to settings), then runs the action once granted. */
    val ask: (onGranted: () -> Unit) -> Unit
)

private enum class MicSheet { EXPLAIN, TURNED_OFF }

/** The activity behind [context], which Android needs to say whether it would still show its permission dialog. */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Remembers a [MicPermission] for voice search on [tvName], and shows its sheets while they are open. */
@Composable
internal fun rememberMicPermission(tvName: String): MicPermission {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("remote_ui", Context.MODE_PRIVATE) }
    var sheet by remember { mutableStateOf<MicSheet?>(null) }
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.edit { putBoolean(PREF_MIC_ASKED, true) }
        if (granted) afterGrant?.invoke()
        afterGrant = null
    }
    val granted = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    when (sheet) {
        MicSheet.EXPLAIN -> MicPermissionSheet(
            turnedOff = false, tvName = tvName,
            onContinue = {
                sheet = null
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { sheet = null; afterGrant = null }
        )
        MicSheet.TURNED_OFF -> MicPermissionSheet(
            turnedOff = true, tvName = tvName,
            onContinue = {
                sheet = null
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onDismiss = { sheet = null; afterGrant = null }
        )
        null -> Unit
    }
    return MicPermission(granted) { action ->
        if (granted()) {
            action()
        } else {
            afterGrant = action
            // Android stops showing its dialog once the user has said no twice (or ticked "don't ask again");
            // it then reports no rationale, which before the first ask means nothing
            val activity = context.findActivity()
            val turnedOff = prefs.getBoolean(PREF_MIC_ASKED, false) && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            sheet = if (turnedOff) MicSheet.TURNED_OFF else MicSheet.EXPLAIN
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicPermissionSheet(turnedOff: Boolean, tvName: String, onContinue: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(36.dp, 4.dp).background(Faint, CircleShape)) }
    ) {
        MicPermissionContent(turnedOff, tvName, onContinue, onDismiss)
    }
}

@Composable
private fun MicPermissionContent(turnedOff: Boolean, tvName: String, onContinue: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (turnedOff) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null,
                tint = if (turnedOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp)
            )
        }
        Text(
            if (turnedOff) "The microphone is off for this app" else "Talk to your TV",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            if (turnedOff) "Android won't ask again. To talk to $tvName, turn on the microphone in this app's settings: Permissions, then Microphone."
            else "Hold the mic key and say what you want to watch. The TV's Google Assistant shows the results.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!turnedOff) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "Your voice goes only to $tvName on your Wi-Fi. This app doesn't record or keep it.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
                )
            }
            Text(
                "Android will ask to allow the microphone next.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (turnedOff) "Open settings" else "Continue", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (turnedOff) Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Not now", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===================== TV without voice: the phone's speech to text =====================

/** True if this phone has a speech recognizer the app can use; an AOSP phone without Google's may have none. */
internal fun Context.hasSpeechRecognizer(): Boolean = SpeechRecognizer.isRecognitionAvailable(this)

/**
 * For a TV without voice: the phone turns speech into text and types it into the TV's search box.
 * Opening it presses the TV's search key if no text box has focus yet, then whatever is heard is
 * typed into the box once the TV reports it. With [youTube] the words go to YouTube's search
 * instead, since YouTube's own keyboard can't be typed into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeechSearchSheet(viewModel: RemoteViewModel, tvName: String, youTube: Boolean, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val field = uiState.imeField
    val context = LocalContext.current
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var listening by remember { mutableStateOf(false) }
    // The text box typing started in; the TV is told about each change once one has focus
    var typing by remember { mutableStateOf(false) }

    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }
    val heard: (String, Boolean) -> Unit = { words, final ->
        text = TextFieldValue(words, selection = TextRange(words.length))
        if (final) {
            listening = false
            if (typing) viewModel.typeText(words)
        }
    }
    val currentHeard by rememberUpdatedState(heard)
    val listen = {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { currentHeard(it, true) }
                listening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { currentHeard(it, false) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
        listening = true
    }

    LaunchedEffect(Unit) {
        // The search key opens the TV's own search, which puts a text box in focus to type into
        if (!youTube && field == null) viewModel.sendKey(TvKey.SEARCH)
        listen()
    }
    // Once the TV has a text box, start typing into it, and catch up with anything heard before it came
    LaunchedEffect(field != null) {
        if (field != null && !youTube && !typing) {
            typing = true
            viewModel.beginTextEntry(field.value)
            if (text.text.isNotBlank() && !listening) viewModel.typeText(text.text)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    val send = {
        if (text.text.isNotBlank()) {
            if (youTube) viewModel.searchYouTube(text.text) else viewModel.submitText()
            close()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(36.dp, 4.dp).background(Faint, CircleShape)) }
    ) {
        SpeechSearchContent(
            tvName = tvName,
            youTube = youTube,
            text = text,
            onTextChange = { new ->
                val changed = new.text != text.text
                text = new
                if (changed && typing) viewModel.typeText(new.text)
            },
            onTv = typing && field?.value == text.text && text.text.isNotEmpty(),
            listening = listening,
            onSend = send,
            onListen = { if (!listening) listen() else recognizer.stopListening() }
        )
    }
}

@Composable
private fun SpeechSearchContent(
    tvName: String,
    youTube: Boolean,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onTv: Boolean,
    listening: Boolean,
    onSend: () -> Unit,
    onListen: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (youTube) "Search YouTube" else "Search"} · $tvName",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (onTv) StatusPill(TvTextStatus.ON_TV)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, letterSpacing = 0.sp),
                    maxLines = 3,
                    shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (text.text.isNotEmpty()) {
                            IconButton(onClick = { onTextChange(TextFieldValue("")) }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear text", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = if (youTube) ImeAction.Search else ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }, onSearch = { onSend() })
                )
                FilledIconButton(onClick = onSend, enabled = text.text.isNotBlank(), modifier = Modifier.size(52.dp)) {
                    Icon(
                        if (youTube) Icons.Default.Search else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (youTube) "Search on TV" else "Send to TV",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                Text(
                    "This TV has no voice search, so your phone turns what you say into text and types it here.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Where the keyboard would be: the phone's speech to text
        Column(
            Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .drawBehind {
                        if (listening) {
                            drawCircle(LiveRed.copy(alpha = 0.07f), radius = size.minDimension / 2 + 26.dp.toPx())
                            drawCircle(LiveRed.copy(alpha = 0.18f), radius = size.minDimension / 2 + 12.dp.toPx())
                        }
                    }
                    .clip(CircleShape)
                    .background(if (listening) LiveRed else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClickLabel = if (listening) "Stop listening" else "Speak again", onClick = onListen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text(
                if (listening) "Speak now" else "Tap the mic to speak again",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = TipSurface
            )
            Text("The phone's speech to text", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

// ===================== Previews =====================

@Composable
private fun VoicePreview(content: @Composable () -> Unit) {
    AirMouseTVRemoteTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) { content() }
    }
}

private val previewLevels = listOf(0.05f, 0.1f, 0.2f, 0.3f, 0.18f, 0.35f, 0.45f, 0.28f, 0.4f, 0.22f, 0.14f, 0.32f, 0.42f, 0.25f, 0.16f, 0.3f, 0.2f, 0.1f, 0.2f, 0.07f, 0.04f)

@Preview(showBackground = true, widthDp = 390, heightDp = 380, name = "Voice: listening while held")
@Composable
private fun ListeningPreview() = VoicePreview {
    VoiceOverlay(VoiceState.Listening(previewLevels), "Living Room TV", {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 380, name = "Voice: slid off, release to cancel")
@Composable
private fun CancelArmedPreview() = VoicePreview {
    VoiceOverlay(VoiceState.Listening(previewLevels, cancelArmed = true), "Living Room TV", {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 380, name = "Voice: tap to start, tap to stop")
@Composable
private fun TapModePreview() = VoicePreview {
    VoiceOverlay(VoiceState.Listening(previewLevels, tapMode = true), "Living Room TV", {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 140, name = "Voice: sent")
@Composable
private fun SentPreview() = VoicePreview {
    VoiceOverlay(VoiceState.Sent, "Living Room TV", {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 380, name = "Voice: Wi-Fi dropped")
@Composable
private fun WifiLostPreview() = VoicePreview {
    VoiceOverlay(VoiceState.Failed(VoiceError.WIFI_LOST), "Living Room TV", {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 380, name = "Voice: microphone busy")
@Composable
private fun MicBusyPreview() = VoicePreview {
    VoiceOverlay(VoiceState.Failed(VoiceError.MIC_BUSY), "Living Room TV", {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, heightDp = 380, name = "Voice: TV didn't answer")
@Composable
private fun NoAnswerPreview() = VoicePreview {
    VoiceOverlay(VoiceState.Failed(VoiceError.TV_NO_ANSWER), "Living Room TV", {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 390, name = "Voice: microphone permission")
@Composable
private fun MicExplainPreview() = AirMouseTVRemoteTheme {
    Box(Modifier.background(SheetSurface)) { MicPermissionContent(turnedOff = false, tvName = "Living Room TV", onContinue = {}, onDismiss = {}) }
}

@Preview(showBackground = true, widthDp = 390, name = "Voice: microphone turned off for good")
@Composable
private fun MicTurnedOffPreview() = AirMouseTVRemoteTheme {
    Box(Modifier.background(SheetSurface)) { MicPermissionContent(turnedOff = true, tvName = "Living Room TV", onContinue = {}, onDismiss = {}) }
}

@Preview(showBackground = true, widthDp = 390, name = "Voice: TV without voice, phone speech to text")
@Composable
private fun SpeechSearchPreview() = AirMouseTVRemoteTheme {
    Box(Modifier.background(SheetSurface).padding(top = 16.dp)) {
        SpeechSearchContent("Living Room TV", false, TextFieldValue("stranger things"), {}, onTv = true, listening = true, onSend = {}, onListen = {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 120, name = "Ask key: idle, live, cancel, stop, no voice")
@Composable
private fun AskKeyStatesPreview() = VoicePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        val noop = {}
        AskKey(AskMode.VOICE, VoiceState.Idle, true, 58.dp, noop, {}, {}, noop, noop)
        AskKey(AskMode.VOICE, VoiceState.Listening(), true, 58.dp, noop, {}, {}, noop, noop)
        AskKey(AskMode.VOICE, VoiceState.Listening(cancelArmed = true), true, 58.dp, noop, {}, {}, noop, noop)
        AskKey(AskMode.VOICE, VoiceState.Listening(tapMode = true), true, 58.dp, noop, {}, {}, noop, noop)
        AskKey(AskMode.TYPE, VoiceState.Idle, true, 58.dp, noop, {}, {}, noop, noop)
    }
}
