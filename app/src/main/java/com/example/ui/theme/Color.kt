package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pennywise design tokens. Palette from the reference designs: light grey canvas, white cards,
// near-black ink, one blue for actions, green for good/income. Text colours were chosen for
// at least 4.5:1 contrast on white (WCAG AA); bright fills are only used as fills.

// Ink (text and dark surfaces)
val Ink = Color(0xFF021720)            // primary text, selected chips, hero card (18.3:1)
val InkSecondary = Color(0xFF5E6A73)   // secondary text (5.6:1)
val InkTertiary = Color(0xFF98A2AA)    // hints/placeholders only, not body text

// Surfaces
val Canvas = Color(0xFFF2F3F5)         // app background
val SurfaceWhite = Color(0xFFFFFFFF)   // cards
val SurfaceMuted = Color(0xFFF4F5F7)   // fields, unselected chips, tracks
val Hairline = Color(0xFFE6E8EB)       // dividers and quiet borders

// Action
val Accent = Color(0xFF0B51D9)         // buttons, links, selected icons (6.6:1)
val AccentSoft = Color(0xFFE8EFFC)     // tinted backgrounds for accent elements

// Financial states
val Positive = Color(0xFF51D200)       // fills: progress, chart, income dots (not for text on white)
val PositiveText = Color(0xFF2B7F00)   // income amounts and good-state text (5.1:1)
val PositiveSoft = Color(0xFFE9F8DD)
val Negative = Color(0xFFD92D33)       // overspend and errors (4.8:1)
val NegativeSoft = Color(0xFFFCE9EA)
val Warning = Color(0xFFF5A524)        // near-limit fills
val WarningText = Color(0xFFA35F00)    // near-limit text (5.0:1)
