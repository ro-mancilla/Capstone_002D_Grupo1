package com.f776.ecopan.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass

val SLIDE_HORIZONTALLY_ENTER = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(400)
) togetherWith slideOutHorizontally(
    targetOffsetX = { -it },
    animationSpec = tween(400)
)

val SLIDE_HORIZONTALLY_POP = slideInHorizontally(
    initialOffsetX = { -it },
    animationSpec = tween(400)
) togetherWith slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = tween(400)
)

val SLIDE_HORIZONTALLY_PREDICTIVE_POP = slideInHorizontally(
    initialOffsetX = { -it },
    animationSpec = tween(400)
) togetherWith slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = tween(400)
)

/**
 * Fade-through, used instead of the horizontal slide once the navigation area moves to the side.
 *
 * A rail or drawer anchors the left edge of the window, so content sliding across it reads as the
 * whole app moving rather than one pane changing. The outgoing content clears before the incoming
 * content starts, which is what keeps the two from ghosting over each other.
 */
val FADE_THROUGH_ENTER = fadeIn(
    animationSpec = tween(durationMillis = 210, delayMillis = 90)
) togetherWith fadeOut(
    animationSpec = tween(durationMillis = 90)
)

val FADE_THROUGH_POP = FADE_THROUGH_ENTER

val SLIDE_HORIZONTALLY = NavDisplay.transitionSpec {
    SLIDE_HORIZONTALLY_ENTER
} + NavDisplay.popTransitionSpec {
    SLIDE_HORIZONTALLY_POP
} + NavDisplay.predictivePopTransitionSpec {
    SLIDE_HORIZONTALLY_PREDICTIVE_POP
}

val SLIDE_VERTICALLY = NavDisplay.transitionSpec {
    // Slide new content up, keeping the old content in place underneath
    slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(400)
    ) togetherWith ExitTransition.KeepUntilTransitionsFinished
} + NavDisplay.popTransitionSpec {
    // Slide old content down, revealing the new content in place underneath
    EnterTransition.None togetherWith
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(400)
            )
} + NavDisplay.predictivePopTransitionSpec {
    // Slide old content down, revealing the new content in place underneath
    EnterTransition.None togetherWith
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(400)
            )
}

val BOUNCE_APPEAR = NavDisplay.transitionSpec {
    // Ball bounces in from top (balanced bounce)
    slideInVertically(
        initialOffsetY = { -it },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    ) + fadeIn(
        animationSpec = tween(400)
    ) + scaleIn(
        initialScale = 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    ) togetherWith ExitTransition.KeepUntilTransitionsFinished
} + NavDisplay.popTransitionSpec {
    // Ball bounces out downward (balanced bounce)
    EnterTransition.None togetherWith (
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(
                animationSpec = tween(400)
            ) + scaleOut(
                targetScale = 0.7f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            )
} + NavDisplay.predictivePopTransitionSpec {
    EnterTransition.None togetherWith (
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 300,
                    easing = LinearEasing
                )
            ) + scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
            )
}
/**
 * The transition set a [NavDisplay] should use, as chosen by [rememberAdaptiveNavTransitions].
 */
data class AdaptiveNavTransitions(
    val enter: ContentTransform,
    val pop: ContentTransform,
    val predictivePop: ContentTransform,
    /** Moving to a later tab in the navigation suite. */
    val lateralForward: ContentTransform,
    /** Moving to an earlier tab in the navigation suite. */
    val lateralBackward: ContentTransform
) {
    /**
     * The transition for a stack change, given the tab movement that caused it.
     *
     * [lateralDirection] comes from `Navigator.lateralDirection`; `0` means this was an ordinary
     * push or pop rather than a tab switch, so [fallback] applies.
     */
    fun forLateral(lateralDirection: Int, fallback: ContentTransform): ContentTransform = when {
        lateralDirection > 0 -> lateralForward
        lateralDirection < 0 -> lateralBackward
        else -> fallback
    }
}

/**
 * Picks navigation transitions from the window width so every [NavDisplay] in the app agrees.
 *
 * Compact windows slide horizontally, matching the left-to-right reading of a phone back stack.
 * From medium up the navigation area sits along the edge as a rail or drawer, so content
 * fade-throughs in place instead of sliding past it.
 *
 * Per-entry `metadata` still wins over this, which is how modal-style destinations keep their
 * vertical slide at every size.
 */
@Composable
fun rememberAdaptiveNavTransitions(): AdaptiveNavTransitions {
    val isCompactWidth = !currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    return remember(isCompactWidth) {
        if (isCompactWidth) {
            AdaptiveNavTransitions(
                enter = SLIDE_HORIZONTALLY_ENTER,
                pop = SLIDE_HORIZONTALLY_POP,
                predictivePop = SLIDE_HORIZONTALLY_PREDICTIVE_POP,
                // Tapping a tab to the right brings content in from the right, and vice versa, so
                // the motion matches where the tab sits in the bar.
                lateralForward = SLIDE_HORIZONTALLY_ENTER,
                lateralBackward = SLIDE_HORIZONTALLY_POP
            )
        } else {
            AdaptiveNavTransitions(
                enter = FADE_THROUGH_ENTER,
                pop = FADE_THROUGH_POP,
                // Predictive back is driven by a drag; a fade has no direction to follow it with,
                // so it simply plays out as the same fade.
                predictivePop = FADE_THROUGH_POP,
                // A fade has no direction to get wrong.
                lateralForward = FADE_THROUGH_ENTER,
                lateralBackward = FADE_THROUGH_ENTER
            )
        }
    }
}
