package com.lxmusic.android.ui.shell

import com.lxmusic.android.local.LocalGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The curtain stack replaced six independent booleans, so the interesting
 * cases are the ones the booleans could not express: parameterised pages,
 * duplicate opens, and closing a page that is not on top.
 */
class RouteStackTest {

    @Test
    fun openPushesAndReportsContainment() {
        val routes = RouteStack()
        assertFalse(routes.contains(Route.Settings))

        routes.open(Route.Settings)

        assertTrue(routes.contains(Route.Settings))
        assertEquals(listOf<Route>(Route.Settings), routes.stack.value)
    }

    @Test
    fun openingTheSameRouteTwiceDoesNotStackIt() {
        val routes = RouteStack()
        routes.open(Route.Queue)
        routes.open(Route.Queue)

        assertEquals(1, routes.stack.value.size)
    }

    @Test
    fun twoGroupPagesAreDistinctRoutes() {
        val routes = RouteStack()
        routes.open(Route.Group("album:A"))
        routes.open(Route.Group("album:B"))

        assertEquals(2, routes.stack.value.size)
        assertEquals("album:B", topGroupRoute(routes.stack.value)?.groupKey)
    }

    @Test
    fun reopeningTheSameGroupIsANoOp() {
        val routes = RouteStack()
        routes.open(Route.Group("album:A"))
        routes.open(Route.Group("album:A"))

        assertEquals(1, routes.stack.value.size)
    }

    @Test
    fun closeRemovesOutOfOrder() {
        val routes = RouteStack()
        routes.open(Route.Settings)
        routes.open(Route.Queue)
        routes.open(Route.Stats)

        routes.close(Route.Queue)

        assertEquals(listOf<Route>(Route.Settings, Route.Stats), routes.stack.value)
    }

    @Test
    fun closeTopFollowsTheDrawOrderNotTheOpenOrder() {
        val routes = RouteStack()
        // Opened bottom-up, then asked to close in draw order.
        routes.open(Route.Queue)
        routes.open(Route.Settings)

        routes.closeTop(listOf(Route.Settings, Route.Queue))

        assertFalse(routes.contains(Route.Settings))
        assertTrue(routes.contains(Route.Queue))
    }

    @Test
    fun closeTopOnAnEmptyStackDoesNothing() {
        val routes = RouteStack()
        routes.closeTop(listOf(Route.Settings, Route.Queue))
        assertTrue(routes.stack.value.isEmpty())
    }

    @Test
    fun groupRouteDerivesItsKindFromTheKeyPrefix() {
        assertEquals(LocalGroup.ALBUMS, Route.Group("album:Kind of Blue").kind)
        assertEquals(LocalGroup.ARTISTS, Route.Group("artist:Miles Davis").kind)
    }

    @Test
    fun groupRouteIdsAreStableAndPrefixed() {
        assertEquals("group:album:A", Route.Group("album:A").id)
        // Two routes with the same key must share an id - the shared curtain is
        // keyed by it, and a random id would restart the entry animation.
        assertEquals(Route.Group("album:A").id, Route.Group("album:A").id)
    }

    @Test
    fun noGroupOpenYieldsThePlaceholderKey() {
        val stack = listOf<Route>(Route.Settings)
        assertFalse(isGroupOpen(stack))
        assertNull(topGroupRoute(stack))
        assertEquals("group:none", routeGroupId(stack))
    }

    @Test
    fun clearingEmptiesEverything() {
        val routes = RouteStack()
        routes.open(Route.Settings)
        routes.open(Route.Group("artist:X"))

        routes.clear()

        assertTrue(routes.stack.value.isEmpty())
    }
}
