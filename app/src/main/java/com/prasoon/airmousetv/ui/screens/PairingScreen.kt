package com.prasoon.airmousetv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.data.api.PAIRING_CODE_LENGTH
import com.prasoon.airmousetv.data.api.normalizePairingCode
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Amber
import com.prasoon.airmousetv.ui.theme.Hairline
import com.prasoon.airmousetv.ui.theme.Offline
import com.prasoon.airmousetv.ui.theme.OfflineText
import com.prasoon.airmousetv.ui.theme.ScreenBlack
import com.prasoon.airmousetv.ui.theme.SheetSurface

/**
 * Shown while connecting to a TV. One drawing of the TV stays in place while what is on it changes:
 * a spinner until the TV asks for its code, then where the code appears, with six boxes to type it
 * into. The code is sent by itself on the sixth character. Cancel is always available.
 */
@Composable
fun PairingScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    PairingContent(
        uiState = uiState,
        onCodeChange = viewModel::updatePairingCode,
        onRetry = viewModel::retryPairing,
        onCancel = viewModel::cancelPairing,
        modifier = modifier
    )
}

/** What the pairing screen is showing; each is a step the connection attempt can be in. */
private enum class PairingStep { CONNECTING, CODE, FAILED, PAIRED }

/** Below this much height above the keyboard the TV drawing is left out, so the code boxes always fit. */
private val MIN_HEIGHT_FOR_DRAWING = 420.dp

@Composable
private fun PairingContent(
    uiState: RemoteUiState,
    onCodeChange: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Once the mode has moved on (the remote is opening, or Cancel was tapped) this screen is still on show
    // for the length of the fade. It keeps drawing the last pairing state: the live one has already been
    // reset, and the tick would turn into a spinner for a quarter of a second on its way out
    val state = rememberLastNonNull(uiState.takeIf { it.mode is RemoteMode.Pairing }) ?: uiState
    val tvName = rememberLastNonNull((state.mode as? RemoteMode.Pairing)?.tv)?.displayName ?: "TV"
    val step = when {
        state.paired -> PairingStep.PAIRED
        state.awaitingCode -> PairingStep.CODE
        state.error != null -> PairingStep.FAILED
        else -> PairingStep.CONNECTING
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        val roomForDrawing = maxHeight >= MIN_HEIGHT_FOR_DRAWING
        val screenWidth = maxWidth
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                // Once paired there is nothing to cancel; the button stays in place so nothing jumps
                IconButton(
                    onClick = onCancel,
                    enabled = step != PairingStep.PAIRED,
                    modifier = Modifier.size(48.dp).alpha(if (step == PairingStep.PAIRED) 0f else 1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel pairing")
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(SCREEN_FADE_MS)) togetherWith fadeOut(tween(SCREEN_FADE_MS)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "pairing step"
            ) { shown ->
                when (shown) {
                    PairingStep.CONNECTING -> TvMessage(
                        title = "Connecting to\n$tvName",
                        // Not "a code will appear": a TV that already trusts this phone shows none
                        body = "A code appears on the TV the first time."
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                    PairingStep.CODE -> EnterCode(
                        tvName = tvName,
                        code = state.pairingCode,
                        rejected = state.codeRejected,
                        error = state.error,
                        onCodeChange = onCodeChange,
                        showDrawing = roomForDrawing,
                        screenWidth = screenWidth
                    )
                    PairingStep.FAILED -> Column(Modifier.fillMaxSize()) {
                        TvMessage(
                            // Covers every way an attempt can end, from no answer to the TV turning it down; the line below says which
                            title = "Couldn't connect to\n$tvName",
                            body = state.error ?: "Make sure it's on and on the same Wi-Fi as this phone.",
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.size(44.dp).background(Amber.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = Amber)
                            }
                        }
                        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryAction("Try again", onRetry)
                            SecondaryAction("Back to list", onCancel)
                        }
                    }
                    PairingStep.PAIRED -> Paired(tvName)
                }
            }
        }
    }
}

private val pairingTitle
    @Composable get() = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp)

/** The TV drawing with [onScreen] on it, a two-line [title] and one line of [body], a third of the way down. */
@Composable
private fun TvMessage(title: String, body: String, modifier: Modifier = Modifier, onScreen: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        TvDrawing(onScreen)
        Spacer(Modifier.height(28.dp))
        Text(title, style = pairingTitle, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** A small TV on its stand, with [onScreen] in the middle of its screen. Decoration: the text beside it says the same. */
@Composable
private fun TvDrawing(onScreen: @Composable () -> Unit) {
    val frame = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
        Box(
            modifier = Modifier.size(width = 210.dp, height = 122.dp).background(ScreenBlack, frame).border(3.dp, Hairline, frame),
            contentAlignment = Alignment.Center
        ) { onScreen() }
        Box(Modifier.size(width = 60.dp, height = 4.dp).background(Hairline, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)))
    }
}

/** The code as it looks on the TV: a card with a line of text and six blanks, ringed in teal to say "look here". */
@Composable
private fun CodeOnTv() {
    val card = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .background(SheetSurface, card)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(width = 70.dp, height = 4.dp).background(Hairline, CircleShape))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(PAIRING_CODE_LENGTH) { Box(Modifier.size(width = 14.dp, height = 18.dp).background(Hairline, RoundedCornerShape(3.dp))) }
        }
    }
}

@Composable
private fun EnterCode(
    tvName: String,
    code: String,
    rejected: Boolean,
    error: String?,
    onCodeChange: (String) -> Unit,
    showDrawing: Boolean,
    screenWidth: Dp
) {
    Column(
        // Scrolls rather than clips if a short screen and large text still leave too little room
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showDrawing) {
            Spacer(Modifier.height(4.dp))
            TvDrawing { CodeOnTv() }
            Spacer(Modifier.height(22.dp))
        }
        Text("Type the code shown\non $tvName", style = pairingTitle, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(26.dp))
        CodeField(code = code, rejected = rejected, onCodeChange = onCodeChange, screenWidth = screenWidth)
        Spacer(Modifier.height(14.dp))
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = OfflineText, modifier = Modifier.size(16.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, color = OfflineText)
            }
        } else {
            Text(
                "Connects as soon as all $PAIRING_CODE_LENGTH are in",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The widest a code box gets; on a narrow phone the six share what width there is. */
private val CODE_BOX_WIDTH = 48.dp
private val CODE_BOX_GAP = 8.dp

/**
 * Six boxes showing [code], filled from a hidden text field so the system keyboard supplies
 * digits and capital letters together. Tapping the boxes brings the keyboard back. A [rejected]
 * code turns the boxes red and shakes them once.
 */
@Composable
private fun CodeField(code: String, rejected: Boolean, onCodeChange: (String) -> Unit, screenWidth: Dp) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Left and right by 8 dp twice, 300 ms in all: "no", without making the user wait
    val shake = remember { Animatable(0f) }
    LaunchedEffect(rejected) {
        if (rejected) {
            view.hapticReject()
            shake.animateTo(0f, keyframes {
                durationMillis = 300
                -8f at 50
                8f at 125
                -8f at 200
                8f at 260
            })
        }
    }

    // Six boxes, the gaps between them, and one more gap splitting the code into two threes
    val gaps = CODE_BOX_GAP * (PAIRING_CODE_LENGTH + 1)
    val boxWidth = minOf(CODE_BOX_WIDTH, (screenWidth - 32.dp - gaps) / PAIRING_CODE_LENGTH)

    BasicTextField(
        value = code,
        onValueChange = { raw ->
            onCodeChange(normalizePairingCode(raw))
        },
        modifier = Modifier.focusRequester(focusRequester),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
        keyboardOptions = KeyboardOptions(
            // Password type makes the keyboard show its number row above the letters; the field is invisible anyway
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.Characters
        ),
        decorationBox = { innerTextField ->
            // The real field must stay composed to hold focus and the keyboard; the boxes are what the user sees
            Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
            Row(
                modifier = Modifier
                    .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                    .semantics { contentDescription = "Pairing code, ${code.length} of $PAIRING_CODE_LENGTH entered" }
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    },
                horizontalArrangement = Arrangement.spacedBy(CODE_BOX_GAP),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PAIRING_CODE_LENGTH) { index ->
                    if (index == PAIRING_CODE_LENGTH / 2) Spacer(Modifier.width(CODE_BOX_GAP))
                    CodeBox(char = code.getOrNull(index), active = index == code.length, rejected = rejected, width = boxWidth)
                }
            }
        }
    )
}

@Composable
private fun CodeBox(char: Char?, active: Boolean, rejected: Boolean, width: Dp) {
    val shape = RoundedCornerShape(14.dp)
    val edge = when {
        rejected -> Offline
        active -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(width = width, height = 62.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .background(if (rejected) Offline.copy(alpha = 0.08f) else Color.Transparent)
            .border(2.dp, edge, shape),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            Text(char.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp))
        }
    }
}

/**
 * The code was accepted. This is the one place motion runs longer than a third of a second: pairing
 * happens once per TV and deserves a moment. The disc grows in, the tick draws itself, and the bar
 * underneath fills for as long as the screen stays, so it is clear the remote is about to open.
 */
@Composable
private fun Paired(tvName: String) {
    val teal = MaterialTheme.colorScheme.primary
    val onTeal = MaterialTheme.colorScheme.onPrimary
    val view = LocalView.current
    val grow = remember { Animatable(0.6f) }
    val tick = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // Typing is over. Left alone the keyboard would go when the code boxes finish fading, and
        // the tick would jump down the screen in the middle of drawing itself
        keyboard?.hide()
        view.hapticConfirm()
        grow.animateTo(1f, tween(200))
        tick.animateTo(1f, tween(250))
    }
    LaunchedEffect(Unit) { bar.animateTo(1f, tween(850, easing = LinearEasing)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = grow.value
                    scaleY = grow.value
                }
                .background(teal.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(116.dp).background(teal.copy(alpha = 0.16f), CircleShape))
            Box(Modifier.size(84.dp).background(teal, CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(44.dp)) {
                    // A tick on a 24-unit grid, drawn only as far along as the animation has got
                    val unit = size.width / 24f
                    val full = Path().apply {
                        moveTo(5f * unit, 12.5f * unit)
                        lineTo(9.5f * unit, 17f * unit)
                        lineTo(19f * unit, 7.5f * unit)
                    }
                    val measure = PathMeasure().apply { setPath(full, false) }
                    val drawn = Path()
                    measure.getSegment(0f, measure.length * tick.value, drawn, true)
                    drawPath(drawn, onTeal, style = Stroke(2.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Paired", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, letterSpacing = 0.sp))
            Text(
                "$tvName is ready",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Box(Modifier.size(width = 120.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(bar.value).background(teal))
        }
    }
}

private val previewMode = RemoteMode.Pairing(
    DiscoveredTv(name = "living-room-tv", friendlyName = "Living Room TV", host = "192.168.1.15", port = 6466)
)

@Composable
private fun PairingPreview(uiState: RemoteUiState) {
    AirMouseTVRemoteTheme { PairingContent(uiState = uiState, onCodeChange = {}, onRetry = {}, onCancel = {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 564, name = "Entering code (keyboard open)")
@Composable
private fun PairingCodePreview() = PairingPreview(RemoteUiState(mode = previewMode, awaitingCode = true, pairingCode = "4F2"))

@Preview(showBackground = true, widthDp = 320, heightDp = 380, name = "Entering code: small phone")
@Composable
private fun PairingCodeSmallPreview() = PairingPreview(RemoteUiState(mode = previewMode, awaitingCode = true, pairingCode = "4F2"))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Connecting")
@Composable
private fun PairingConnectingPreview() = PairingPreview(RemoteUiState(mode = previewMode))

@Preview(showBackground = true, widthDp = 390, heightDp = 564, name = "Wrong code")
@Composable
private fun PairingErrorPreview() = PairingPreview(
    RemoteUiState(
        mode = previewMode, awaitingCode = true, pairingCode = "4F2A91",
        codeRejected = true, error = "That code didn't match. Try again."
    )
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Couldn't connect")
@Composable
private fun PairingFailedPreview() = PairingPreview(
    RemoteUiState(mode = previewMode, error = "Living Room TV didn't respond. Make sure it is on, then try again.")
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Paired")
@Composable
private fun PairingPairedPreview() = PairingPreview(RemoteUiState(mode = previewMode, awaitingCode = true, paired = true))
