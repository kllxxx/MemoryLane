package com.kllxx.memorylane.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kllxx.memorylane.R
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.feature.achievements.api.AchievementsRoute
import com.kllxx.memorylane.feature.profile.api.ProfileRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BottomNavBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Bar(content: @Composable () -> Unit = {}) {
        val navController = rememberNavController()
        val destination = navController.currentBackStackEntryAsState().value?.destination
        val tabs = listOf(
            Triple(TimelineRoute, TimelineRoute::class, R.string.nav_timeline),
            Triple(AchievementsRoute, AchievementsRoute::class, R.string.nav_achievements),
            Triple(ProfileRoute, ProfileRoute::class, R.string.nav_profile),
        )
        Column(Modifier.fillMaxSize()) {
            NavHost(navController, startDestination = TimelineRoute, modifier = Modifier.weight(1f)) {
                composable<TimelineRoute> { Box(Modifier.testTag(TIMELINE)) }
                composable<AchievementsRoute> { Box(Modifier.testTag(ACHIEVEMENTS)) }
                composable<ProfileRoute> { Box(Modifier.testTag(PROFILE)) }
            }
            BottomNavBar(
                tabs = tabs.map { (route, routeClass, label) ->
                    BottomBarTab(
                        label = stringResource(label),
                        outlinedIcon = UiR.drawable.ic_nav_person_outlined,
                        filledIcon = UiR.drawable.ic_nav_person_filled,
                        selected = destination?.hierarchy?.any { it.hasRoute(routeClass) } == true,
                        onClick = { navController.navigate(route) },
                    )
                },
            )
        }
    }

    @Test
    fun `clicking each tab opens its screen and marks only that tab selected`() {
        rule.setContent { Bar() }
        val labels = listOf(R.string.nav_timeline, R.string.nav_achievements, R.string.nav_profile)
            .map { androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(it) }
        val screens = listOf(TIMELINE, ACHIEVEMENTS, PROFILE)

        labels.forEachIndexed { index, label ->
            rule.onNodeWithContentDescription(label).performClick()
            rule.onNodeWithTag(screens[index]).assertExists()
            labels.forEachIndexed { other, otherLabel ->
                val node = rule.onNodeWithContentDescription(otherLabel)
                if (other == index) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
    }

    private companion object {
        const val TIMELINE = "timeline"
        const val ACHIEVEMENTS = "achievements"
        const val PROFILE = "profile"
    }
}
