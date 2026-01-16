package com.prasoon.airmousetv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.unit.sp

//@Composable
//fun AirMouseScreen(
//    modifier: Modifier = Modifier.Companion,
//    onTouch: (Offset) -> Unit,
//    sensitivity: Float,
//    onSensitivityChange: (Float) -> Unit
//) {
//    Column {
//        TopAppBar(
//            title = { Text("Air Mouse") },
//            actions = {
//                Slider(value = sensitivity, onValueChange = onSensitivityChange)
//            }
//        )
//        Box(
//            modifier = modifier
//                .weight(1f)
//                .fillMaxWidth()
//                .background(Color.Companion.Black.copy(alpha = 0.9f))
//                .pointerInput(Unit) { detectDragGestures(onDrag = { _, drag -> onTouch(drag) }) }
//        ) {
//            Text(
//                "🖱️",
//                modifier = Modifier.Companion.align(Alignment.Companion.Center),
//                fontSize = 48.sp
//            )
//        }
//        Row {
//            MouseButton("LMB") { sendClick(LEFT) }
//            Button("RECENTER") { sendRecenter() }
//            MouseButton("RMB") { sendClick(RIGHT) }
//        }
//    }
//}