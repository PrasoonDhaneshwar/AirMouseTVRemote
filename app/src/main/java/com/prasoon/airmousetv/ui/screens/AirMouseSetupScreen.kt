package com.prasoon.airmousetv.ui.screens

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.prasoon.airmousetv.airmouse.model.AirMouseSetupStep
import com.prasoon.airmousetv.airmouse.model.AirMouseUiState
import com.prasoon.airmousetv.airmouse.model.NearbyDevice
import com.prasoon.airmousetv.airmouse.model.PointerLinkState
import com.prasoon.airmousetv.airmouse.model.TvSearch
import com.prasoon.airmousetv.data.model.KeyAction
import com.prasoon.airmousetv.data.model.TvKey
import com.prasoon.airmousetv.presentation.RemoteViewModel
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.PadSurface

/**
 * How long the phone asks to stay visible to the TV, in seconds. Two minutes is Android's own
 * default and enough to walk through the TV's settings; the user can ask again if it runs out.
 */
private const val VISIBLE_FOR_S = 120

/** The permissions the air mouse needs on Android 12 and later, where Bluetooth is asked for at runtime. */
@RequiresApi(Build.VERSION_CODES.S)
private val BLUETOOTH_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE
)

/** True if the app may use Bluetooth. Before Android 12 the permission comes with the install. */
internal fun Context.hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    BLUETOOTH_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

/**
 * Asks for the Bluetooth permission if it is still needed, then runs [onGranted]. Returns the
 * function that starts this. A refusal is answered with a toast: the remote works without it.
 */
@Composable
internal fun rememberBluetoothPermissionRequest(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            onGranted()
        } else {
            Toast.makeText(context, "The air mouse needs the Nearby devices permission", Toast.LENGTH_LONG).show()
        }
    }
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.hasBluetoothPermission()) {
            onGranted()
        } else {
            launcher.launch(BLUETOOTH_PERMISSIONS)
        }
    }
}

/** Returns a function that shows Android's own "turn on Bluetooth?" prompt. Needs the Bluetooth permission. */
@Composable
internal fun rememberBluetoothEnableRequest(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return { launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
}

/**
 * The one-time setup that pairs the TV with the phone as a Bluetooth mouse. The pairing itself is
 * the system's (the TV's Bluetooth settings, and Android's pairing dialog on the phone), so these
 * screens only say what to do next and move on by themselves when the TV has connected.
 */
@Composable
internal fun AirMouseSetupScreen(
    viewModel: RemoteViewModel,
    airMouse: AirMouseUiState,
    tvName: String,
    onKey: (TvKey, KeyAction) -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    val step = airMouse.setup ?: return
    BackHandler(onBack = viewModel::backInAirMouseSetup)

    val begin = rememberBluetoothPermissionRequest(onGranted = viewModel::beginAirMouseSetup)
    val turnOnBluetooth = rememberBluetoothEnableRequest()
    val makeVisible = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Android answers with how long the phone stays visible, or "cancelled" if the user said no
        if (result.resultCode != Activity.RESULT_CANCELED) viewModel.awaitTvPairing()
    }
    val askToBeVisible = {
        makeVisible.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, VISIBLE_FOR_S)
        )
    }

    // Bluetooth turns out to be off once the setup starts: ask once, and leave a button for a second try
    var askedToTurnOn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(airMouse.link) {
        if (airMouse.link == PointerLinkState.BLUETOOTH_OFF && !askedToTurnOn) {
            askedToTurnOn = true
            turnOnBluetooth()
        }
    }

    AirMouseSetupContent(
        step = step,
        link = airMouse.link,
        phoneName = airMouse.phoneName,
        search = airMouse.search,
        tvName = tvName,
        needsPermission = !LocalContext.current.hasBluetoothPermission(),
        onKey = onKey,
        onBegin = begin,
        onTurnOnBluetooth = turnOnBluetooth,
        onTvSearching = askToBeVisible,
        onPair = viewModel::pairAirMouseWith,
        onBack = viewModel::backInAirMouseSetup,
        onClose = viewModel::closeAirMouseSetup,
        onDone = viewModel::finishAirMouseSetup,
        onCopyDiagnostics = onCopyDiagnostics
    )
}

@Composable
private fun AirMouseSetupContent(
    step: AirMouseSetupStep,
    link: PointerLinkState,
    phoneName: String?,
    search: TvSearch,
    tvName: String,
    needsPermission: Boolean,
    onKey: (TvKey, KeyAction) -> Unit,
    onBegin: () -> Unit,
    onTurnOnBluetooth: () -> Unit,
    onTvSearching: () -> Unit,
    onPair: (address: String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        when (step) {
            AirMouseSetupStep.INTRO -> Intro(link, needsPermission, onBegin, onTurnOnBluetooth, onClose)
            AirMouseSetupStep.ON_TV -> {
                val pairingWith = search.pairingWith
                when {
                    pairingWith != null -> Pairing(pairingWith, onKey, onBack)
                    search.possible -> FindTv(tvName, search.candidates, onKey, onPair, onTvSearching, onBack)
                    // The phone can't look for the TV (before Android 12), so the TV looks for the phone
                    else -> OnTv(onKey, onTvSearching, onBack)
                }
            }
            AirMouseSetupStep.WAITING -> Waiting(phoneName, onKey, onTvSearching, onBack)
            AirMouseSetupStep.DONE -> Done(tvName, onDone)
            AirMouseSetupStep.UNSUPPORTED -> Unsupported(blocked = link == PointerLinkState.BLOCKED, onClose, onCopyDiagnostics)
        }
    }
}

/** The top row of a setup screen: a way out on the left, and the progress bars once the steps have begun. */
@Composable
private fun TopRow(icon: ImageVector, description: String, onClick: () -> Unit, stepsDone: Int?) {
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        // Pulled left so the icon, not its touch area, lines up with the text below
        IconButton(onClick = onClick, modifier = Modifier.offset(x = (-12).dp).size(48.dp)) {
            Icon(icon, contentDescription = description)
        }
        if (stepsDone != null) StepBars(stepsDone, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun StepBars(done: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Step $done of 3" },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            val color = if (index < done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            Box(Modifier.size(24.dp, 4.dp).background(color, CircleShape))
        }
    }
}

/** The part of a setup screen above its buttons; scrolls if large text makes it taller than the screen. */
@Composable
private fun ColumnScope.Body(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
}

@Composable
private fun StepCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Headline(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold), modifier = modifier)
}

@Composable
private fun Explanation(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** A card of short numbered lines. */
@Composable
private fun NumberedCard(lines: List<String>, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PadSurface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        lines.forEachIndexed { index, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Text(line, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
            }
        }
    }
}

@Composable
private fun ColumnScope.Intro(
    link: PointerLinkState,
    needsPermission: Boolean,
    onBegin: () -> Unit,
    onTurnOnBluetooth: () -> Unit,
    onClose: () -> Unit
) {
    TopRow(Icons.Default.Close, "Close", onClose, stepsDone = null)
    Body {
        PointAtTvDrawing(Modifier.padding(top = 12.dp).fillMaxWidth().height(150.dp))
        Headline("Point your phone\nat the TV", Modifier.padding(top = 20.dp))
        Explanation("A pointer on the TV follows your hand, like a laser pointer.", Modifier.padding(top = 10.dp))
        Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IntroPoint(Icons.Default.Bluetooth, "Uses Bluetooth. Nothing to install on the TV.")
            IntroPoint(Icons.Default.Schedule, "One-time setup, about a minute.")
            IntroPoint(Icons.Default.SettingsRemote, "The remote keys keep working as they do now.")
        }
    }
    when (link) {
        // The setup has started and Android hasn't yet said whether this phone can be a mouse
        PointerLinkState.CONNECTING -> Row(
            Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
            Text("Checking this phone…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PointerLinkState.BLUETOOTH_OFF -> {
            SetupNote("Bluetooth is off on this phone.")
            PrimaryAction("Turn on Bluetooth", onTurnOnBluetooth)
        }
        else -> {
            if (needsPermission) SetupNote("Android will ask to allow Bluetooth next.")
            PrimaryAction("Set up air mouse", onBegin)
        }
    }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(48.dp)) {
        Text("Not now", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
}

@Composable
private fun IntroPoint(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
    }
}

/** The D-pad's size inside the setup: big enough to hit without looking, small enough to leave the instructions on screen. */
private val SETUP_DPAD_SIZE = 190.dp

/**
 * The few remote keys it takes to walk through the TV's settings, on the setup screens that ask
 * for exactly that. Without them the user would have to leave the setup, or pick up the TV's own
 * remote, to follow the steps the screen is showing.
 */
@Composable
private fun SetupRemote(onKey: (TvKey, KeyAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabeledKey("BACK", enabled = true, onClick = null, key = TvKey.BACK, onKey = onKey) {
            KeyIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back on the TV")
        }
        DPad(onKey, enabled = true, size = SETUP_DPAD_SIZE)
        LabeledKey("HOME", enabled = true, onClick = null, key = TvKey.HOME, onKey = onKey) {
            KeyIcon(Icons.Default.Home, "Home on the TV")
        }
    }
}

@Composable
private fun ColumnScope.FindTv(
    tvName: String,
    candidates: List<NearbyDevice>,
    onKey: (TvKey, KeyAction) -> Unit,
    onPair: (address: String) -> Unit,
    onLetTvFindPhone: () -> Unit,
    onBack: () -> Unit
) {
    TopRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, stepsDone = 1)
    Body {
        Spacer(Modifier.height(28.dp))
        StepCaption("STEP 1 · FIND THE TV")
        Headline("Looking for $tvName over Bluetooth", Modifier.padding(top = 6.dp))
        if (candidates.isEmpty()) {
            Explanation(
                "Not found yet? Open Add accessory on the TV with the keys below. That makes the TV visible, and pairing starts by itself.",
                Modifier.padding(top = 10.dp)
            )
            NumberedCard(listOf("Settings", "Remotes & Accessories", "Add accessory"), Modifier.padding(top = 16.dp))
        } else {
            // Found, but under a name the app can't match to the connected TV, so the user says which it is
            Explanation("Is one of these your TV?", Modifier.padding(top = 10.dp))
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { device ->
                    SecondaryAction("Pair with ${device.name ?: device.address}", { onPair(device.address) })
                }
            }
        }
        SetupRemote(onKey, Modifier.padding(top = 20.dp))
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp)
        Text("Searching…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // The older way round, for a TV the phone can't find: the TV looks for the phone
    TextButton(onClick = onLetTvFindPhone, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text("Let the TV find this phone instead", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Shown while a pairing the phone started is waiting to be accepted. Both sides ask: the TV with a
 * screen of its own, which the keys here can answer, and the phone with a dialog or, when Android
 * decides so, only a notification.
 */
@Composable
private fun ColumnScope.Pairing(deviceName: String, onKey: (TvKey, KeyAction) -> Unit, onBack: () -> Unit) {
    TopRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, stepsDone = 2)
    Body {
        Spacer(Modifier.height(28.dp))
        StepCaption("STEP 2 · ACCEPT ON BOTH")
        Headline("Pairing with $deviceName", Modifier.padding(top = 6.dp))
        NumberedCard(
            listOf(
                "On the TV, choose Pair. The OK key below does it.",
                "On this phone, accept the pairing request. If none appears, pull down the notifications and tap it."
            ),
            Modifier.padding(top = 16.dp)
        )
        SetupRemote(onKey, Modifier.padding(top = 20.dp))
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp)
        Text("Waiting for both to accept…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.OnTv(onKey: (TvKey, KeyAction) -> Unit, onTvSearching: () -> Unit, onBack: () -> Unit) {
    TopRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, stepsDone = 1)
    Body {
        Spacer(Modifier.height(28.dp))
        StepCaption("STEP 1 · ON THE TV")
        Headline("Open “Add accessory”", Modifier.padding(top = 6.dp))
        NumberedCard(listOf("Settings", "Remotes & Accessories", "Add accessory"), Modifier.padding(top = 16.dp))
        Explanation("Names vary by TV. Look for Bluetooth or Accessories in Settings.", Modifier.padding(top = 12.dp))
        SetupRemote(onKey, Modifier.padding(top = 20.dp))
    }
    SetupNote("Android will ask to make this phone visible.")
    PrimaryAction("The TV is searching", onTvSearching)
}

@Composable
private fun ColumnScope.Waiting(phoneName: String?, onKey: (TvKey, KeyAction) -> Unit, onMakeVisibleAgain: () -> Unit, onBack: () -> Unit) {
    // The TV lists the phone under its Bluetooth name, which the user may never have seen
    val name = phoneName?.takeIf { it.isNotBlank() }
    TopRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, stepsDone = 2)
    Body {
        Spacer(Modifier.height(28.dp))
        StepCaption("STEP 2 · ON THE TV")
        Headline(if (name != null) "Pick “$name” in the TV's list" else "Pick this phone in the TV's list", Modifier.padding(top = 6.dp))
        Explanation(
            "Then check that the code on the TV matches the one Android shows on this phone, and accept on both. " +
                "If nothing appears here, pull down the notifications and tap the pairing request.",
            Modifier.padding(top = 10.dp)
        )
        SetupRemote(onKey, Modifier.padding(top = 20.dp))
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp)
        Text("Waiting for the TV…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SecondaryAction("Don't see it? Make visible again", onMakeVisibleAgain)
}

@Composable
private fun ColumnScope.Done(tvName: String, onDone: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { StepBars(3) }
    Body {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val teal = MaterialTheme.colorScheme.primary
            Box(Modifier.size(150.dp).background(teal.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(110.dp).background(teal.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(80.dp).background(teal, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                    }
                }
            }
            Headline("Air mouse is ready", Modifier.padding(top = 24.dp))
            Explanation("Linked to $tvName", Modifier.padding(top = 6.dp))
        }
        NumberedCard(
            listOf("Hold your thumb on the pad", "Point the phone; the pointer follows", "Lift your thumb to re-aim"),
            Modifier.padding(top = 32.dp)
        )
    }
    PrimaryAction("Try it", onDone)
}

@Composable
private fun ColumnScope.Unsupported(blocked: Boolean, onClose: () -> Unit, onCopyDiagnostics: () -> Unit) {
    TopRow(Icons.Default.Close, "Close", onClose, stepsDone = null)
    Body {
        Headline(
            if (blocked) "Another app is using the phone as a mouse" else "This phone can't be a Bluetooth mouse",
            Modifier.padding(top = 24.dp)
        )
        Explanation(
            if (blocked) {
                "Android lets one app at a time act as a Bluetooth mouse or keyboard. Close the other one, then set up the air mouse again."
            } else {
                "Its maker has turned this Bluetooth feature off. The remote still works as before, and Touchpad mode can move around the TV."
            },
            Modifier.padding(top = 10.dp)
        )
    }
    PrimaryAction("Back to remote", onClose)
    SecondaryAction("Copy diagnostics", onCopyDiagnostics, Modifier.padding(top = 10.dp))
}

@Composable
private fun SetupPreview(step: AirMouseSetupStep, link: PointerLinkState = PointerLinkState.IDLE, search: TvSearch = TvSearch()) {
    AirMouseTVRemoteTheme {
        AirMouseSetupContent(step, link, "Pixel 8", search, "Living Room TV", needsPermission = true, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: intro")
@Composable
private fun SetupIntroPreview() = SetupPreview(AirMouseSetupStep.INTRO)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: checking the phone")
@Composable
private fun SetupCheckingPreview() = SetupPreview(AirMouseSetupStep.INTRO, PointerLinkState.CONNECTING)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: 1 on the TV")
@Composable
private fun SetupOnTvPreview() = SetupPreview(AirMouseSetupStep.ON_TV, PointerLinkState.NEEDS_PAIRING)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: 2 waiting for the TV")
@Composable
private fun SetupWaitingPreview() = SetupPreview(AirMouseSetupStep.WAITING, PointerLinkState.NEEDS_PAIRING)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: ready")
@Composable
private fun SetupDonePreview() = SetupPreview(AirMouseSetupStep.DONE, PointerLinkState.READY)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: phone not supported")
@Composable
private fun SetupUnsupportedPreview() = SetupPreview(AirMouseSetupStep.UNSUPPORTED, PointerLinkState.UNSUPPORTED)

@Preview(showBackground = true, widthDp = 320, heightDp = 568, name = "Setup: small screen")
@Composable
private fun SetupSmallScreenPreview() = SetupPreview(AirMouseSetupStep.ON_TV, PointerLinkState.NEEDS_PAIRING)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: 1 looking for the TV")
@Composable
private fun SetupFindTvPreview() = SetupPreview(AirMouseSetupStep.ON_TV, PointerLinkState.NEEDS_PAIRING, TvSearch(possible = true))

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: 1 which one is the TV")
@Composable
private fun SetupPickTvPreview() = SetupPreview(
    AirMouseSetupStep.ON_TV, PointerLinkState.NEEDS_PAIRING,
    TvSearch(possible = true, candidates = listOf(NearbyDevice("00:11:22:33:44:55", "Bedroom TV", looksLikeTv = true)))
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Setup: 2 accept the pairing")
@Composable
private fun SetupPairingPreview() = SetupPreview(
    AirMouseSetupStep.ON_TV, PointerLinkState.NEEDS_PAIRING, TvSearch(possible = true, pairingWith = "Living Room TV")
)
