package com.f776.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ecopan motion: 80 / 140 / 200 / 320 ms on `cubic-bezier(.2, 0, .2, 1)`. When the user asked the
 * platform for reduced motion every duration collapses to 0 -- the specs become `snap()` and the
 * `*Millis` values are 0 -- so a control that snaps still responds. [reduced] also tells decorative
 * infinite animations to stay still. Read as `EcopanTheme.motion`.
 */
@Immutable
class EcopanMotion(val reduced: Boolean) {

    /** Press feedback. */
    val instantMillis: Int get() = if (reduced) 0 else DurationInstant

    /** The one transition every control uses: fill, border, content colour. */
    val fastMillis: Int get() = if (reduced) 0 else DurationFast

    /** Small layout changes, such as a tab indicator. */
    val baseMillis: Int get() = if (reduced) 0 else DurationBase

    /** Things that enter or leave: dialogs, toasts. */
    val slowMillis: Int get() = if (reduced) 0 else DurationSlow

    fun <T> instant(): FiniteAnimationSpec<T> = spec(DurationInstant, Standard)
    fun <T> fast(): FiniteAnimationSpec<T> = spec(DurationFast, Standard)
    fun <T> base(): FiniteAnimationSpec<T> = spec(DurationBase, Standard)
    fun <T> slow(): FiniteAnimationSpec<T> = spec(DurationSlow, Standard)

    /** Decelerating, for things that enter. */
    fun <T> enter(durationMillis: Int = DurationSlow): FiniteAnimationSpec<T> = spec(durationMillis, Out)

    /** Accelerating, for things that leave. */
    fun <T> exit(durationMillis: Int = DurationBase): FiniteAnimationSpec<T> = spec(durationMillis, In)

    private fun <T> spec(durationMillis: Int, easing: CubicBezierEasing): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = easing)

    companion object {
        const val DurationInstant = 80
        const val DurationFast = 140
        const val DurationBase = 200
        const val DurationSlow = 320

        val Standard = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)
        val Out = CubicBezierEasing(0f, 0f, 0.22f, 1f)
        val In = CubicBezierEasing(0.5f, 0f, 1f, 1f)
    }
}

/** True when the platform asks for reduced motion (Android animator scale 0, iOS Reduce Motion). */
@Composable
internal expect fun rememberReducedMotion(): Boolean

internal val LocalEcopanMotion = staticCompositionLocalOf { EcopanMotion(reduced = false) }
