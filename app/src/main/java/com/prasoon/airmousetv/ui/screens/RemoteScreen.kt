package com.prasoon.airmousetv.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
import androidx.core.content.edit
import com.prasoon.airmousetv.airmouse.model.AirMouseSetupStep
import com.prasoon.airmousetv.airmouse.model.AirMouseUiState
import com.prasoon.airmousetv.data.api.DiagnosticsHeader
import com.prasoon.airmousetv.data.api.SCROLL_STEP_DP
import com.prasoon.airmousetv.data.api.SUPPORT_EMAIL
import com.prasoon.airmousetv.data.api.TvApp
import com.prasoon.airmousetv.data.api.diagnosticsEmailBody
import com.prasoon.airmousetv.data.api.diagnosticsReport
import com.prasoon.airmousetv.data.api.diagnosticsSubject
import com.prasoon.airmousetv.data.model.AppLaunch
import com.prasoon.airmousetv.data.repository.MAX_DIAGNOSTIC_LINES
import com.prasoon.airmousetv.data.model.DiscoveredTv
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.data.model.RemoteMode
import com.prasoon.airmousetv.data.model.RemoteUiState
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.data.model.VoiceState
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Amber
import com.prasoon.airmousetv.ui.theme.AmberText
import com.prasoon.airmousetv.ui.theme.Charcoal
import com.prasoon.airmousetv.ui.theme.TipSurface
import com.prasoon.airmousetv.ui.theme.VideoRed
import com.prasoon.airmousetv.ui.theme.Faint
import com.prasoon.airmousetv.ui.theme.MenuSurface
import com.prasoon.airmousetv.ui.theme.Offline
import com.prasoon.airmousetv.ui.theme.OfflineText
import com.prasoon.airmousetv.ui.theme.Online
import com.prasoon.airmousetv.ui.theme.PadSurface
import com.prasoon.airmousetv.ui.theme.SheetSurface
import com.prasoon.airmousetv.ui.theme.SurfaceHigh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

/** Set once the "hold a key to repeat it" tip has been closed or acted on, so it never shows again. */
private const val PREF_HOLD_TIP_SEEN = "hold_tip_seen"

/** Set once the tip that points at the air mouse has been closed, or the air mouse has been looked at. */
private const val PREF_AIR_TIP_SEEN = "air_tip_seen"

/** Set once the "⋮" menu has been opened while it carried the dot for the air mouse entry. */
private const val PREF_AIR_MENU_SEEN = "air_menu_seen"

/**
 * How the remote advertises the air mouse right now. [inSwitch]: it has a segment in the mode
 * switch. [proChip]: that segment and the menu entry carry the PRO mark, which they do until the
 * TV is set up. [tip]: the one-time line under the switch. [menuDot]: the dot on "⋮".
 */
internal data class AirOffer(
    val inSwitch: Boolean = false,
    val proChip: Boolean = false,
    val tip: Boolean = false,
    val menuDot: Boolean = false,
)

/**
 * The working remote: D-pad or touchpad in the middle of the thumb's reach, then the key row
 * (Keyboard, Back · Home · Apps, Ask), then media and volume. A short press taps a key; holding it sends a long press.
 * While a dropped session is being re-established the keys are disabled and the header says so.
 */
@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("remote_ui", Context.MODE_PRIVATE) }
    var padMode by rememberSaveable {
        mutableStateOf(PadMode.entries.firstOrNull { it.name == uiPrefs.getString("pad_mode", null) } ?: PadMode.DPAD)
    }
    var showHoldTip by rememberSaveable { mutableStateOf(!uiPrefs.getBoolean(PREF_HOLD_TIP_SEEN, false)) }
    val dismissHoldTip = {
        if (showHoldTip) {
            showHoldTip = false
            uiPrefs.edit { putBoolean(PREF_HOLD_TIP_SEEN, true) }
        }
    }
    var showTextEntry by remember { mutableStateOf(false) }
    var showYouTubeSearch by remember { mutableStateOf(false) }
    var showSpeechSearch by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val tvName = rememberLastNonNull((uiState.mode as? RemoteMode.Connected)?.tv?.displayName).orEmpty().ifBlank { "TV" }
    // The TV asks for its keyboard by bumping showRequests; open text entry on each new request. Not
    // while the phone's speech to text is open: it opened the TV's search itself, and types into it
    val showRequests = uiState.imeField?.showRequests ?: 0
    var seenRequests by remember { mutableIntStateOf(showRequests) }
    LaunchedEffect(showRequests) {
        if (showRequests > seenRequests && !showSpeechSearch) showTextEntry = true
        seenRequests = showRequests
    }

    // YouTube draws its own keyboard, so the TV reports no text box while it is open: the Keyboard key
    // then searches YouTube instead of sitting dimmed
    val inYouTube = uiState.foregroundApp?.startsWith("com.google.android.youtube.tv") == true
    val youTubeKey = inYouTube && uiState.imeField == null
    var youTubeTipSeen by rememberSaveable { mutableStateOf(uiPrefs.getBoolean(PREF_YOUTUBE_TIP_SEEN, false)) }
    val dismissYouTubeTip = {
        if (!youTubeTipSeen) {
            youTubeTipSeen = true
            uiPrefs.edit { putBoolean(PREF_YOUTUBE_TIP_SEEN, true) }
        }
    }
    val showYouTubeTip = youTubeKey && !youTubeTipSeen
    LaunchedEffect(showYouTubeTip) {
        if (showYouTubeTip) {
            delay(YOUTUBE_TIP_SHOWN_MS)
            dismissYouTubeTip()
        }
    }

    // Voice: the TV's assistant where the TV offers it, otherwise the phone's speech to text if the
    // phone has a recognizer, otherwise no Ask key at all
    val hasRecognizer = remember { context.hasSpeechRecognizer() }
    val askMode = when {
        uiState.voiceOffered -> AskMode.VOICE
        hasRecognizer -> AskMode.TYPE
        else -> null
    }
    val mic = rememberMicPermission(tvName)
    val voiceActions = VoiceActions(
        onHoldStart = { if (mic.granted()) viewModel.startVoice(tapMode = false) else mic.ask {} },
        onCancelArmed = viewModel::setVoiceCancelArmed,
        onRelease = { cancelled -> if (cancelled) viewModel.cancelVoice() else viewModel.finishVoice() },
        onShortTap = {
            if (mic.granted()) Toast.makeText(context, "Hold the mic key and speak", Toast.LENGTH_SHORT).show()
            else mic.ask {}
        },
        onTapToggle = {
            when {
                askMode == AskMode.TYPE -> mic.ask { showSpeechSearch = true }
                uiState.voice is VoiceState.Listening -> viewModel.finishVoice()
                else -> mic.ask { viewModel.startVoice(tapMode = true) }
            }
        },
        onStopAndSend = viewModel::finishVoice,
        onCancel = viewModel::cancelVoice,
        onTryAgain = {
            viewModel.dismissVoice()
            if (!uiState.isReconnecting) mic.ask { viewModel.startVoice(tapMode = true) }
        },
        onDismiss = viewModel::dismissVoice
    )
    // Back stops a search started by a tap, and closes the "Sent" card or an error
    val voice = uiState.voice
    BackHandler(enabled = voice is VoiceState.Sent || voice is VoiceState.Failed || (voice as? VoiceState.Listening)?.tapMode == true) {
        if (voice is VoiceState.Listening) viewModel.cancelVoice() else viewModel.dismissVoice()
    }
    val selectPadMode = { mode: PadMode ->
        padMode = mode
        uiPrefs.edit { putString("pad_mode", mode.name) }
    }
    var showDiagnostics by remember { mutableStateOf(false) }
    val openDiagnostics = { showDiagnostics = true }

    val airMouse = uiState.airMouse
    // The air mouse is advertised (its place in the mode switch, the tip, the dot on the menu) unless the
    // phone is known to lack the Bluetooth mouse role. Unknown counts as present, so a fresh install,
    // which can't find out before it has the Bluetooth permission, still gets the offer
    val airOffered = airMouse.available && !airMouse.roleMissing && !airMouse.linked
    var airTipSeen by rememberSaveable { mutableStateOf(uiPrefs.getBoolean(PREF_AIR_TIP_SEEN, false)) }
    var airMenuSeen by rememberSaveable { mutableStateOf(uiPrefs.getBoolean(PREF_AIR_MENU_SEEN, false)) }
    val dismissAirTip = {
        if (!airTipSeen) {
            airTipSeen = true
            uiPrefs.edit { putBoolean(PREF_AIR_TIP_SEEN, true) }
        }
    }
    var showAirSettings by remember { mutableStateOf(false) }
    // A remembered choice of the air mouse falls back to the D-pad on a TV, or a phone, where it has
    // no place in the switch, and comes back by itself on the TV it was made for
    val shownPadMode = if (padMode == PadMode.AIR_MOUSE && !airMouse.linked && !airOffered) PadMode.DPAD else padMode
    // Before setup the air mouse segment shows the offer, which needs no pointer link
    val airPadShowing = shownPadMode == PadMode.AIR_MOUSE && airMouse.linked
    DisposableEffect(airPadShowing) {
        viewModel.setAirMousePadShowing(airPadShowing)
        onDispose { viewModel.setAirMousePadShowing(false) }
    }
    val allowBluetooth = rememberBluetoothPermissionRequest(onGranted = viewModel::retryAirMouse)
    val turnOnBluetooth = rememberBluetoothEnableRequest()
    val currentSettings by rememberUpdatedState(airMouse.settings)
    val airPadActions = remember(viewModel) {
        AirPadActions(
            onPress = viewModel::airPress,
            onSlide = viewModel::airSlide,
            onButton = viewModel::airButton,
            onScroll = viewModel::airScroll,
            onRecenter = viewModel::airRecenter,
            onAirPointing = { viewModel.updateAirMouseSettings(currentSettings.copy(airPointing = it)) },
            onAllowBluetooth = { allowBluetooth() },
            onTurnOnBluetooth = { turnOnBluetooth() },
            onSetUpAgain = viewModel::redoAirMouseSetup,
            onSetUp = viewModel::openAirMouseSetup
        )
    }

    if (airMouse.setup != null) {
        AirMouseSetupScreen(viewModel, airMouse, tvName, onKey = viewModel::sendKey, onDiagnostics = openDiagnostics)
        // A setup that ends linked hands over to the pad it was for
        LaunchedEffect(airMouse.setup) {
            if (airMouse.setup == AirMouseSetupStep.DONE) selectPadMode(PadMode.AIR_MOUSE)
        }
        return
    }

    RemoteContent(
        uiState = uiState,
        tvName = tvName,
        padMode = shownPadMode,
        onPadModeChange = { mode ->
            // Going to look at the air mouse is what the tip was asking for
            if (mode == PadMode.AIR_MOUSE) dismissAirTip()
            selectPadMode(mode)
        },
        airPadActions = airPadActions,
        airOffer = AirOffer(
            inSwitch = airMouse.linked || airOffered,
            proChip = airOffered,
            tip = airOffered && !airTipSeen && shownPadMode != PadMode.AIR_MOUSE,
            menuDot = airOffered && !airMenuSeen
        ),
        onDismissAirTip = dismissAirTip,
        onMenuOpened = {
            if (!airMenuSeen) {
                airMenuSeen = true
                uiPrefs.edit { putBoolean(PREF_AIR_MENU_SEEN, true) }
            }
        },
        onSetUpAirMouse = viewModel::openAirMouseSetup,
        onAirMouseSettings = { showAirSettings = true },
        showHoldTip = showHoldTip,
        onDismissHoldTip = dismissHoldTip,
        onKey = { key, action ->
            // Holding a key is what the tip was there to teach
            if (action == KeyAction.LONG_START) dismissHoldTip()
            // Still sent: the user may mean it, but should know the app can't undo it
            if (key == TvKey.POWER && action == KeyAction.TAP && uiState.powerOffIsFinal) {
                Toast.makeText(context, "Once off, this TV can only be turned on with its own remote", Toast.LENGTH_LONG).show()
            }
            viewModel.sendKey(key, action)
        },
        onShowKeyboard = {
            when {
                uiState.imeField != null -> showTextEntry = true
                youTubeKey -> {
                    dismissYouTubeTip()
                    showYouTubeSearch = true
                }
                else -> Toast.makeText(context, "Open a search box on the TV first", Toast.LENGTH_SHORT).show()
            }
        },
        onDiagnostics = openDiagnostics,
        onSwitchTv = viewModel::switchTv,
        youTubeKey = youTubeKey,
        showYouTubeTip = showYouTubeTip,
        onDismissYouTubeTip = dismissYouTubeTip,
        askMode = askMode,
        voiceActions = voiceActions,
        appsOpen = showApps,
        onApps = { showApps = true },
        onDismissAppLaunch = viewModel::dismissAppLaunch
    )

    if (showApps) {
        val appsActions = remember(viewModel) {
            AppsActions(onOpen = viewModel::openApp, onRemove = viewModel::removeRecentApp, onRename = viewModel::renameApp)
        }
        AppsSheet(
            recent = uiState.recentApps, popular = uiState.popularApps, onTv = uiState.foregroundApp,
            tvName = tvName, actions = appsActions, onDismiss = { showApps = false }
        )
    }
    if (showAirSettings && airMouse.linked) {
        val settingsActions = remember(viewModel) {
            AirSettingsActions(
                onChange = viewModel::updateAirMouseSettings,
                onCalibrate = viewModel::calibrateAirMouse,
                onUnlink = {
                    showAirSettings = false
                    viewModel.unlinkAirMouse()
                }
            )
        }
        AirMouseSettingsSheet(airMouse, tvName, settingsActions, onDismiss = {
            showAirSettings = false
            viewModel.clearCalibrationResult()
        })
    }
    if (showTextEntry) {
        TextEntrySheet(viewModel = viewModel, tvName = tvName, onDismiss = { showTextEntry = false })
    }
    if (showYouTubeSearch) {
        YouTubeSearchSheet(tvName = tvName, onSearch = viewModel::searchYouTube, onDismiss = { showYouTubeSearch = false })
    }
    if (showSpeechSearch) {
        SpeechSearchSheet(viewModel, tvName, youTube = youTubeKey, onDismiss = { showSpeechSearch = false })
    }
    // Last, so it opens over the air mouse setup too, which has its own way here
    if (showDiagnostics) {
        val header = remember { diagnosticsHeader(context, viewModel.diagnosticsTv()) }
        val report = remember { diagnosticsReport(header, viewModel.diagnosticsReport()) }
        DiagnosticsSheet(
            report = report,
            maxEvents = MAX_DIAGNOSTIC_LINES,
            tvName = tvName,
            onSend = {
                showDiagnostics = false
                sendDiagnostics(context, header.appVersion, report)
            },
            onCopy = {
                showDiagnostics = false
                clipboard.setText(AnnotatedString(report))
                Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDiagnostics = false }
        )
    }
}

/** This build's version and the phone it runs on, for the top of a diagnostics report. */
private fun diagnosticsHeader(context: Context, tv: String?): DiagnosticsHeader {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    @Suppress("DEPRECATION")
    val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    return DiagnosticsHeader(
        appVersion = info.versionName.orEmpty(),
        appBuild = build,
        phone = "${Build.MANUFACTURER} ${Build.MODEL}",
        android = Build.VERSION.RELEASE,
        tv = tv
    )
}

/**
 * Opens [report] as a prefilled email to support. Plain ACTION_SEND text lists every app that takes text, ranked by the system, and on a real phone
 * the email apps were missing from the share sheet; a mailto: selector limits it to email apps,
 * while keeping ACTION_SEND's extras, which email apps read more reliably than a mailto: link's
 * query. With one email app it opens straight to the draft, with several Android asks which. A
 * phone without one gets the share sheet instead.
 */
private fun sendDiagnostics(context: Context, appVersion: String, report: String) {
    val lines = report.count { it == '\n' } + 1
    val title = "Air Mouse TV Remote diagnostics · $lines lines"
    fun send() = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, diagnosticsSubject(appVersion))
        putExtra(Intent.EXTRA_TEXT, diagnosticsEmailBody(report))
        // The share sheet's preview line on Android 10 and later
        putExtra(Intent.EXTRA_TITLE, title)
    }
    val email = send().apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) }
    try {
        context.startActivity(email)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent.createChooser(send(), title))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can send it. Use Copy instead.", Toast.LENGTH_LONG).show()
        }
    }
}

/** What the Ask key and the voice panel do; see [AskKey] and [VoiceOverlay]. */
internal class VoiceActions(
    val onHoldStart: () -> Unit = {},
    val onCancelArmed: (Boolean) -> Unit = {},
    val onRelease: (cancelled: Boolean) -> Unit = {},
    val onShortTap: () -> Unit = {},
    val onTapToggle: () -> Unit = {},
    val onStopAndSend: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)

/** Set once the "Search YouTube from your phone" bubble has gone: after a tap on the key, or [YOUTUBE_TIP_SHOWN_MS]. */
private const val PREF_YOUTUBE_TIP_SEEN = "youtube_tip_seen"

/** How long the YouTube tip stays if the key isn't tapped: long enough to read, short enough not to nag. */
private const val YOUTUBE_TIP_SHOWN_MS = 5000L

/** Small capitals under a key or a group of keys. */
private val captionStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.Medium
    )

@Composable
private fun Header(
    tvName: String,
    reconnecting: Boolean,
    enabled: Boolean,
    onKey: (TvKey, KeyAction) -> Unit,
    menu: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val teal = MaterialTheme.colorScheme.primary
                // The halo is part of "connected"; a lost session gets a bare red dot
                Box(
                    Modifier.size(14.dp).background(if (reconnecting) Color.Transparent else teal.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(8.dp).background(if (reconnecting) Offline else teal, CircleShape))
                }
                Text(
                    text = if (reconnecting) "DISCONNECTED · RECONNECTING…" else "CONNECTED",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (reconnecting) OfflineText else teal
                )
            }
            Text(
                tvName,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RemoteKey(
            key = TvKey.POWER,
            onKey = onKey,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
            container = Color.Transparent,
            border = BorderStroke(2.dp, Amber)
        ) { KeyIcon(Icons.Default.PowerSettingsNew, "Power", tint = Amber) }
        menu()
    }
}

/**
 * The "⋮" menu in the header: things that are about the app and the TV connection, not the remote keys.
 * The air mouse has one entry in it: the setup while the TV isn't set up ([onSetUpAirMouse], null on a
 * phone that can't be a Bluetooth mouse), its settings once it is ([onAirMouseSettings]).
 * With [dot] the button carries a mark that there is something new inside, until [onOpened] has run.
 */
@Composable
private fun OverflowMenu(
    onDiagnostics: () -> Unit,
    onSwitchTv: () -> Unit,
    onSetUpAirMouse: (() -> Unit)?,
    onAirMouseSettings: (() -> Unit)?,
    proChip: Boolean,
    dot: Boolean,
    onOpened: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = {
                expanded = true
                onOpened()
            },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                contentColor = if (expanded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = if (dot) "More, 1 new item" else "More")
        }
        if (dot) {
            val teal = MaterialTheme.colorScheme.primary
            // Ringed in the background colour, so it reads as a dot on the button and not part of the icon
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(10.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape).padding(1.dp).background(teal, CircleShape)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MenuSurface,
            border = BorderStroke(1.dp, SurfaceHigh)
        ) {
            if (onSetUpAirMouse != null) {
                MenuItem("Set up air mouse", Icons.Default.Mouse, tint = MaterialTheme.colorScheme.primary, proChip = proChip) {
                    expanded = false
                    onSetUpAirMouse()
                }
                HorizontalDivider(color = SurfaceHigh)
            } else if (onAirMouseSettings != null) {
                MenuItem("Air mouse settings", Icons.Default.Mouse) {
                    expanded = false
                    onAirMouseSettings()
                }
                HorizontalDivider(color = SurfaceHigh)
            }
            MenuItem("Switch TV", Icons.Default.SwapHoriz) {
                expanded = false
                onSwitchTv()
            }
            HorizontalDivider(color = SurfaceHigh)
            MenuItem("Send diagnostics", Icons.AutoMirrored.Filled.Send, tint = MaterialTheme.colorScheme.primary) {
                expanded = false
                onDiagnostics()
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    proChip: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
                if (proChip) ProChip()
            }
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        },
        onClick = onClick
    )
}

/** Shown until closed, or until a key is first held: nothing else on the remote says that holding repeats. */
@Composable
private fun HoldTip(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Hold a key to repeat it. Hold OK or Power for menus.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.Close, contentDescription = "Dismiss tip",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Shown once under the mode switch on a phone that can be an air mouse: without it, the third
 * segment is just a word with a chip on it.
 */
@Composable
private fun AirMouseTip(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(SheetSurface)
            .border(1.dp, SurfaceHigh, CircleShape)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Air mouse: point your phone at the TV", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Close, contentDescription = "Dismiss tip",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * A row of keys in one rounded pill. Since 3.0 no caption: the icons are the common ones, and screen
 * readers still hear each key's name.
 */
@Composable
private fun KeyPill(modifier: Modifier = Modifier, keys: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = keys
    )
}

/** Below this screen width the key row's keys shrink (round keys 50 dp, pill keys 52 dp wide), so the row fits a 320 dp phone. */
private val COMPACT_KEYS_BELOW = 360.dp

/**
 * The row under the pad: Keyboard, then Back, Home and Apps in one pill with Home lit (the key the
 * thumb goes back to), then Ask. Nothing in it appears or disappears with the TV's state, so it never reflows:
 * the Keyboard key changes its face for YouTube, and the Ask key dims on a TV without voice. On a
 * phone with nothing to offer for voice, an empty space keeps the pill in the middle.
 */
@Composable
private fun KeyRow(
    uiState: RemoteUiState,
    enabled: Boolean,
    compact: Boolean,
    onKey: (TvKey, KeyAction) -> Unit,
    onShowKeyboard: () -> Unit,
    youTubeKey: Boolean,
    showYouTubeTip: Boolean,
    onDismissYouTubeTip: () -> Unit,
    askMode: AskMode?,
    voiceActions: VoiceActions,
    appsOpen: Boolean,
    onApps: () -> Unit
) {
    val round = if (compact) 50.dp else 58.dp
    val pillKey = Modifier.size(if (compact) 52.dp else 64.dp, 50.dp)
    val pillShape = RoundedCornerShape(25.dp)
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyboardKey(
                size = round, enabled = enabled, youTube = youTubeKey,
                // Dimmed, but still tappable, while the TV has no text box: a tap then says what to do
                dimmed = uiState.imeField == null && !youTubeKey,
                onClick = onShowKeyboard
            )
            KeyPill {
                RemoteKey(TvKey.BACK, onKey, enabled, pillKey, pillShape, Color.Transparent) {
                    KeyIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Box(Modifier.width(4.dp))
                RemoteKey(TvKey.HOME, onKey, enabled, pillKey, pillShape, SurfaceHigh) {
                    KeyIcon(Icons.Default.Home, "Home")
                }
                Box(Modifier.width(4.dp))
                AppsKey(pillKey, pillShape, enabled, lit = appsOpen, onClick = onApps)
            }
            if (askMode != null) {
                AskKey(
                    mode = askMode,
                    voice = uiState.voice,
                    enabled = enabled,
                    size = round,
                    onHoldStart = voiceActions.onHoldStart,
                    onCancelArmed = voiceActions.onCancelArmed,
                    onRelease = voiceActions.onRelease,
                    onShortTap = voiceActions.onShortTap,
                    onTapToggle = voiceActions.onTapToggle
                )
            } else {
                Box(Modifier.size(round))
            }
        }
        if (showYouTubeTip) {
            TipBubble(
                "Search YouTube from your phone",
                onDismiss = onDismissYouTubeTip,
                // Sits above the row without taking room in it, so nothing moves when it goes
                modifier = Modifier.align(Alignment.TopStart).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0))
                    layout(placeable.width, 0) { placeable.place(0, -placeable.height - 14.dp.roundToPx()) }
                }
            )
        }
    }
}

/**
 * The Apps key at the end of the pill: opens the Apps sheet, and is lit (teal on the raised surface)
 * while the sheet is open. Not a TV key, so a tap only, no hold.
 */
@Composable
private fun AppsKey(modifier: Modifier, shape: Shape, enabled: Boolean, lit: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    KeySurface(
        enabled = enabled,
        pressed = pressed,
        held = false,
        shape = shape,
        container = if (lit) SurfaceHigh else Color.Transparent,
        border = null,
        modifier = modifier.clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
    ) {
        Icon(
            AppsDotsIcon, contentDescription = "Apps on the TV",
            tint = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The round Keyboard key. While YouTube is on screen with no text box it is lit and shows a search
 * glass with a red play mark, and opens YouTube search; otherwise it opens the typing sheet.
 */
@Composable
private fun KeyboardKey(size: Dp, enabled: Boolean, youTube: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    KeySurface(
        enabled = enabled && !dimmed,
        pressed = pressed,
        held = false,
        shape = CircleShape,
        container = MaterialTheme.colorScheme.surfaceVariant,
        border = null,
        // The key draws its own pressed look, so the ripple would only double it
        modifier = Modifier.size(size).clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
    ) {
        if (youTube) {
            Image(rememberVectorPainter(rememberVideoSearchIcon()), contentDescription = "Search YouTube", modifier = Modifier.size(26.dp))
        } else {
            KeyIcon(Icons.Default.Keyboard, "Keyboard")
        }
    }
}

/**
 * A search glass with a red play mark in its lens: a general video-search sign rather than YouTube's
 * logo. Built in two colours, which a tinted [Icon] would flatten into one.
 */
@Composable
private fun rememberVideoSearchIcon(): ImageVector {
    val ink = MaterialTheme.colorScheme.onSurface
    return remember(ink) {
        ImageVector.Builder("VideoSearch", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(ink), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(17.5f, 10.5f)
                arcTo(7f, 7f, 0f, true, true, 3.5f, 10.5f)
                arcTo(7f, 7f, 0f, true, true, 17.5f, 10.5f)
                close()
                moveTo(21f, 21f); lineTo(15.5f, 15.5f)
            }
            path(fill = SolidColor(VideoRed), stroke = SolidColor(VideoRed), strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 7.8f); lineTo(9f, 13.2f); lineTo(13.4f, 10.5f); close()
            }
        }.build()
    }
}

/** A light one-time bubble pointing down at the key under its left end; a tap closes it. */
@Composable
private fun TipBubble(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clickable(onClick = onDismiss)
            .drawBehind {
                // The pointer under the bubble, centred over the first key's middle
                val half = 6.dp.toPx()
                val x = 29.dp.toPx()
                drawPath(
                    Path().apply {
                        moveTo(x - half, size.height - 1f)
                        lineTo(x, size.height + half)
                        lineTo(x + half, size.height - 1f)
                        close()
                    },
                    TipSurface
                )
            }
            .background(TipSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = Charcoal)
    }
}

/** Previous / play-pause / next. The keys share the pill's width, so they still fit on a narrow phone. */
@Composable
private fun MediaPill(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    KeyPill(modifier) {
        val side = Modifier.weight(1f).height(48.dp)
        RemoteKey(TvKey.PREVIOUS, onKey, enabled, side, container = Color.Transparent) {
            KeyIcon(Icons.Default.SkipPrevious, "Previous")
        }
        RemoteKey(TvKey.PLAY_PAUSE, onKey, enabled, Modifier.weight(1.3f).height(48.dp), container = SurfaceHigh) {
            KeyIcon(rememberPlayPauseIcon(), "Play/Pause")
        }
        RemoteKey(TvKey.NEXT, onKey, enabled, side, container = Color.Transparent) {
            KeyIcon(Icons.Default.SkipNext, "Next")
        }
    }
}

/** Minus, mute and plus keys. The TV protocol has volume steps rather than an absolute level, so there is no slider. */
@Composable
private fun VolumePill(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    KeyPill(modifier) {
        val side = Modifier.weight(1f).height(48.dp)
        RemoteKey(TvKey.VOLUME_DOWN, onKey, enabled, side, container = Color.Transparent) {
            KeyIcon(Icons.Default.Remove, "Volume down")
        }
        RemoteKey(TvKey.MUTE, onKey, enabled, Modifier.size(48.dp), container = SurfaceHigh) {
            KeyIcon(Icons.Default.VolumeOff, "Mute", tint = Amber)
        }
        RemoteKey(TvKey.VOLUME_UP, onKey, enabled, side, container = Color.Transparent) {
            KeyIcon(Icons.Default.Add, "Volume up")
        }
    }
}

/** Below this much usable height (before text scaling) the remote scrolls rather than squeeze the pad. */
private val MIN_FIXED_LAYOUT_HEIGHT = 640.dp

/** The pad area's height when the remote scrolls. */
private val MIN_PAD_HEIGHT = 240.dp

/** The D-pad's full size; it is drawn smaller, with everything in proportion, when the screen is short. */
private val DPAD_SIZE = 290.dp

/**
 * Circular directional pad, [size] across. OK in the middle is the one filled key on the remote:
 * it is what the thumb comes back to. The arrows have no key shape of their own; the disc is the key.
 */
@Composable
internal fun DPad(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean, size: Dp = DPAD_SIZE) {
    val scale = size / DPAD_SIZE
    val edge = 8.dp * scale
    val arrowShape = RoundedCornerShape(28.dp * scale)
    val long = 96.dp * scale
    val short = 68.dp * scale
    val arrow = 32.dp * scale
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(PadSurface)
            .border(1.dp, SurfaceHigh, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        RemoteKey(TvKey.UP, onKey, enabled, Modifier.align(Alignment.TopCenter).padding(top = edge).size(long, short), arrowShape, Color.Transparent) {
            KeyIcon(Icons.Default.KeyboardArrowUp, "Up", size = arrow)
        }
        RemoteKey(TvKey.DOWN, onKey, enabled, Modifier.align(Alignment.BottomCenter).padding(bottom = edge).size(long, short), arrowShape, Color.Transparent) {
            KeyIcon(Icons.Default.KeyboardArrowDown, "Down", size = arrow)
        }
        RemoteKey(TvKey.LEFT, onKey, enabled, Modifier.align(Alignment.CenterStart).padding(start = edge).size(short, long), arrowShape, Color.Transparent) {
            KeyIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", size = arrow)
        }
        RemoteKey(TvKey.RIGHT, onKey, enabled, Modifier.align(Alignment.CenterEnd).padding(end = edge).size(short, long), arrowShape, Color.Transparent) {
            KeyIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", size = arrow)
        }
        val teal = MaterialTheme.colorScheme.primary
        Box(
            Modifier.size(118.dp * scale).background(teal.copy(alpha = if (enabled) 0.14f else 0f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            RemoteKey(TvKey.OK, onKey, enabled, Modifier.size(104.dp * scale), container = teal) {
                Text(
                    "OK",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp * scale, letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * A round key with a small caption underneath. Pass [key] to send a TV key (with tap/hold handling),
 * or [onClick] to run a local action.
 */
@Composable
internal fun LabeledKey(
    label: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    key: TvKey? = null,
    onKey: ((TvKey, KeyAction) -> Unit)? = null,
    dimmed: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val size = Modifier.size(58.dp)
        if (key != null && onKey != null) {
            RemoteKey(key, onKey, enabled, size, content = content)
        } else {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            KeySurface(
                enabled = enabled && !dimmed,
                pressed = pressed,
                held = false,
                shape = CircleShape,
                container = MaterialTheme.colorScheme.surfaceVariant,
                border = null,
                // The key draws its own pressed look, so the ripple would only double it
                modifier = size.clickable(interaction, indication = null, enabled = enabled && onClick != null) { onClick?.invoke() },
                content = content
            )
        }
        Text(label, style = captionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** A play triangle followed by pause bars: the TV doesn't report playback state, so the icon claims neither. */
@Composable
private fun rememberPlayPauseIcon(): ImageVector = remember {
    ImageVector.Builder("PlayPause", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2f, 6f); lineTo(2f, 18f); lineTo(11f, 12f); close()
            moveTo(14f, 6f); lineTo(17f, 6f); lineTo(17f, 18f); lineTo(14f, 18f); close()
            moveTo(19f, 6f); lineTo(22f, 6f); lineTo(22f, 18f); lineTo(19f, 18f); close()
        }
    }.build()
}

@Composable
internal fun KeyIcon(
    icon: ImageVector,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 24.dp
) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun RemoteContent(
    uiState: RemoteUiState,
    tvName: String,
    padMode: PadMode,
    onPadModeChange: (PadMode) -> Unit,
    airPadActions: AirPadActions,
    airOffer: AirOffer,
    onDismissAirTip: () -> Unit,
    onMenuOpened: () -> Unit,
    onSetUpAirMouse: () -> Unit,
    onAirMouseSettings: () -> Unit,
    showHoldTip: Boolean,
    onDismissHoldTip: () -> Unit,
    onKey: (TvKey, KeyAction) -> Unit,
    onShowKeyboard: () -> Unit,
    onDiagnostics: () -> Unit,
    onSwitchTv: () -> Unit,
    youTubeKey: Boolean = false,
    showYouTubeTip: Boolean = false,
    onDismissYouTubeTip: () -> Unit = {},
    askMode: AskMode? = AskMode.VOICE,
    voiceActions: VoiceActions = VoiceActions(),
    appsOpen: Boolean = false,
    onApps: () -> Unit = {},
    onDismissAppLaunch: () -> Unit = {}
) {
    val reconnecting = uiState.isReconnecting
    val enabled = !reconnecting
    val airMouse = uiState.airMouse

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Larger text makes the fixed rows taller, so the screen counts as short sooner
        val scrolls = maxHeight < MIN_FIXED_LAYOUT_HEIGHT * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val compactKeys = maxWidth < COMPACT_KEYS_BELOW
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(
                tvName, reconnecting = reconnecting, enabled = enabled, onKey = onKey,
                menu = {
                    OverflowMenu(
                        onDiagnostics, onSwitchTv,
                        onSetUpAirMouse = onSetUpAirMouse.takeIf { airMouse.available && !airMouse.linked },
                        onAirMouseSettings = onAirMouseSettings.takeIf { airMouse.linked },
                        proChip = airOffer.proChip, dot = airOffer.menuDot, onOpened = onMenuOpened
                    )
                }
            )

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // The tip brings its own gap: once it has gone, an empty slot here would still be spaced
            // from its neighbours and leave a double gap under the header
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = showHoldTip && enabled) {
                    Box(Modifier.padding(bottom = 14.dp)) { HoldTip(onDismissHoldTip) }
                }
                PadModeToggle(
                    padMode, PadMode.entries.filter { it != PadMode.AIR_MOUSE || airOffer.inSwitch },
                    proOn = PadMode.AIR_MOUSE.takeIf { airOffer.proChip }, onSelect = onPadModeChange
                )
                // Below the switch it points at; gone for good once closed or once the air mouse has been looked at
                AnimatedVisibility(visible = airOffer.tip && enabled) {
                    Box(Modifier.padding(top = 10.dp)) { AirMouseTip(onDismissAirTip) }
                }
            }

            // The pad takes whatever height the fixed rows leave. On a screen too short for that it gets a
            // fixed height instead and the page scrolls, so the pad can never shrink to nothing
            BoxWithConstraints(
                modifier = (if (scrolls) Modifier.height(MIN_PAD_HEIGHT) else Modifier.weight(1f)).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val dpadSize = minOf(DPAD_SIZE, maxWidth, maxHeight)
                // Only the pad changes with the mode; everything around it stays exactly where it is
                Crossfade(targetState = padMode, animationSpec = tween(200), label = "pad") { mode ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (mode) {
                            PadMode.DPAD -> DPad(onKey, enabled, size = dpadSize)
                            PadMode.TOUCHPAD -> Touchpad(onKey, enabled, Modifier.fillMaxSize())
                            // Not dimmed with the keys while the Wi-Fi session reconnects: the pointer
                            // travels over Bluetooth and keeps working. It is told, so it doesn't promise working keys
                            PadMode.AIR_MOUSE -> AirMousePad(airMouse, tvName, airPadActions, Modifier.fillMaxSize(), wifiUp = !reconnecting)
                        }
                    }
                }
                // Over the pad, not over the keys: the thumb stays on the Ask key while the panel shows
                VoiceOverlay(
                    uiState.voice, tvName,
                    onStopAndSend = voiceActions.onStopAndSend,
                    onCancel = voiceActions.onCancel,
                    onTryAgain = voiceActions.onTryAgain,
                    onDismiss = voiceActions.onDismiss,
                    modifier = Modifier.fillMaxSize()
                )
                AppLaunchCard(uiState.appLaunch, tvName, onDismissAppLaunch, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            }

            KeyRow(
                uiState = uiState,
                enabled = enabled,
                compact = compactKeys,
                onKey = onKey,
                onShowKeyboard = onShowKeyboard,
                youTubeKey = youTubeKey,
                showYouTubeTip = showYouTubeTip && enabled,
                onDismissYouTubeTip = onDismissYouTubeTip,
                askMode = askMode,
                voiceActions = voiceActions,
                appsOpen = appsOpen,
                onApps = onApps
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MediaPill(onKey, enabled, Modifier.weight(1f))
                VolumePill(onKey, enabled, Modifier.weight(1f))
            }

            Text(
                if (reconnecting) "The keys come back when the TV answers."
                else "Some apps, like Netflix, ignore media keys. Press OK to pause.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (reconnecting) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp),
                color = Offline,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/** How long the TV gets to show the typed text before the sheet says it hasn't arrived: a few of the ~350 ms edit slots. */
private const val NOT_ON_TV_AFTER_MS = 2000L

/** How long the automatic OK press gets to open the TV's keyboard before the sheet offers other ways. */
private const val OK_PRESS_SETTLE_MS = 1500L

/**
 * Types into whatever text field is focused on the TV. Each change is sent as the difference
 * from the previous text, so edits and deletions work: through the TV's text input session, or as
 * key presses if none is open. The send button, like the keyboard's send action, sends Enter to the TV.
 */
@Composable
private fun TextEntrySheet(viewModel: RemoteViewModel, tvName: String, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val field = uiState.imeField
    // The TV puts its cursor at the end of the text it already holds, and edits are made relative to
    // that, so the sheet starts with its own cursor at the end too (a plain string would start it at the front)
    var value by remember {
        val initial = field?.value.orEmpty()
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    val context = LocalContext.current

    // The TV's text box went away (another app, the TV's idle timeout, a lost connection): typing here would go nowhere
    val fieldClosed = field == null
    LaunchedEffect(fieldClosed) {
        if (fieldClosed) {
            Toast.makeText(context, "The TV's text box closed", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    // When the TV keeps text other than what was typed, show that rather than text that isn't there
    LaunchedEffect(Unit) {
        viewModel.tvTextAccepted.collect { value = TextFieldValue(it, selection = TextRange(it.length)) }
    }

    LaunchedEffect(Unit) {
        viewModel.beginTextEntry(value.text)
        // A focused text box that hasn't opened the TV's keyboard yet (e.g. SmartTube's search the first
        // time) takes no text edits. Pressing OK on it opens the keyboard, and the text session follows.
        if (field != null && !field.acceptsText) viewModel.sendKey(TvKey.OK)
    }

    // "Not on TV yet" only after the text has had time to get there, or the pill would flicker on every letter
    val onTv = field?.value == value.text
    var overdue by remember { mutableStateOf(false) }
    LaunchedEffect(onTv, value.text) {
        overdue = false
        if (!onTv) {
            delay(NOT_ON_TV_AFTER_MS)
            overdue = true
        }
    }
    var okPressSettled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(OK_PRESS_SETTLE_MS)
        okPressSettled = true
    }

    TvInputSheet(
        title = field?.label?.takeIf { it.isNotBlank() } ?: "Type",
        tvName = tvName,
        value = value,
        onValueChange = { new ->
            val textChanged = new.text != value.text
            value = new
            // Moving the cursor changes nothing on the TV. With no text box on the TV, letters sent
            // as key presses go nowhere (or trigger shortcuts)
            if (textChanged && field != null) viewModel.typeText(new.text)
        },
        status = when {
            value.text.isEmpty() -> null
            onTv -> TvTextStatus.ON_TV
            overdue -> TvTextStatus.NOT_ON_TV
            else -> null
        },
        sendIcon = Icons.AutoMirrored.Filled.ArrowForward,
        sendDescription = "Send to TV",
        imeAction = ImeAction.Send,
        onSend = { viewModel.submitText() },
        onDismiss = onDismiss
    ) { close ->
        if (field?.acceptsText == false && okPressSettled) {
            NotTakingText(onPressOk = { viewModel.sendKey(TvKey.OK) }, onUseTvKeyboard = close)
        }
    }
}

/**
 * Shown when the TV's text box has no text session even after OK was pressed on it: one more OK may
 * open its keyboard, and if this TV or app ignores typing altogether, its own keyboard still works.
 */
@Composable
private fun NotTakingText(onPressOk: () -> Unit, onUseTvKeyboard: () -> Unit) {
    NoticeCard {
        Text("The TV's text box isn't taking text yet.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPressOk,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.5.dp, Amber)
            ) {
                Text("Press OK on TV", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            TextButton(onClick = onUseTvKeyboard, modifier = Modifier.weight(1f).height(44.dp), shape = MaterialTheme.shapes.small) {
                Text("Use TV keyboard", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * Asks for a search and opens YouTube's results for it on the TV.
 *
 * YouTube draws its own keyboard, so the TV reports no text box. Its search box would take letter
 * key presses, but the TV's remote service drops letters unless a real text box has focus
 * (checked on a Google TV: the D-pad keys reached the input system, the letters never did), so
 * the results are opened by link instead.
 */
@Composable
private fun YouTubeSearchSheet(tvName: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    TvInputSheet(
        title = "Search YouTube",
        tvName = tvName,
        value = query,
        onValueChange = { query = it },
        status = null,
        sendIcon = Icons.Default.Search,
        sendDescription = "Search on TV",
        imeAction = ImeAction.Search,
        titleDot = VideoRed,
        sendEnabled = query.text.isNotBlank(),
        onSend = { onSearch(query.text) },
        onDismiss = onDismiss
    ) {
        // YouTube's own keyboard can't be typed into from a phone, which is why the results open directly
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text("Results open on the TV.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Whether the TV's text box holds what the sheet shows. */
internal enum class TvTextStatus { ON_TV, NOT_ON_TV }

/**
 * The bottom sheet for anything typed on the phone and sent to the TV: a text field, a round send
 * button, and room underneath for a note. It closes itself after [onSend]. [extra] is handed a
 * function that closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvInputSheet(
    title: String,
    tvName: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    status: TvTextStatus?,
    sendIcon: ImageVector,
    sendDescription: String,
    imeAction: ImeAction,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    sendEnabled: Boolean = true,
    titleDot: Color? = null,
    extra: @Composable ColumnScope.(close: () -> Unit) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slide away first, then leave the composition, so closing looks the same as swiping the sheet down
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(36.dp, 4.dp).background(Faint, CircleShape)) }
    ) {
        TvInputSheetContent(
            title, tvName, value, onValueChange, status, sendIcon, sendDescription, imeAction,
            onSend = {
                if (sendEnabled) {
                    onSend()
                    close()
                }
            },
            sendEnabled = sendEnabled,
            focusRequester = rememberFocusOnOpen(),
            titleDot = titleDot,
            extra = { extra(close) }
        )
    }
}

@Composable
private fun TvInputSheetContent(
    title: String,
    tvName: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    status: TvTextStatus?,
    sendIcon: ImageVector,
    sendDescription: String,
    imeAction: ImeAction,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    focusRequester: FocusRequester,
    titleDot: Color? = null,
    extra: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val captionText = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            val captionColor = MaterialTheme.colorScheme.onSurfaceVariant
            // The label comes from the TV and can be long ("Search movies, shows, apps and more"), so it is
            // the part that gives way: the TV's name says where the text is going. The name is measured
            // first and the label gets what is left
            // The same red as the play mark on the key that opened the sheet, so the two read as one
            if (titleDot != null) Box(Modifier.size(8.dp).background(titleDot, CircleShape))
            Row(Modifier.weight(1f)) {
                Text(
                    title, style = captionText, color = captionColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                Text(" · $tvName", style = captionText, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (status != null) StatusPill(status)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
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
                    if (value.text.isNotEmpty()) {
                        IconButton(onClick = { onValueChange(TextFieldValue("")) }) {
                            Icon(
                                Icons.Default.Close, contentDescription = "Clear text",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onSend = { onSend() }, onSearch = { onSend() })
            )
            FilledIconButton(onClick = onSend, enabled = sendEnabled, modifier = Modifier.size(52.dp)) {
                Icon(sendIcon, contentDescription = sendDescription, modifier = Modifier.size(22.dp))
            }
        }
        extra()
    }
}

@Composable
internal fun StatusPill(status: TvTextStatus) {
    val onTv = status == TvTextStatus.ON_TV
    val color = if (onTv) Online else AmberText
    Row(
        modifier = Modifier
            .background((if (onTv) Online else Amber).copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (onTv) Icons.Default.Check else Icons.Default.ErrorOutline,
            contentDescription = null, tint = color, modifier = Modifier.size(14.dp)
        )
        Text(
            if (onTv) "On TV" else "Not on TV yet",
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
            color = color
        )
    }
}

/** A [FocusRequester] that takes focus once its field is laid out, so the keyboard opens with the sheet. */
@Composable
private fun rememberFocusOnOpen(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    // Wait for the field to be laid out before asking for focus, or there is nothing to focus yet
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    return focusRequester
}

/** How the directional keys are presented. */
private enum class PadMode(val label: String) { DPAD("D-pad"), TOUCHPAD("Touchpad"), AIR_MOUSE("Air mouse") }

/** The toggle's width for each pad mode it offers: wide enough for "Touchpad" at the largest text the layout allows for. */
private val PAD_MODE_WIDTH = 110.dp

/**
 * Switch between the pads in [modes]. The selected one is raised rather than teal: teal is kept for
 * OK, the thing to press, and this is only a setting.
 */
@Composable
private fun PadModeToggle(selected: PadMode, modes: List<PadMode>, proOn: PadMode? = null, onSelect: (PadMode) -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = PAD_MODE_WIDTH * modes.size + 20.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        modes.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) SurfaceHigh else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab) { onSelect(mode) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        mode.label,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        // On a narrow phone the PRO chip leaves too little room; the word gives way, not the chip
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (mode == proOn) ProChip()
                }
            }
        }
    }
}

/** How long an edge arrow stays lit after the last step its way: long enough to see, gone soon after the finger stops. */
private const val STEP_FLASH_MS = 160L

/** Spacing of the touchpad's dot grid, which is there so the surface reads as something to swipe on. */
private val TOUCHPAD_GRID = 22.dp

/**
 * A surface that turns swipes into D-pad presses (one per [SCROLL_STEP_DP] dragged, so a long swipe
 * keeps scrolling) and a tap into OK. A soft dot follows the finger, and the arrow at the matching
 * edge lights up for each step sent, so the user can see what the TV is being told without looking
 * for it on the TV. Nothing moves unless a finger is down.
 */
@Composable
private fun Touchpad(onKey: (TvKey, KeyAction) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val stepPx = with(LocalDensity.current) { SCROLL_STEP_DP.dp.toPx() }
    val currentOnKey by rememberUpdatedState(onKey)
    val view = LocalView.current
    val teal = MaterialTheme.colorScheme.primary
    var finger by remember { mutableStateOf<Offset?>(null) }
    var lit by remember { mutableStateOf<TvKey?>(null) }
    // Counts steps, so each one restarts the timer: an arrow stays lit through a run of steps its way
    var steps by remember { mutableIntStateOf(0) }
    LaunchedEffect(steps) {
        if (lit != null) {
            delay(STEP_FLASH_MS)
            lit = null
        }
    }
    val step = { key: TvKey ->
        currentOnKey(key, KeyAction.TAP)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        lit = key
        steps++
    }
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(PadSurface)
            .border(1.dp, SurfaceHigh, shape)
            .drawWithCache {
                // The dots are worked out once per size and drawn in one call: only the finger's halo
                // changes while it moves, and this is redrawn on every frame of a swipe
                val grid = TOUCHPAD_GRID.toPx()
                // Named apart because inside buildList "size" is the list's own
                val area = size
                val dots = buildList {
                    var y = grid / 2
                    while (y < area.height) {
                        var x = grid / 2
                        while (x < area.width) {
                            add(Offset(x, y))
                            x += grid
                        }
                        y += grid
                    }
                }
                val dotColor = SurfaceHigh.copy(alpha = 0.6f)
                val dotSize = 2.4.dp.toPx()
                onDrawBehind {
                    drawPoints(dots, PointMode.Points, dotColor, dotSize, StrokeCap.Round)
                    finger?.let { drawCircle(teal.copy(alpha = 0.18f), 30.dp.toPx(), it) }
                }
            }
            .semantics {
                contentDescription = "Touchpad. Double-tap to select, or switch to the D-pad."
                if (enabled) onClick { currentOnKey(TvKey.OK, KeyAction.TAP); true } else disabled()
            }
            // Only watches where the finger is, for the dot; the gestures below decide what it means
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    finger = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.firstOrNull()?.let { finger = it.position }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        finger = null
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // Same rule as RemoteKey: a short touch is OK, held past HOLD_THRESHOLD_MS it is a long OK
                // (the TV's context menu). A swipe cancels the press, so moving never sends either.
                detectTapGestures(onPress = {
                    var held = false
                    try {
                        val released = withTimeoutOrNull(HOLD_THRESHOLD_MS) { tryAwaitRelease() }
                        when (released) {
                            true -> {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                currentOnKey(TvKey.OK, KeyAction.TAP)
                            }
                            null -> {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                currentOnKey(TvKey.OK, KeyAction.LONG_START)
                                held = true
                                tryAwaitRelease()
                            }
                            false -> Unit
                        }
                    } finally {
                        // Also runs if cancelled mid-hold, so the TV never keeps OK held down
                        if (held) currentOnKey(TvKey.OK, KeyAction.LONG_END)
                    }
                })
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
                            step(if (dx > 0) TvKey.RIGHT else TvKey.LEFT)
                            dx -= stepPx * sign(dx)
                            dy = 0f
                        } else if (abs(dy) > abs(dx) && abs(dy) >= stepPx) {
                            step(if (dy > 0) TvKey.DOWN else TvKey.UP)
                            dy -= stepPx * sign(dy)
                            dx = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        EdgeArrow(Icons.Default.KeyboardArrowUp, lit == TvKey.UP, Modifier.align(Alignment.TopCenter))
        EdgeArrow(Icons.Default.KeyboardArrowDown, lit == TvKey.DOWN, Modifier.align(Alignment.BottomCenter))
        EdgeArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, lit == TvKey.LEFT, Modifier.align(Alignment.CenterStart))
        EdgeArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, lit == TvKey.RIGHT, Modifier.align(Alignment.CenterEnd))
        Text(
            "Swipe to move · tap for OK\nHold OK for options",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            // Clear of the edge arrows, which sit 12dp in and 28dp wide
            modifier = Modifier.padding(horizontal = 52.dp)
        )
    }
}

/** One of the touchpad's four arrows: a faint hint at rest, teal for a moment when its step is sent. */
@Composable
private fun EdgeArrow(icon: ImageVector, lit: Boolean, modifier: Modifier) {
    // Lights at once, so it is in step with the haptic tick, and fades back
    val tint by animateColorAsState(
        if (lit) MaterialTheme.colorScheme.primary else Faint,
        tween(if (lit) 0 else 200),
        label = "edge"
    )
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.padding(12.dp).size(28.dp))
}

/** How long a key must be held before it counts as a long press instead of a tap. */
private const val HOLD_THRESHOLD_MS = 400L

/**
 * A key that taps on a short press. Held past [HOLD_THRESHOLD_MS] it sends a long press
 * (key down on the TV), released when the finger lifts, so volume repeats and power opens its menu.
 *
 * The thumb gets an answer before the TV does: a tick and a slight shrink on touch, then a firmer
 * buzz and a ring when the hold begins, which is how the user learns that holding repeats.
 */
@Composable
private fun RemoteKey(
    key: TvKey,
    onKey: (TvKey, KeyAction) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val currentOnKey by rememberUpdatedState(onKey)
    val view = LocalView.current
    KeySurface(
        enabled = enabled,
        pressed = pressed,
        held = held,
        shape = shape,
        container = container,
        border = border,
        // The touch handling below is invisible to accessibility services, so say what a double-tap does
        modifier = modifier.semantics {
            if (enabled) onClick { currentOnKey(key, KeyAction.TAP); true } else disabled()
        }.pointerInput(key, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                pressed = true
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                try {
                    // null means the hold timeout expired before release; true = released, false = cancelled
                    val released = withTimeoutOrNull(HOLD_THRESHOLD_MS) { tryAwaitRelease() }
                    when (released) {
                        true -> currentOnKey(key, KeyAction.TAP)
                        null -> {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            currentOnKey(key, KeyAction.LONG_START)
                            held = true
                            tryAwaitRelease()
                        }
                        false -> Unit
                    }
                } finally {
                    // Also runs when this block is cancelled mid-hold (the keys got disabled, or the screen
                    // went away): without it the TV would keep the key held down
                    if (held) currentOnKey(key, KeyAction.LONG_END)
                    held = false
                    pressed = false
                }
            })
        },
        content = content
    )
}

/**
 * What every key looks like: [container] at rest, shrunk a little and tinted teal while [pressed],
 * ringed in teal while [held]. Shrinking is quick and the spring back slower, so a fast tap still shows.
 */
@Composable
internal fun KeySurface(
    enabled: Boolean,
    pressed: Boolean,
    held: Boolean,
    shape: Shape,
    container: Color,
    border: BorderStroke?,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val teal = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(if (pressed) 80 else 150), label = "press")
    val ring by animateFloatAsState(if (held) 1f else 0f, tween(150), label = "hold")
    // Teal on the filled OK key would show nothing, so that one darkens instead
    val pressTint = when {
        !pressed -> Color.Transparent
        container == teal -> Color.Black.copy(alpha = 0.25f)
        else -> teal.copy(alpha = 0.25f)
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clip(shape)
            .background(container)
            .background(pressTint)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .border(2.dp, teal.copy(alpha = ring), shape)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private val previewState = RemoteUiState(
    mode = RemoteMode.Connected(
        DiscoveredTv(name = "living-room-tv", friendlyName = "Living Room TV", host = "192.168.1.15", port = 6466)
    )
)

@Composable
private fun RemotePreview(
    uiState: RemoteUiState = previewState,
    padMode: PadMode = PadMode.DPAD,
    showHoldTip: Boolean = false,
    airOffer: AirOffer = AirOffer(inSwitch = uiState.airMouse.linked),
    askMode: AskMode? = AskMode.VOICE,
    youTubeTip: Boolean = false
) {
    AirMouseTVRemoteTheme {
        RemoteContent(
            uiState, "Living Room TV", padMode, {}, AirPadActions(), airOffer, {}, {}, {}, {},
            showHoldTip, {}, { _, _ -> }, {}, {}, {},
            youTubeKey = uiState.foregroundApp?.startsWith("com.google.android.youtube.tv") == true && uiState.imeField == null,
            showYouTubeTip = youTubeTip,
            askMode = askMode
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Voice: listening while held")
@Composable
private fun RemoteVoiceListeningPreview() = RemotePreview(
    previewState.copy(voice = VoiceState.Listening(List(21) { (it % 7) * 0.06f }))
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Voice: slid off, release to cancel")
@Composable
private fun RemoteVoiceCancelPreview() = RemotePreview(
    previewState.copy(voice = VoiceState.Listening(List(21) { (it % 5) * 0.06f }, cancelArmed = true))
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Voice: tap to start, tap to stop (TalkBack)")
@Composable
private fun RemoteVoiceTapPreview() = RemotePreview(
    previewState.copy(voice = VoiceState.Listening(List(21) { (it % 6) * 0.06f }, tapMode = true))
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Voice: sent")
@Composable
private fun RemoteVoiceSentPreview() = RemotePreview(previewState.copy(voice = VoiceState.Sent))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Voice: TV without voice, Ask key dims (Aa)")
@Composable
private fun RemoteNoVoicePreview() = RemotePreview(askMode = AskMode.TYPE)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "YouTube: search key, first time (tip)")
@Composable
private fun RemoteYouTubeTipPreview() = RemotePreview(
    previewState.copy(foregroundApp = "com.google.android.youtube.tv"), youTubeTip = true
)

private val airMousePreviewState = previewState.copy(
    airMouse = AirMouseUiState(available = true, linked = true, link = PointerLinkState.READY, hasGyroscope = true)
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Air mouse")
@Composable
private fun RemoteAirMousePreview() = RemotePreview(airMousePreviewState, padMode = PadMode.AIR_MOUSE)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Air mouse: pointer link lost")
@Composable
private fun RemoteAirMouseLostPreview() = RemotePreview(
    airMousePreviewState.copy(airMouse = airMousePreviewState.airMouse.copy(link = PointerLinkState.CONNECTING)),
    padMode = PadMode.AIR_MOUSE
)

@Preview(showBackground = true, widthDp = 320, heightDp = 640, name = "Air mouse: narrow screen")
@Composable
private fun RemoteAirMouseNarrowPreview() = RemotePreview(airMousePreviewState, padMode = PadMode.AIR_MOUSE)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "D-pad")
@Composable
private fun RemoteDPadPreview() = RemotePreview()

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "First launch: hold tip")
@Composable
private fun RemoteHoldTipPreview() = RemotePreview(showHoldTip = true)

@Preview(showBackground = true, widthDp = 360, heightDp = 520, name = "Short screen: scrolls")
@Composable
private fun RemoteShortScreenPreview() = RemotePreview()

@Preview(showBackground = true, widthDp = 320, heightDp = 640, name = "Narrow screen")
@Composable
private fun RemoteNarrowScreenPreview() = RemotePreview()

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Touchpad")
@Composable
private fun RemoteTouchpadPreview() = RemotePreview(padMode = PadMode.TOUCHPAD)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Reconnecting")
@Composable
private fun RemoteReconnectingPreview() = RemotePreview(previewState.copy(isReconnecting = true))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Remote in YouTube")
@Composable
private fun RemoteInYouTubePreview() = RemotePreview(previewState.copy(foregroundApp = "com.google.android.youtube.tv"))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Apps: opening an app")
@Composable
private fun RemoteAppOpeningPreview() = RemotePreview(
    previewState.copy(appLaunch = AppLaunch(TvApp("com.netflix.ninja", "Netflix"), AppLaunch.Stage.OPENING))
)

@Composable
private fun SheetPreview(content: @Composable () -> Unit) {
    AirMouseTVRemoteTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 16.dp)) { content() }
    }
}

@Preview(showBackground = true, widthDp = 390, name = "Typing: on the TV")
@Composable
private fun TextEntryOnTvPreview() = SheetPreview {
    TvInputSheetContent(
        "Search movies, shows, apps and more", "Living Room TV", TextFieldValue("stranger th"), {}, TvTextStatus.ON_TV,
        Icons.AutoMirrored.Filled.ArrowForward, "Send to TV", ImeAction.Send, {}, true, remember { FocusRequester() }
    ) {}
}

@Preview(showBackground = true, widthDp = 390, name = "Typing: not reaching the TV")
@Composable
private fun TextEntryNotOnTvPreview() = SheetPreview {
    TvInputSheetContent(
        "Search", "Living Room TV", TextFieldValue("hello"), {}, TvTextStatus.NOT_ON_TV,
        Icons.AutoMirrored.Filled.ArrowForward, "Send to TV", ImeAction.Send, {}, true, remember { FocusRequester() }
    ) { NotTakingText({}, {}) }
}

@Preview(showBackground = true, widthDp = 390, name = "YouTube search")
@Composable
private fun YouTubeSearchPreview() = SheetPreview {
    TvInputSheetContent(
        "Search YouTube", "Living Room TV", TextFieldValue("lofi beats"), {}, null,
        Icons.Default.Search, "Search on TV", ImeAction.Search, {}, true, remember { FocusRequester() }
    ) {
        Text("Results open on the TV.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val airOfferPreviewState = previewState.copy(
    airMouse = AirMouseUiState(available = true, hasGyroscope = true)
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Air mouse offered: PRO chip, tip, menu dot")
@Composable
private fun RemoteAirOfferedPreview() = RemotePreview(
    airOfferPreviewState, airOffer = AirOffer(inSwitch = true, proChip = true, tip = true, menuDot = true)
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Air mouse tapped before setup")
@Composable
private fun RemoteAirOfferPadPreview() = RemotePreview(
    airOfferPreviewState, padMode = PadMode.AIR_MOUSE, airOffer = AirOffer(inSwitch = true, proChip = true)
)

@Preview(showBackground = true, widthDp = 320, heightDp = 640, name = "Air mouse offered: narrow screen")
@Composable
private fun RemoteAirOfferedNarrowPreview() = RemotePreview(
    airOfferPreviewState, airOffer = AirOffer(inSwitch = true, proChip = true, tip = true)
)
