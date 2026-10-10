package com.prasoon.airmousetv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.prasoon.airmousetv.R

/** Space Grotesk is a variable font; the weight axis only applies on API 26+, older devices use its default weight. */
@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
    ).map { weight ->
        Font(
            R.font.space_grotesk,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val Typography = Typography(
    displayLarge = style(FontWeight.ExtraBold, 48, 52, -0.5),
    displayMedium = style(FontWeight.ExtraBold, 40, 44, -0.25),
    displaySmall = style(FontWeight.Bold, 32, 38),
    headlineLarge = style(FontWeight.Bold, 30, 36, 0.25),
    headlineMedium = style(FontWeight.Bold, 26, 32, 0.25),
    headlineSmall = style(FontWeight.Bold, 22, 28, 0.25),
    titleLarge = style(FontWeight.Bold, 20, 26, 0.5),
    titleMedium = style(FontWeight.SemiBold, 16, 22, 0.15),
    titleSmall = style(FontWeight.SemiBold, 14, 20, 0.1),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.25),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.25),
    bodySmall = style(FontWeight.Medium, 12, 16, 0.4),
    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.5),
    labelMedium = style(FontWeight.SemiBold, 12, 16, 0.8),
    labelSmall = style(FontWeight.SemiBold, 10, 14, 1.0),
)
