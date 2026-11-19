@file:OptIn(KoinExperimentalAPI::class, ExperimentalMaterial3AdaptiveApi::class)

package com.f776.ecopan.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.f776.core.designsystem.theme.EcopanTheme
import com.f776.core.ui.appstate.LocalSnackbarAppState
import com.f776.ecopan.core.navigation.Navigator
import com.f776.ecopan.core.navigation.rememberAdaptiveNavTransitions
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.window.core.layout.WindowSizeClass
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

/**
 * Destinations that own a navigation-suite tab, and therefore a back stack segment of their own.
 *
 * Per platform, because the till and the baker's phone are different clients — see
 * [TopLevelDestinations].
 */
private val TopLevelKeys: List<NavKey> = TopLevelDestinations.map { it.key }

/** Where a client lands when nothing else has been asked for. */
internal val DefaultRoute: NavKey = TopLevelDestinations.first().key

@Composable
internal fun AppNavigation(
    modifier: Modifier = Modifier,
    initialRoute: NavKey
) {
    val startKeys = remember(initialRoute) {
        // A top-level route *is* the stack; anything else is a detail, pushed on top of the
        // platform's default tab so that backing out of it lands somewhere that exists.
        if (initialRoute in TopLevelKeys) arrayOf(initialRoute) else arrayOf(DefaultRoute, initialRoute)
    }

    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    platformRoutes()
                }
            }
        },
        *startKeys
    )

    val navigator = koinInject<Navigator>().apply {
        this.backStack = backStack
        this.topLevelKeys = TopLevelKeys
    }

    val entryProvider = koinEntryProvider<NavKey>()
    val transitions = rememberAdaptiveNavTransitions()
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    val currentKey = navigator.backStack.lastOrNull()
    // Tab selection follows the segment the user is in, so a detail screen keeps its tab lit.
    val currentTopLevel = navigator.currentTopLevel
    // The suite only belongs on the top-level destinations.
    val isCompactWidth = !currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    // The default also picks the bottom bar whenever the *height* is compact, which is every phone
    // in landscape: the bar then took a fifth of a ~400dp-tall window. Width alone decides here, so
    // a landscape phone gets the rail and keeps its height for content.
    val layoutType = if (isCompactWidth) {
        NavigationSuiteType.NavigationBar
    } else {
        NavigationSuiteType.NavigationRail
    }
    // The rail is a fixed ~80dp column; the bottom bar divides the whole screen between two or
    // three items. Only the rail needs its labels capped, and capping both would ellipsise
    // "Configuración" on a phone that has room for it twice over.
    val narrowNavLabels = layoutType != NavigationSuiteType.NavigationBar
    // On a phone a pushed screen covers the window, so the bar goes with it. From medium up the
    // rail keeps its own column and the screen only fills what is left, so removing it would
    // resize the content underneath — a visible shift every time a detail or form opens.
    val suiteVisible = currentKey in TopLevelKeys || !isCompactWidth
    // Hiding through the state animates the bar out; swapping layoutType to None would drop it
    // between frames and snap the screen underneath taller, which is the shift itself.
    val suiteState = rememberNavigationSuiteScaffoldState(
        initialValue = if (suiteVisible) {
            NavigationSuiteScaffoldValue.Visible
        } else {
            NavigationSuiteScaffoldValue.Hidden
        }
    )
    LaunchedEffect(suiteVisible) {
        if (suiteVisible) suiteState.show() else suiteState.hide()
    }

    // The design's tab bar (Panadero móvil 2a): the active tab is its icon on a soft peach pill with
    // a crust label, the rest are muted ink, all on the card ground. Tokens, not Material roles --
    // the Material roles resolve the selected label lighter than the unselected one in this scheme.
    val ecopanColors = EcopanTheme.colors
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = ecopanColors.crust,
        selectedTextColor = ecopanColors.crust,
        indicatorColor = ecopanColors.peachSoft,
        unselectedIconColor = ecopanColors.inkMuted,
        unselectedTextColor = ecopanColors.inkMuted,
    )
    val railItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = ecopanColors.crust,
        selectedTextColor = ecopanColors.crust,
        indicatorColor = ecopanColors.peachSoft,
        unselectedIconColor = ecopanColors.inkMuted,
        unselectedTextColor = ecopanColors.inkMuted,
    )
    val navigationItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = itemColors,
        navigationRailItemColors = railItemColors,
    )

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        state = suiteState,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = ecopanColors.card,
            navigationRailContainerColor = ecopanColors.card,
        ),
        navigationSuiteItems = {
            TopLevelDestinations.forEach { destination ->
                item(
                    selected = currentTopLevel == destination.key,
                    onClick = { navigator.switchTopLevel(destination.key) },
                    // The default item colours resolve the *selected* label and icon to a lighter
                    // value than the unselected ones in this scheme, so the active tab read as the
                    // dimmed one — the selection was inverted on both the rail and the bottom bar.
                    // Set from the design's own tokens instead: brand ink on a brand-soft
                    // indicator for active, muted for the rest.
                    colors = navigationItemColors,
                    icon = { Icon(destination.icon, contentDescription = null) },
                    // "Configuración" is wider than the 80dp rail at the default label size, and
                    // neither the rail nor `maxLines` bounds it -- the word drew off the left edge
                    // of the till's window. `maxLines`/`overflow` alone do nothing here because
                    // the item measures its label with an unbounded width, so the cap has to be an
                    // explicit `widthIn`, and only in the rail (see [narrowNavLabels]).
                    label = {
                        Text(
                            stringResource(destination.label),
                            style = EcopanTheme.typography.eyebrow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = if (narrowNavLabels) Modifier.widthIn(max = 72.dp) else Modifier,
                        )
                    }
                )
            }
        }
    ) {
        // The suite's content slot already consumes the navigation-bar inset when the bar is
        // showing, so navigationBarsPadding() here resolves to zero beside the bar and to the
        // real inset on the full-screen destinations where the suite is hidden. Either way the
        // snackbar clears whatever is at the bottom.
        Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = navigator::goBack,
            entryDecorators = listOf(
                // Add the default decorators for managing scenes and saving state
                rememberSaveableStateHolderNavEntryDecorator(),
                // Then add the view model store decorator
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider,
            sceneStrategies = listOf(listDetailStrategy),
            // A tab switch reorders the stack, so it can surface as either a push or a pop
            // depending on where the target tab already sat. Both paths defer to the tab
            // direction when there is one.
            transitionSpec = {
                transitions.forLateral(navigator.lateralDirection, transitions.enter)
            },
            popTransitionSpec = {
                transitions.forLateral(navigator.lateralDirection, transitions.pop)
            },
            predictivePopTransitionSpec = { transitions.predictivePop }
        )

            SnackbarHost(
                hostState = LocalSnackbarAppState.current.snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}
