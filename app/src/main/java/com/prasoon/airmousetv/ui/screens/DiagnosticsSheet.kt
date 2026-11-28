package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.ui.theme.AirMouseTVRemoteTheme
import com.prasoon.airmousetv.ui.theme.Charcoal
import com.prasoon.airmousetv.ui.theme.Faint
import com.prasoon.airmousetv.ui.theme.Muted
import com.prasoon.airmousetv.ui.theme.SheetSurface
import com.prasoon.airmousetv.ui.theme.SurfaceMid
import com.prasoon.airmousetv.ui.theme.Teal
import com.prasoon.airmousetv.ui.theme.TextSecondary

/** Light enough on the sheet to read as the list's main text, a step under white. */
private val ListText = Color(0xFFE6E9ED)

/**
 * What Send diagnostics will share, shown before anything leaves the phone: what the report holds,
 * the one thing it never holds, and the report itself. Send opens an email app with it;
 * Copy instead puts it on the clipboard, as the old menu item did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsSheet(
    report: String,
    maxEvents: Int,
    tvName: String,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetSurface,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(36.dp, 4.dp).background(Faint, CircleShape)) }
    ) {
        DiagnosticsContent(report, maxEvents, tvName, onSend, onCopy)
    }
}

@Composable
private fun DiagnosticsContent(report: String, maxEvents: Int, tvName: String, onSend: () -> Unit, onCopy: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Send diagnostics",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            Text(tvName, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "A record of what the TV did lately, to find out why something didn't work. " +
                "Nothing leaves your phone until you pick an app to send it with.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TextSecondary
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Included(Icons.Default.Check, Teal, "The TV's model and its address on your Wi-Fi", ListText)
            Included(Icons.Default.Check, Teal, "The apps the TV showed, and the buttons you pressed", ListText)
            Included(Icons.Default.Check, Teal, "Connection, volume and voice events", ListText)
            Included(Icons.Default.Block, Muted, "Nothing you typed or said", TextSecondary)
        }
        // Both ways scrollable: log lines are long, and wrapping them would make the times hard to scan
        Box(
            Modifier.fillMaxWidth().height(118.dp)
                .background(Charcoal, RoundedCornerShape(12.dp))
                .border(1.dp, SurfaceMid, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 17.sp, color = Muted, softWrap = false)
        }
        Text("The last $maxEvents events · scroll to read all", fontSize = 12.sp, color = Muted)
        Button(onClick = onSend, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Send", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
        TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
            Text("Copy instead", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        }
    }
}

@Composable
private fun Included(icon: ImageVector, tint: Color, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = color)
    }
}

private val previewReport = """
    App 3.0 (7)
    Phone: Google Pixel 8, Android 15
    TV: Hisense 65A6K at 192.168.1.20, remote service 5.2

    14:02:11 TV Hisense 65A6K, remote service 5.2; offers 639, using 615, text input offered
    14:02:12 TV pings, using 40s silence timeout
    14:03:40 ImeKeyInject app=com.google.android.youtube.tv textField=false
    14:03:52 sent DOWN TAP ×6, last at 14:03:58
    14:05:07 TV opened voice session 3 for com.google.android.katniss
    14:05:09 TV heard the end of speech in voice session 3
""".trimIndent()

@Composable
private fun DiagnosticsPreview(report: String) = AirMouseTVRemoteTheme {
    Box(Modifier.background(SheetSurface).padding(top = 16.dp)) {
        DiagnosticsContent(report, maxEvents = 120, tvName = "Living Room TV", onSend = {}, onCopy = {})
    }
}

@Preview(widthDp = 390, name = "Diagnostics: what will be sent")
@Composable
private fun DiagnosticsSheetPreview() = DiagnosticsPreview(previewReport)

@Preview(widthDp = 320, name = "Diagnostics: narrow phone, no TV yet")
@Composable
private fun DiagnosticsSheetEmptyPreview() =
    DiagnosticsPreview("App 3.0 (7)\nPhone: Google Pixel 8, Android 15\nTV: not connected yet")
