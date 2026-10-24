package com.kllxx.memorylane.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kllxx.memorylane.feature.achievements.api.AchievementsRoute
import com.kllxx.memorylane.feature.profile.api.ProfileRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigateToTabTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navController: NavHostController

    private fun setContent() {
        rule.setContent {
            navController = rememberNavController()
            AppNavShell(
                navController = navController,
                startDestination = TimelineRoute,
                showBottomBar = false,
                bottomBar = {},
            ) {
                composable<TimelineRoute> { Box(androidx.compose.ui.Modifier.testTag("timeline")) }
                composable<AchievementsRoute> { Box(androidx.compose.ui.Modifier.testTag("achievements")) }
                composable<ProfileRoute> { Box(androidx.compose.ui.Modifier.testTag("profile")) }
            }
        }
        rule.waitForIdle()
    }

    private fun navigateToTab(route: Any) {
        rule.runOnUiThread { navController.navigateToTab(route) }
        rule.waitForIdle()
    }

    @Test
    fun `going to the timeline from achievements shows the timeline tab`() {
        setContent()
        navigateToTab(AchievementsRoute)
        rule.onNodeWithTag("achievements").assertExists()

        navigateToTab(TimelineRoute)

        rule.onNodeWithTag("timeline").assertExists()
        assertTrue(navController.currentDestination?.hasRoute<TimelineRoute>() == true)
    }

    @Test
    fun `switching tabs never stacks a second timeline`() {
        setContent()

        navigateToTab(AchievementsRoute)
        navigateToTab(TimelineRoute)
        navigateToTab(AchievementsRoute)
        navigateToTab(TimelineRoute)

        val timelines = navController.currentBackStack.value.count { it.destination.hasRoute<TimelineRoute>() }
        assertEquals(1, timelines)
    }

    @Test
    fun `tabs can be reached from each other`() {
        setContent()

        navigateToTab(ProfileRoute)
        rule.onNodeWithTag("profile").assertExists()
        navigateToTab(AchievementsRoute)
        rule.onNodeWithTag("achievements").assertExists()
    }
}
