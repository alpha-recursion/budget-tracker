package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Material roles mapped onto the Pennywise tokens, so default components (dialogs, menus,
// date picker, switches) match the custom ones.
private val PennywiseColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = SurfaceWhite,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    secondary = Ink,
    onSecondary = SurfaceWhite,
    secondaryContainer = SurfaceMuted,
    onSecondaryContainer = Ink,
    tertiary = PositiveText,
    onTertiary = SurfaceWhite,
    tertiaryContainer = PositiveSoft,
    onTertiaryContainer = PositiveText,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkSecondary,
    surfaceTint = SurfaceWhite, // no purple-ish tonal tint on elevated surfaces
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = SurfaceWhite,
    surfaceContainer = SurfaceWhite,
    surfaceContainerHigh = SurfaceWhite,
    surfaceContainerHighest = SurfaceMuted,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Negative,
    onError = SurfaceWhite,
    errorContainer = NegativeSoft,
    onErrorContainer = Negative
)

/** Corner radii: chips/fields 12-16, cards 24, sheets/dialogs 28. */
val PennywiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PennywiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PennywiseColorScheme,
        typography = Typography,
        shapes = PennywiseShapes,
        content = content
    )
}
