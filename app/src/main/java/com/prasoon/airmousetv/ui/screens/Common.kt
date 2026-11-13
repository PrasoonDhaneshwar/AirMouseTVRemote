package com.prasoon.airmousetv.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.ui.theme.Amber
import com.prasoon.airmousetv.ui.theme.Hairline

/** How long one screen takes to fade into the next. Short enough that a remote never feels like it is loading. */
internal const val SCREEN_FADE_MS = 250

/** The one filled button on a screen: its main action. */
@Composable
internal fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
}

/** An outlined button for the other thing a screen offers. */
@Composable
internal fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.outline else Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
    }
}

/** An amber-edged card for something that went wrong and can be put right from here. */
@Composable
internal fun NoticeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.copy(alpha = 0.08f))
            .border(1.dp, Amber.copy(alpha = 0.3f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content
    )
}

/**
 * The last non-null [value] seen. A screen stays on show for [SCREEN_FADE_MS] after the state it
 * draws has gone (the TV it was connected to, say), and should fade out looking as it did.
 */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

/** The buzz for "that was refused" (a wrong code). Android only has a dedicated one from 11 on. */
internal fun View.hapticReject() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    )
}

/** The buzz for "that worked" (paired). Android only has a dedicated one from 11 on. */
internal fun View.hapticConfirm() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )
}
