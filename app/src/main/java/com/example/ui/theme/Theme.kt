package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PennywisePrimaryContainer,
    onPrimary = PennywiseOnPrimaryContainer,
    primaryContainer = PennywisePrimary,
    onPrimaryContainer = PennywisePrimaryContainer,
    secondary = PennywiseSecondaryContainer,
    onSecondary = PennywiseOnSecondaryContainer,
    secondaryContainer = PennywiseSecondary,
    onSecondaryContainer = PennywiseSecondaryContainer,
    tertiary = PennywiseTertiaryContainer,
    onTertiary = PennywiseOnTertiaryContainer,
    background = PennywiseBackgroundDark,
    surface = PennywiseSurfaceDark,
    surfaceVariant = PennywiseSurfaceVariantDark,
    onBackground = PennywiseOnSurfaceDark,
    onSurface = PennywiseOnSurfaceDark,
    onSurfaceVariant = PennywiseOnSurfaceVariantDark,
    outline = PennywiseOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = PennywisePrimary,
    onPrimary = PennywiseOnPrimary,
    primaryContainer = PennywisePrimaryContainer,
    onPrimaryContainer = PennywiseOnPrimaryContainer,
    secondary = PennywiseSecondary,
    onSecondary = PennywiseOnSecondary,
    secondaryContainer = PennywiseSecondaryContainer,
    onSecondaryContainer = PennywiseOnSecondaryContainer,
    tertiary = PennywiseTertiary,
    onTertiary = PennywiseOnTertiary,
    background = PennywiseBackgroundLight,
    surface = PennywiseSurfaceLight,
    surfaceVariant = PennywiseSurfaceVariantLight,
    onBackground = PennywiseOnSurfaceLight,
    onSurface = PennywiseOnSurfaceLight,
    onSurfaceVariant = PennywiseOnSurfaceVariantLight,
    outline = PennywiseOutlineLight
)

@Composable
fun PennywiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep intentional brand identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
