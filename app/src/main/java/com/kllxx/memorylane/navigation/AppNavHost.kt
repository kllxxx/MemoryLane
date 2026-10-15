package com.kllxx.memorylane.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kllxx.memorylane.R
import com.kllxx.memorylane.feature.achievements.api.AchievementsRoute
import com.kllxx.memorylane.feature.achievements.impl.AchievementsScreen
import com.kllxx.memorylane.feature.onboarding.api.OnboardingRoute
import com.kllxx.memorylane.feature.onboarding.impl.OnboardingScreen
import com.kllxx.memorylane.feature.profile.api.ProfileRoute
import com.kllxx.memorylane.feature.profile.impl.ProfileScreen
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import com.kllxx.memorylane.feature.swipe.impl.SwipeScreen
import com.kllxx.memorylane.feature.swipe.impl.timeline.TimelineScreen
import kotlin.reflect.KClass

private data class BottomNavItem(
    val route: Any,
    val routeClass: KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(TimelineRoute, TimelineRoute::class, R.string.nav_timeline, Icons.Filled.Timeline),
    BottomNavItem(AchievementsRoute, AchievementsRoute::class, R.string.nav_achievements, Icons.Filled.EmojiEvents),
    BottomNavItem(ProfileRoute, ProfileRoute::class, R.string.nav_profile, Icons.Filled.Person),
)

@Composable
fun AppNavHost(
    startDestination: StartDestination,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Bottom bar only on the tab screens; hidden during Onboarding and Swipe.
    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        bottomNavItems.any { destination.hasRoute(it.routeClass) }
    } == true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination.toRoute(),
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<OnboardingRoute> { OnboardingScreen() }
            composable<SwipeRoute> { SwipeScreen() }
            composable<TimelineRoute> { TimelineScreen() }
            composable<AchievementsRoute> { AchievementsScreen() }
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}

private fun StartDestination.toRoute(): Any = when (this) {
    StartDestination.Onboarding -> OnboardingRoute
    StartDestination.Swipe -> SwipeRoute
    StartDestination.Main -> TimelineRoute
}
