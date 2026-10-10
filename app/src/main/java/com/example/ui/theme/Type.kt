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

/** One type scale for the whole app; screens use these styles instead of ad-hoc sizes. */
val Typography = Typography(
    displayLarge = style(40, 48, FontWeight.Bold, -0.02),     // hero amounts
    displayMedium = style(34, 42, FontWeight.Bold, -0.02),
    displaySmall = style(30, 38, FontWeight.Bold, -0.015),
    headlineLarge = style(28, 36, FontWeight.Bold, -0.01),    // page titles
    headlineMedium = style(24, 32, FontWeight.Bold, -0.01),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.005),
    titleLarge = style(20, 28, FontWeight.Bold),
    titleMedium = style(17, 24, FontWeight.SemiBold),         // card titles
    titleSmall = style(15, 20, FontWeight.SemiBold),          // list item titles
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),          // buttons
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium, 0.01)
)
