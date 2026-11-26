package com.prasoon.airmousetv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.data.api.TvApp
import com.prasoon.airmousetv.data.api.TvApps
import com.prasoon.airmousetv.data.model.AppLaunch
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Faint
import com.prasoon.airmousetv.ui.theme.Muted
import com.prasoon.airmousetv.ui.theme.SheetSurface
import com.prasoon.airmousetv.ui.theme.SurfaceHigh
import com.prasoon.airmousetv.ui.theme.SurfaceMid

/** Tiles across the sheet: four fit a 320 dp phone with the sheet's margins. */
private const val TILES_PER_ROW = 4

/** What the Apps sheet tells the rest of the app. */
internal class AppsActions(
    val onOpen: (TvApp) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onRename: (pkg: String, name: String) -> Unit = { _, _ -> },
)

/**
 * The TV's apps, opened by a tap: Recent (what the TV has shown, newest first), then Popular. The TV
 * can't list its apps, so this is what the phone has seen plus common ones. Long-press a recent app
 * to rename it or take it off the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppsSheet(
    recent: List<TvApp>,
    popular: List<TvApp>,
    onTv: String?,
    tvName: String,
    actions: AppsActions,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetSurface,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(36.dp, 4.dp).background(Faint, CircleShape)) }
    ) {
        AppsContent(recent, popular, onTv, tvName, actions, onOpened = onDismiss)
    }
}

@Composable
private fun AppsContent(
    recent: List<TvApp>,
    popular: List<TvApp>,
    onTv: String?,
    tvName: String,
    actions: AppsActions,
    onOpened: () -> Unit
) {
    var renaming by remember { mutableStateOf<TvApp?>(null) }
    val open = { app: TvApp ->
        actions.onOpen(app)
        onOpened()
    }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Apps", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(tvName, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (recent.isNotEmpty()) {
            SectionLabel("RECENT")
            TileGrid(recent) { app ->
                AppTile(app, onTv = app.pkg == onTv, onClick = { open(app) }, menu = { close ->
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { close(); renaming = app })
                    DropdownMenuItem(text = { Text("Remove from recent") }, onClick = { close(); actions.onRemove(app.pkg) })
                })
            }
        }
        if (popular.isNotEmpty()) {
            SectionLabel("POPULAR")
            TileGrid(popular) { app -> AppTile(app, onTv = app.pkg == onTv, onClick = { open(app) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = Muted, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Text(
                if (recent.isEmpty()) "Apps you open on the TV will show up here, newest first."
                else "The TV doesn't share its full app list, so this shows the apps you've opened on it, plus popular ones.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = Muted
            )
        }
    }
    renaming?.let { app ->
        RenameDialog(app, onRename = { actions.onRename(app.pkg, it) }, onDismiss = { renaming = null })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.9.sp, fontWeight = FontWeight.Medium),
        color = Muted
    )
}

/** [apps] in rows of [TILES_PER_ROW]; a short last row keeps the columns of the rows above. */
@Composable
private fun TileGrid(apps: List<TvApp>, tile: @Composable (TvApp) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        apps.chunked(TILES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app -> Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { tile(app) } }
                repeat(TILES_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One app: its first letter on a key-coloured tile (the TV sends no icons), and its name. The app on
 * screen now gets a teal ring and ON TV. With a [menu], a long press opens it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: TvApp,
    onTv: Boolean,
    onClick: () -> Unit,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClickLabel = "Open on the TV",
                onLongClickLabel = if (menu != null) "Rename or remove" else null,
                onLongClick = menu?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                },
                onClick = onClick
            )
            .clearAndSetSemantics {
                contentDescription = if (onTv) "${app.name}, on the TV now" else app.name
            }
            .padding(vertical = 4.dp)
    ) {
        Box {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(shape)
                    .background(SurfaceMid)
                    .then(if (onTv) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
            }
            if (menu != null) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { menu { menuOpen = false } }
            }
        }
        Text(
            app.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (onTv) {
            Text("ON TV", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Names [app] on every TV. Emptied, it goes back to the app's own name. */
@Composable
private fun RenameDialog(app: TvApp, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(app.name) }
    val save = {
        onRename(name)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    placeholder = { Text(TvApps.nameFor(app.pkg)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() })
                )
                Text("Leave it empty to use the app's own name.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = save) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The card over the pad after a tap in the Apps sheet: "Opening …" until the TV shows the app, or
 * why it didn't open. A tap closes it.
 */
@Composable
internal fun AppLaunchCard(launch: AppLaunch?, tvName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Keeps the last card while it slides away
    var shown by remember { mutableStateOf(launch) }
    if (launch != null) shown = launch
    AnimatedVisibility(
        visible = launch != null,
        enter = fadeIn() + slideInVertically { -it / 3 },
        exit = fadeOut() + slideOutVertically { -it / 3 },
        modifier = modifier
    ) {
        val card = shown ?: return@AnimatedVisibility
        val name = card.app.name
        val (title, detail) = when (card.stage) {
            AppLaunch.Stage.OPENING -> "Opening $name" to "on $tvName"
            AppLaunch.Stage.NOT_INSTALLED -> "$name isn't on this TV" to "Its Play Store page is open on the TV."
            AppLaunch.Stage.DID_NOT_OPEN -> "$name didn't open" to "Open it on the TV. Apps from outside the Play Store may not open from the phone."
        }
        val shape = RoundedCornerShape(20.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(SheetSurface)
                .border(1.dp, SurfaceHigh, shape)
                .clickable(onClickLabel = "Close", role = Role.Button, onClick = onDismiss)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceMid), contentAlignment = Alignment.Center) {
                Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
                Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Nine dots, the usual "all apps" sign, drawn as dots rather than Material's squares. */
internal val AppsDotsIcon: ImageVector by lazy {
    ImageVector.Builder("AppsDots", 24.dp, 24.dp, 24f, 24f).apply {
        for (y in listOf(5f, 12f, 19f)) for (x in listOf(5f, 12f, 19f)) {
            path(fill = SolidColor(androidx.compose.ui.graphics.Color.White)) {
                moveTo(x - 2f, y)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 4f, dy1 = 0f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -4f, dy1 = 0f)
                close()
            }
        }
    }.build()
}

private val previewRecent = listOf(
    TvApp("com.google.android.youtube.tv", "YouTube"),
    TvApp("com.netflix.ninja", "Netflix"),
    TvApp("com.spotify.tv.android", "Spotify"),
    TvApp("com.stremio.one", "Stremio"),
    TvApp("com.teamsmart.videomanager.tv", "SmartTube"),
    TvApp("com.lagradost.cloudstream3", "CloudStream"),
)

@Composable
private fun AppsPreview(recent: List<TvApp>, popular: List<String>) {
    AirMouseTVRemoteTheme {
        Box(Modifier.background(SheetSurface).padding(top = 16.dp)) {
            AppsContent(
                recent, popular.map { TvApp(it, TvApps.nameFor(it)) }, onTv = recent.firstOrNull()?.pkg,
                tvName = "Living Room TV", actions = AppsActions(), onOpened = {}
            )
        }
    }
}

@Preview(widthDp = 390, name = "Apps: recent and popular")
@Composable
private fun AppsSheetPreview() = AppsPreview(previewRecent, TvApps.popular("US", previewRecent.map { it.pkg }))

@Preview(widthDp = 390, name = "Apps: first use")
@Composable
private fun AppsSheetFirstPreview() = AppsPreview(emptyList(), TvApps.popular("IN"))

@Preview(widthDp = 320, name = "Apps: narrow phone")
@Composable
private fun AppsSheetNarrowPreview() = AppsPreview(previewRecent, TvApps.popular("GB"))

@Preview(widthDp = 390, name = "Apps: opening, not installed, didn't open")
@Composable
private fun AppLaunchCardPreview() = AirMouseTVRemoteTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppLaunchCard(AppLaunch(TvApp("com.netflix.ninja", "Netflix"), AppLaunch.Stage.OPENING), "Living Room TV", {})
        AppLaunchCard(AppLaunch(TvApp("com.hulu.livingroomplus", "Hulu"), AppLaunch.Stage.NOT_INSTALLED), "Living Room TV", {})
        AppLaunchCard(AppLaunch(TvApp("com.stremio.one", "Stremio"), AppLaunch.Stage.DID_NOT_OPEN), "Living Room TV", {})
    }
}
