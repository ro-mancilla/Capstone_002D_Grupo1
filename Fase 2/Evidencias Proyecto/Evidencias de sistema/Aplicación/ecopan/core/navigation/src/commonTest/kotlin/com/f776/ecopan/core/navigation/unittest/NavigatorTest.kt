package com.f776.ecopan.core.navigation.unittest

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.f776.ecopan.core.navigation.Navigator
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data object Home : NavKey

@Serializable
private data object Deadlines : NavKey

@Serializable
private data class Detail(val id: Int) : NavKey

@Serializable
private data object Settings : NavKey

@Serializable
private data object Form : NavKey

class NavigatorTest {

    private fun navigatorOf(vararg keys: NavKey) = Navigator().apply {
        backStack = NavBackStack(*keys)
        topLevelKeys = listOf(Home, Deadlines, Settings)
    }

    @Test
    fun `switching to an unvisited tab pushes it`() {
        val navigator = navigatorOf(Home)

        navigator.switchTopLevel(Deadlines)

        assertEquals(listOf(Home, Deadlines), navigator.backStack.toList())
    }

    @Test
    fun `switching back to a tab keeps the history it had`() {
        val navigator = navigatorOf(Home)
        navigator.goTo(Detail(1))
        navigator.switchTopLevel(Deadlines)
        navigator.goTo(Detail(2))

        navigator.switchTopLevel(Home)

        // Both segments survive; only their order changes.
        assertEquals(
            listOf(Deadlines, Detail(2), Home, Detail(1)),
            navigator.backStack.toList()
        )
        assertEquals(Home, navigator.currentTopLevel)
    }

    @Test
    fun `re-selecting the current tab pops it back to its root`() {
        val navigator = navigatorOf(Home)
        navigator.goTo(Detail(1))
        navigator.goTo(Detail(2))

        navigator.switchTopLevel(Home)

        assertEquals(listOf(Home), navigator.backStack.toList())
    }

    @Test
    fun `a detail screen still reports its owning tab as current`() {
        val navigator = navigatorOf(Home)
        navigator.switchTopLevel(Deadlines)
        navigator.goTo(Detail(1))

        assertEquals(Deadlines, navigator.currentTopLevel)
    }

    @Test
    fun `moving to a later tab reports a forward direction`() {
        val navigator = navigatorOf(Home)

        navigator.switchTopLevel(Deadlines)

        assertEquals(1, navigator.lateralDirection)
    }

    @Test
    fun `moving to an earlier tab reports a backward direction`() {
        val navigator = navigatorOf(Home)
        navigator.switchTopLevel(Deadlines)

        navigator.switchTopLevel(Home)

        // Home sits left of Deadlines in the bar, so its content has to arrive from the left.
        assertEquals(-1, navigator.lateralDirection)
    }

    @Test
    fun `an ordinary push is not treated as a tab switch`() {
        val navigator = navigatorOf(Home)
        navigator.switchTopLevel(Deadlines)

        navigator.goTo(Detail(1))

        assertEquals(0, navigator.lateralDirection)
    }

    @Test
    fun `re-selecting the current tab is not a lateral move`() {
        val navigator = navigatorOf(Home)
        navigator.switchTopLevel(Deadlines)
        navigator.goTo(Detail(1))

        navigator.switchTopLevel(Deadlines)

        assertEquals(0, navigator.lateralDirection)
    }

    @Test
    fun `selecting another item swaps the detail instead of stacking it`() {
        val navigator = navigatorOf(Home)
        navigator.replaceDetail(Detail(1))

        navigator.replaceDetail(Detail(2))

        // One back press returns to the list, not through every item that was viewed.
        assertEquals(listOf(Home, Detail(2)), navigator.backStack.toList())
    }

    @Test
    fun `the first selection does not replace the list itself`() {
        val navigator = navigatorOf(Home)

        navigator.replaceDetail(Detail(1))

        assertEquals(listOf(Home, Detail(1)), navigator.backStack.toList())
    }

    @Test
    fun `re-selecting the open item is a no-op`() {
        val navigator = navigatorOf(Home)
        navigator.replaceDetail(Detail(1))

        navigator.replaceDetail(Detail(1))

        assertEquals(listOf(Home, Detail(1)), navigator.backStack.toList())
    }

    @Test
    fun `closing the detail leaves the list alone`() {
        val navigator = navigatorOf(Home)
        navigator.replaceDetail(Detail(1))

        navigator.closeDetail<Detail>()

        assertEquals(listOf(Home), navigator.backStack.toList())
    }

    @Test
    fun `closing the detail does nothing when another screen is in front`() {
        val navigator = navigatorOf(Home)
        navigator.replaceDetail(Detail(1))
        navigator.goTo(Form)

        navigator.closeDetail<Detail>()

        // A form opened from the detail must not be dismissed by a change back in the list.
        assertEquals(listOf(Home, Detail(1), Form), navigator.backStack.toList())
    }

    @Test
    fun `closing the detail is safe when none is open`() {
        val navigator = navigatorOf(Home)

        navigator.closeDetail<Detail>()

        assertEquals(listOf(Home), navigator.backStack.toList())
    }

    @Test
    fun `direction is measured across the whole bar not just adjacent tabs`() {
        val navigator = navigatorOf(Home)

        navigator.switchTopLevel(Settings)
        assertEquals(1, navigator.lateralDirection)

        navigator.switchTopLevel(Home)
        assertEquals(-1, navigator.lateralDirection)
    }

    @Test
    fun `each tab keeps its own history across three tabs`() {
        val navigator = navigatorOf(Home)
        navigator.switchTopLevel(Deadlines)
        navigator.goTo(Detail(1))
        navigator.switchTopLevel(Settings)

        navigator.switchTopLevel(Deadlines)

        assertEquals(Deadlines, navigator.currentTopLevel)
        assertEquals(Detail(1), navigator.backStack.last())
    }

    @Test
    fun `going back past a tab root falls through to the previous tab`() {
        val navigator = navigatorOf(Home)
        navigator.switchTopLevel(Deadlines)

        navigator.goBack()

        assertEquals(listOf(Home), navigator.backStack.toList())
        assertEquals(Home, navigator.currentTopLevel)
    }
}
