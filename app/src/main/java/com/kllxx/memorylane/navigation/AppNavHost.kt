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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kllxx.memorylane.R
import com.kllxx.memorylane.feature.achievements.api.AchievementsRoute
import com.kllxx.memorylane.feature.achievements.impl.AchievementsScreen
import com.kllxx.memorylane.feature.onboarding.api.OnboardingRoute
import com.kllxx.memorylane.feature.onboarding.impl.OnboardingScreen
import com.kllxx.memorylane.feature.profile.api.ProfileRoute
import com.kllxx.memorylane.feature.profile.impl.ProfileScreen
import com.kllxx.memorylane.feature.swipe.api.ReviewRoute
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import com.kllxx.memorylane.feature.swipe.impl.SwipeScreen
import com.kllxx.memorylane.feature.swipe.impl.review.ReviewScreen
import com.kllxx.memorylane.feature.swipe.impl.timeline.TimelineIntent
import com.kllxx.memorylane.feature.swipe.impl.timeline.TimelineScreen
import com.kllxx.memorylane.feature.swipe.impl.timeline.TimelineViewModel
import java.time.LocalDate
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
    today: LocalDate,
    navController: NavHostController = rememberNavController(),
) {
    // ISO date of the day last opened in Swipe or Review, waiting to be reloaded on Timeline
    var lastSortedDate by rememberSaveable { mutableStateOf<String?>(null) }
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
                                    // Anchored to Timeline, not the graph's start, which may be Swipe or Onboarding
                                    popUpTo<TimelineRoute> {
                                        inclusive = false
                                        saveState = true
                                    }
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
            startDestination = startDestination.toRoute(today),
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(SwipeRoute(today.toString())) {
                            popUpTo<OnboardingRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<SwipeRoute> { entry ->
                // Remembered so Timeline can reload this day once the user is back on it
                LaunchedEffect(entry) { lastSortedDate = entry.toRoute<SwipeRoute>().date }
                SwipeScreen(
                    onNavigateToReview = { date, totalPhotosCount ->
                        navController.navigate(ReviewRoute(date.toString(), totalPhotosCount)) {
                            popUpTo<SwipeRoute> { inclusive = true }
                        }
                    },
                    onNavigateToTimeline = { navController.navigateToTimeline() },
                )
            }
            composable<ReviewRoute> { entry ->
                LaunchedEffect(entry) { lastSortedDate = entry.toRoute<ReviewRoute>().date }
                ReviewScreen()
            }
            composable<TimelineRoute> {
                val timelineViewModel: TimelineViewModel = hiltViewModel()
                // Timeline caches each day, so the day that was just sorted has to be reloaded
                LaunchedEffect(lastSortedDate) {
                    lastSortedDate?.let { date ->
                        timelineViewModel.onIntent(TimelineIntent.RefreshDay(LocalDate.parse(date)))
                        lastSortedDate = null
                    }
                }
                TimelineScreen(
                    onNavigateToSwipe = { date -> navController.navigate(SwipeRoute(date.toString())) },
                    viewModel = timelineViewModel,
                )
            }
            composable<AchievementsRoute> { AchievementsScreen() }
            composable<ProfileRoute> {
                ProfileScreen(
                    onNavigateToOnboarding = {
                        // Full restart of the flow: nothing from the previous session stays on the back stack
                        navController.navigate(OnboardingRoute) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Leaves Swipe/Review for Timeline without ever stacking a second Timeline. When Timeline is already
 * on the back stack everything above it is popped; when it is not (Swipe was the start destination)
 * Swipe is replaced instead.
 */
private fun NavHostController.navigateToTimeline() {
    val timelineOnBackStack = runCatching { getBackStackEntry<TimelineRoute>() }.isSuccess
    navigate(TimelineRoute) {
        if (timelineOnBackStack) {
            popUpTo<TimelineRoute> { inclusive = false }
        } else {
            popUpTo<SwipeRoute> { inclusive = true }
        }
        launchSingleTop = true
    }
}

private fun StartDestination.toRoute(today: LocalDate): Any = when (this) {
    StartDestination.Onboarding -> OnboardingRoute
    StartDestination.Swipe -> SwipeRoute(today.toString())
    StartDestination.Main -> TimelineRoute
}
