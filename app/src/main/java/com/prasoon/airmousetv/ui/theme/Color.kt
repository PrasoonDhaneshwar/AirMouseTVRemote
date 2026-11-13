package com.prasoon.airmousetv.ui.theme

import androidx.compose.ui.graphics.Color

// "Deep Code" palette from the Stitch design (project 11571088689893595022).
val Teal = Color(0xFF00C4CC)
val TealDim = Color(0xFF00767B)
val OnTeal = Color(0xFF00282A)

val Charcoal = Color(0xFF161A1F)
val SurfaceMid = Color(0xFF2C3038)
val SurfaceHigh = Color(0xFF363B44)
/** The D-pad disc and the touchpad: a step below the keys, so the keys on and around it read as raised. */
val PadSurface = Color(0xFF22272E)
/** Bottom sheets, and the code card in the pairing screen's TV drawing. */
val SheetSurface = Color(0xFF20252C)
/** The "⋮" menu: lifted just clear of the screen it opens over. */
val MenuSurface = Color(0xFF2A2F37)
/** Lines that only draw a shape (the TV drawing, a disabled button's edge), not the edge of a control. */
val Hairline = Color(0xFF3A4049)
/** Marks on a surface that are hints rather than controls: the touchpad's edge arrows, a sheet's handle. */
val Faint = Color(0xFF4A515B)
/** The screen inside the pairing screen's TV drawing. */
val ScreenBlack = Color(0xFF0E1013)
/** Borders of controls: 3.2:1 against Charcoal, above the 3:1 that accessibility guidance asks for. */
val Outline = Color(0xFF626A75)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFBFBFBF)

/** A TV that is reachable right now. Distinct from Teal, which marks selection and primary actions. */
val Online = Color(0xFF4ADE80)

/** The TV is not answering: a lost session. Distinct from Amber, which is for warnings. */
val Offline = Color(0xFFEF4444)

/** [Offline] lightened for small text: the pure red falls short of 4.5:1 on Charcoal. */
val OfflineText = Color(0xFFF26B6B)

val Amber = Color(0xFFE37E4A)
val OnAmber = Color(0xFF2B1204)
/** [Amber] lightened for small text on an amber-tinted card. */
val AmberText = Color(0xFFF0A27A)
