package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the phone's "Remove animations" (animator duration scale 0) is on. Motion is then
 * skipped: values jump to their end, and looping effects (shimmer, pulse, shine) are not drawn.
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Motion tokens. Every transition stays under 700 ms. */
object PwMotion {
    const val SHORT = 200
    const val MEDIUM = 400
    const val LONG = 650

    /** Sliding thumbs on segmented controls: a small overshoot, like the design's spring. */
    @Composable
    @ReadOnlyComposable
    fun <T> thumb(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    /** Bars, rings and counts growing into place. */
    @Composable
    @ReadOnlyComposable
    fun <T> grow(durationMs: Int = LONG, delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else tween(durationMs, delayMs, FastOutSlowInEasing)

    /** Colour and small state changes. */
    @Composable
    @ReadOnlyComposable
    fun <T> fade(durationMs: Int = SHORT): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else tween(durationMs)
}
