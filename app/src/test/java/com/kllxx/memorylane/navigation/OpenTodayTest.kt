package com.kllxx.memorylane.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kllxx.memorylane.feature.achievements.api.AchievementsRoute
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenTodayTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 15)
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
                composable<AchievementsRoute> { Box(Modifier) }
                composable<SwipeRoute> { Box(Modifier) }
            }
        }
        rule.waitForIdle()
    }

    private fun openToday() {
        rule.runOnUiThread { navController.openToday(today) }
        rule.waitForIdle()
    }

    private fun backStackRoutes(): List<String> = navController.currentBackStack.value
        .mapNotNull { entry ->
            when {
                entry.destination.hasRoute<CalendarRoute>() -> "calendar"
                entry.destination.hasRoute<SwipeRoute>() -> "swipe"
                entry.destination.hasRoute<AchievementsRoute>() -> "achievements"
                else -> null
            }
        }

    @Test
    fun `the reminder opens the deck of today above calendar`() {
        setContent()
        openToday()

        assertTrue(navController.currentDestination?.hasRoute<SwipeRoute>() == true)
        assertEquals(listOf("calendar", "swipe"), backStackRoutes())
    }

    @Test
    fun `a second tap does not stack a second deck`() {
        setContent()
        openToday()
        openToday()

        assertEquals(listOf("calendar", "swipe"), backStackRoutes())
    }

    @Test
    fun `back from the deck goes to calendar`() {
        setContent()
        openToday()

        rule.runOnUiThread { navController.popBackStack() }
        rule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<CalendarRoute>() == true)
        assertEquals(listOf("calendar"), backStackRoutes())
    }

    @Test
    fun `from another tab the deck still sits right above calendar`() {
        setContent()
        rule.runOnUiThread { navController.navigateToTab(AchievementsRoute) }
        rule.waitForIdle()

        openToday()

        assertEquals(listOf("calendar", "swipe"), backStackRoutes())
    }
}
