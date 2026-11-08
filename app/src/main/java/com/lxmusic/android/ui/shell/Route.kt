package com.lxmusic.android.ui.shell

import com.lxmusic.android.local.LocalGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A full-screen page that can be raised over the stage.
 *
 * These used to be five booleans spread across the ViewModel and `LxApp`, which
 * worked while every page was argless. [Group] is the first one that is not -
 * "open the album whose key is X" cannot be expressed by a boolean - so the
 * whole set becomes a typed stack instead of growing a sixth flag.
 */
sealed interface Route {
    /** Stable identity, used as the `key(...)` of the composed curtain. */
    val id: String

    data object Settings : Route {
        override val id: String get() = "settings"
    }

    data object Folders : Route {
        override val id: String get() = "folders"
    }

    data object Stats : Route {
        override val id: String get() = "stats"
    }

    data object Queue : Route {
        override val id: String get() = "queue"
    }

    data object Equalizer : Route {
        override val id: String get() = "equalizer"
    }

    data object NowPlaying : Route {
        override val id: String get() = "nowPlaying"
    }

    /**
     * An album or an artist page.
     *
     * Only the key travels with the route, never the [com.lxmusic.android.local.MusicGroup]
     * itself: the grouping is rebuilt on every search keystroke, so a captured
     * object would go stale the moment the user types in the search box.
     */
    data class Group(val groupKey: String) : Route {
        override val id: String get() = "group:$groupKey"

        /**
         * Which grouping produced [groupKey].
         *
         * Read off the prefix that [com.lxmusic.android.local.LocalLibrary] writes
         * (`album:` / `artist:`), so a route stays meaningful without asking the
         * screen which chip happens to be selected.
         */
        val kind: LocalGroup
            get() = if (groupKey.startsWith("artist:")) LocalGroup.ARTISTS else LocalGroup.ALBUMS
    }
}

/**
 * The curtain stack.
 *
 * Typed as a stack, but rendered as a fixed set: [LxShell] draws every registered
 * curtain in its declared order and only asks this class whether a given route is
 * open. That keeps the visual layering stable no matter what the user opened
 * first - settings always sits above the queue, as it did back when each page was
 * its own boolean.
 */
class RouteStack {

    private val _stack = MutableStateFlow<List<Route>>(emptyList())

    /** Open routes, oldest first. The last element is the topmost. */
    val stack: StateFlow<List<Route>> = _stack.asStateFlow()

    fun contains(route: Route): Boolean = _stack.value.any { it.id == route.id }

    /** True when any route of [kind] is open. Used by tests and by `LxApp`. */
    fun isOpen(predicate: (Route) -> Boolean): Boolean = _stack.value.any(predicate)

    /** Pushes [route] unless the identical route is already on the stack. */
    fun open(route: Route) {
        if (contains(route)) return
        _stack.value = _stack.value + route
    }

    /** Removes [route] wherever it sits, so a page can be closed out of order. */
    fun close(route: Route) {
        if (!contains(route)) return
        _stack.value = _stack.value.filterNot { it.id == route.id }
    }

    /**
     * Closes the topmost open route among [order] - the declared draw order.
     *
     * `LxShell` passes its own curtain list, so "back" always dismisses the page
     * the user can actually see rather than last-opened-bookkeeping.
     */
    fun closeTop(order: List<Route>) {
        order.firstOrNull { contains(it) }?.let(::close)
    }

    fun clear() {
        if (_stack.value.isEmpty()) return
        _stack.value = emptyList()
    }
}

/** The topmost open group route, or null. */
fun topGroupRoute(stack: List<Route>): Route.Group? =
    stack.lastOrNull { it is Route.Group } as? Route.Group

/**
 * Whether any album / artist page is open.
 *
 * There is one curtain for the whole kind rather than one per route: only one
 * group page is ever on screen, and rebuilding a curtain per key would restart
 * the entry animation every time the user walked from an album to its artist.
 */
fun isGroupOpen(stack: List<Route>): Boolean = stack.any { it is Route.Group }

/** The key the shared group curtain should compose, or a stable placeholder. */
fun routeGroupId(stack: List<Route>): String =
    topGroupRoute(stack)?.let { "group:${it.id}" } ?: "group:none"
