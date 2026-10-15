package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.R

// Plus Jakarta Sans, bundled as static weights (a single variable font file cannot change weight
// on Android 7, the app's minimum). It includes the ₹ sign and tabular figures.
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold)
)

// "tnum": every digit has the same width, so amounts and dates line up in lists.
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum"
)

/** One type scale for the whole app (v4: 24 page · 15 section · 14 row · 12 meta). */
val Typography = Typography(
    displayLarge = style(44, 52, FontWeight.ExtraBold, -0.02), // amount being entered
    displayMedium = style(34, 40, FontWeight.ExtraBold, -0.02), // hero amounts
    displaySmall = style(30, 36, FontWeight.ExtraBold, -0.02),
    headlineLarge = style(24, 32, FontWeight.ExtraBold, -0.02), // page titles
    headlineMedium = style(22, 28, FontWeight.ExtraBold, -0.01), // key numbers in cards
    headlineSmall = style(20, 26, FontWeight.ExtraBold, -0.01),
    titleLarge = style(18, 24, FontWeight.Bold),
    titleMedium = style(15, 20, FontWeight.Bold),              // section and card titles
    titleSmall = style(14, 20, FontWeight.SemiBold),           // list row titles
    bodyLarge = style(15, 22, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),              // row subtitles, meta
    labelLarge = style(13, 18, FontWeight.Bold),               // buttons, chips, links
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.01)
)
