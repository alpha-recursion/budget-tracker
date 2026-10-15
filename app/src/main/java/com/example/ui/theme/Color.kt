package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pennywise design tokens (redesign v4): light grey canvas, white cards, navy for dark surfaces,
// one purple accent, green kept for income and good states. Text colours are at least 4.5:1 on
// white (WCAG AA); bright fills are only used as fills.

// Ink (text) and navy (dark surfaces: hero card, nav bar, selected chips)
val Ink = Color(0xFF0E1A2B)            // primary text (17:1)
val InkSecondary = Color(0xFF5E6A73)   // secondary text (5.6:1)
val InkTertiary = Color(0xFF98A2AA)    // hints/placeholders only, not body text
val Navy = Color(0xFF0B1A2E)           // hero, nav bar, selected chips, tooltips

// Surfaces
val Canvas = Color(0xFFF4F5F7)         // app background
val SurfaceWhite = Color(0xFFFFFFFF)   // cards
val SurfaceMuted = Color(0xFFF4F5F7)   // fields and quiet fills inside white cards
val Track = Color(0xFFE9EBEE)          // segmented control track
val TrackSoft = Color(0xFFEEF0F3)      // progress bar tracks, in-card dividers
val Hairline = Color(0xFFE6E8EB)       // chip borders and quiet dividers
val Outline = Color(0xFFD5D9DE)        // secondary button border, switch off track

// Action (one accent)
val Accent = Color(0xFF6D28D9)         // buttons, links, selected icons (7.1:1)
val AccentBright = Color(0xFF7C3AED)   // top of the button gradient, hero glow
val AccentLight = Color(0xFF8B5CF6)    // top of the add-button gradient
val AccentSoft = Color(0xFFF1EAFE)     // tinted backgrounds for accent elements
val AccentTrack = Color(0xFFECE6FA)    // unselected chart bars
val AccentOnDark = Color(0xFFC4B5FD)   // links and actions on navy

// Financial states
val Positive = Color(0xFF34B864)       // fills: progress, income bars (not for text on white)
val PositiveText = Color(0xFF1F7A3D)   // income amounts and good-state text (5.6:1)
val PositiveSoft = Color(0xFFE7F5EC)
val Negative = Color(0xFFD92D33)       // overspend and errors (4.8:1)
val NegativeSoft = Color(0xFFFCE9EA)
val Warning = Color(0xFFF5A524)        // near-limit fills
val WarningText = Color(0xFFA35F00)    // near-limit text (5.0:1)
val WarningSoft = Color(0xFFFFF4E0)
