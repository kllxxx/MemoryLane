package com.kllxx.memorylane.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Serializable
internal data object TabRoute

@Serializable
internal data object OtherRoute

@RunWith(RobolectricTestRunner::class)
class AppNavShellTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Shell(navController: NavHostController) {
        val onTab = navController.currentBackStackEntryAsState().value?.destination?.hasRoute<TabRoute>() == true
        AppNavShell(
            navController = navController,
            startDestination = TabRoute,
            showBottomBar = onTab,
            bottomBar = { NavigationBar { NavigationBarItem(true, {}, {}, label = { Text("tab") }) } },
        ) {
            composable<TabRoute> { Box(Modifier.fillMaxSize().tabScreenInsets().testTag(TAB)) }
            composable<OtherRoute> { Box(Modifier.fillMaxSize().testTag(OTHER)) }
        }
    }

    @Test
    fun `the tab screen keeps its bounds while it fades out for a screen without the bottom bar`() {
        lateinit var navController: NavHostController
        rule.setContent {
            navController = rememberNavController()
            Shell(navController)
        }
        rule.waitForIdle()
        val before = rule.onNodeWithTag(TAB).getUnclippedBoundsInRoot()

        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { navController.navigate(OtherRoute) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(CROSSFADE_PROGRESS_MS)

        // Both screens are on stage: the old one is still showing, the new one is coming in
        rule.onNodeWithTag(TAB).assertIsDisplayed()
        rule.onNodeWithTag(OTHER).assertIsDisplayed()
        assertEquals(before, rule.onNodeWithTag(TAB).getUnclippedBoundsInRoot())
    }

    private companion object {
        const val TAB = "tab"
        const val OTHER = "other"
        const val CROSSFADE_PROGRESS_MS = 100L
    }
}
