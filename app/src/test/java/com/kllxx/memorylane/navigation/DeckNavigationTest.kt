package com.kllxx.memorylane.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.feature.calendar.api.CalendarRoute
import com.kllxx.memorylane.feature.swipe.api.BeforeDeleteRoute
import com.kllxx.memorylane.feature.swipe.api.DeckKind
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import com.kllxx.memorylane.feature.swipe.impl.toBeforeDeleteRoute
import com.kllxx.memorylane.feature.swipe.impl.toResultRoute
import com.kllxx.memorylane.feature.swipe.impl.toSwipeRoute
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The deck travels in the routes as a real value: a kind, and a date only for a day. */
@RunWith(RobolectricTestRunner::class)
class DeckNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate.of(2026, 10, 15)
    private lateinit var navController: NavHostController

    private fun setContent() {
        rule.setContent {
            navController = rememberNavController()
            AppNavShell(
                navController = navController,
                startDestination = CalendarRoute,
                showBottomBar = false,
                bottomBar = {},
            ) {
                composable<CalendarRoute> { Box(Modifier) }
                composable<SwipeRoute> { Box(Modifier) }
                composable<BeforeDeleteRoute> { Box(Modifier) }
                composable<ResultRoute> { Box(Modifier) }
            }
        }
        rule.waitForIdle()
    }

    private fun navigate(route: Any) {
        rule.runOnUiThread { navController.navigate(route) }
        rule.waitForIdle()
    }

    @Test
    fun `calendar opens the undated deck, a route with the kind UNDATED and no date`() {
        setContent()

        navigate(SwipeRoute.undated())

        val entry = navController.currentBackStackEntry!!
        assertTrue(entry.destination.hasRoute<SwipeRoute>())
        val route = entry.toRoute<SwipeRoute>()
        assertEquals(DeckKind.UNDATED, route.deck)
        assertNull(route.date)
    }

    @Test
    fun `calendar opens a day, a route with the kind DAY and its date`() {
        setContent()

        navigate(SwipeRoute.day(day))

        val route = navController.currentBackStackEntry!!.toRoute<SwipeRoute>()
        assertEquals(DeckKind.DAY, route.deck)
        assertEquals("2026-10-15", route.date)
    }

    @Test
    fun `the undated deck leads to before-delete and result as the undated deck`() {
        setContent()
        navigate(SwipeRoute.undated())

        navigate(DeckKey.Undated.toBeforeDeleteRoute())
        val beforeDelete = navController.currentBackStackEntry!!.toRoute<BeforeDeleteRoute>()
        assertEquals(DeckKind.UNDATED, beforeDelete.deck)
        assertNull(beforeDelete.date)

        navigate(DeckKey.Undated.toResultRoute(deletedCount = 2, freedBytes = 5, keptCount = 1, totalCount = 3))
        val result = navController.currentBackStackEntry!!.toRoute<ResultRoute>()
        assertEquals(DeckKind.UNDATED, result.deck)
        assertNull(result.date)
        assertEquals(ResultRoute.undated(2, 5, 1, 3), result)
    }

    @Test
    fun `a day leads to before-delete and result with its date`() {
        setContent()
        navigate(DeckKey.Day(day).toSwipeRoute())

        navigate(DeckKey.Day(day).toBeforeDeleteRoute())
        assertEquals(BeforeDeleteRoute.day(day), navController.currentBackStackEntry!!.toRoute<BeforeDeleteRoute>())

        navigate(DeckKey.Day(day).toResultRoute(2, 5, 1, 3))
        assertEquals(ResultRoute.day(day, 2, 5, 1, 3), navController.currentBackStackEntry!!.toRoute<ResultRoute>())
    }

    @Test
    fun `back from the undated deck goes to calendar`() {
        setContent()
        navigate(SwipeRoute.undated())

        rule.runOnUiThread { navController.popBackStack() }
        rule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<CalendarRoute>() == true)
    }

    @Test
    fun `the undated deck and a day are two destinations on the back stack, not one`() {
        setContent()
        navigate(SwipeRoute.undated())
        navigate(SwipeRoute.day(day))

        val decks = navController.currentBackStack.value
            .filter { it.destination.hasRoute<SwipeRoute>() }
            .map { it.toRoute<SwipeRoute>().deck }

        assertEquals(listOf(DeckKind.UNDATED, DeckKind.DAY), decks)
    }
}
