package com.prasoon.airmousetv.ui.components


import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ButtonDPad(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCenter: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .size(if (isCenter) 96.dp else 72.dp)
            .padding(4.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCenter)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = label,
            fontSize = if (isCenter) 24.sp else 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}