package com.f776.ecopan.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

class Navigator {
    lateinit var backStack: NavBackStack<NavKey>

    /**
     * The top-level destinations shown in the navigation suite, registered once by `AppNavigation`.
     *
     * [backStack] is a flat list, but it is read as one segment per tab: a segment starts at a
     * top-level key and runs until the next one. Keeping the segmentation derived rather than
     * stored means the per-tab histories survive process death for free, since the only thing that
     * has to be saved is the flat stack that `NavBackStack` already persists.
     */
    var topLevelKeys: List<NavKey> = emptyList()

    /** The tab currently in front, i.e. the owner of the segment the last entry belongs to. */
    val currentTopLevel: NavKey?
        get() = backStack.lastOrNull { it in topLevelKeys }

    /**
     * Which way the last navigation moved along the navigation suite: `1` towards a later tab,
     * `-1` towards an earlier one, `0` when it was not a tab switch at all.
     *
     * Switching tabs neither grows nor shrinks the back stack — it reorders it — so a `NavDisplay`
     * cannot tell from the stack alone whether the user moved left or right along the bar. Without
     * this, every tab switch animates as a forward push and tapping the tab on the left slides in
     * from the right.
     */
    var lateralDirection: Int by mutableStateOf(0)
        private set

    fun goTo(destination: NavKey) {
        lateralDirection = 0
        backStack.add(destination)
    }

    fun goBack() {
        lateralDirection = 0
        backStack.removeLastOrNull()
    }

    /**
     * Closes the open detail of type [T], if one is showing, leaving the list on its own.
     *
     * Does nothing when something else is in front, so a form opened from the detail is never
     * dismissed out from under the user by a change back in the list.
     */
    inline fun <reified T : NavKey> closeDetail() {
        if (backStack.lastOrNull() is T) {
            goBack()
        }
    }

    /**
     * Shows [destination] in a detail pane, replacing any detail of the same type already open.
     *
     * Picking a second item out of a list is a change of selection rather than a step forward, so
     * pushing would make back walk through every item the user glanced at. A tab root is never
     * replaced, which is what keeps the first selection from swallowing the list itself.
     */
    fun replaceDetail(destination: NavKey) {
        lateralDirection = 0
        val current = backStack.lastOrNull()
        if (current == destination) return
        if (current != null &&
            current !in topLevelKeys &&
            current::class == destination::class
        ) {
            backStack.removeLastOrNull()
        }
        backStack.add(destination)
    }

    /**
     * Selects a top-level destination, preserving whatever history that tab already had.
     *
     * Re-selecting the tab already in front pops it back to its root, which is the behaviour users
     * expect from a navigation bar. Otherwise the tab's whole segment is moved to the end of the
     * stack, so its entries — and the view models and saved state attached to them — stay alive.
     */
    fun switchTopLevel(destination: NavKey) {
        lateralDirection = directionTo(destination)

        val start = backStack.indexOfFirst { it == destination }
        if (start == -1) {
            backStack.add(destination)
            return
        }

        val segmentEnd = segmentEndAfter(start)
        if (currentTopLevel == destination) {
            // Already in front: drop everything pushed on top of the tab root.
            lateralDirection = 0
            repeat(segmentEnd - start - 1) { backStack.removeAt(start + 1) }
            return
        }

        val segment = backStack.subList(start, segmentEnd).toList()
        repeat(segment.size) { backStack.removeAt(start) }
        backStack.addAll(segment)
    }

    /**
     * Compares [destination] against the tab in front, using the order the tabs are shown in.
     *
     * Unknown tabs, or a switch made before any tab is showing, count as no lateral movement.
     */
    private fun directionTo(destination: NavKey): Int {
        val from = topLevelKeys.indexOf(currentTopLevel)
        val to = topLevelKeys.indexOf(destination)
        if (from == -1 || to == -1) return 0
        return to.compareTo(from)
    }

    /** Index just past the last entry belonging to the segment starting at [start]. */
    private fun segmentEndAfter(start: Int): Int {
        for (index in start + 1 until backStack.size) {
            if (backStack[index] in topLevelKeys) return index
        }
        return backStack.size
    }
}
