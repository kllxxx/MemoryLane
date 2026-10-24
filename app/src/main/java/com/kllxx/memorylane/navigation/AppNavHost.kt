package com.kllxx.memorylane.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kllxx.memorylane.R
import com.kllxx.memorylane.core.ui.R as UiR
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
    @DrawableRes val outlinedIcon: Int,
    @DrawableRes val filledIcon: Int,
)

private val bottomNavItems = listOf(
    BottomNavItem(
        TimelineRoute, TimelineRoute::class, R.string.nav_timeline,
        UiR.drawable.ic_nav_calendar_outlined, UiR.drawable.ic_nav_calendar_filled,
    ),
    BottomNavItem(
        AchievementsRoute, AchievementsRoute::class, R.string.nav_achievements,
        UiR.drawable.ic_nav_trophy_outlined, UiR.drawable.ic_nav_trophy_filled,
    ),
    BottomNavItem(
        ProfileRoute, ProfileRoute::class, R.string.nav_profile,
        UiR.drawable.ic_nav_person_outlined, UiR.drawable.ic_nav_person_filled,
    ),
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

    AppNavShell(
        navController = navController,
        startDestination = startDestination.toRoute(today),
        showBottomBar = showBottomBar,
        bottomBar = {
            BottomNavBar(
                tabs = bottomNavItems.map { item ->
                    BottomBarTab(
                        label = stringResource(item.labelRes),
                        outlinedIcon = item.outlinedIcon,
                        filledIcon = item.filledIcon,
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true,
                        onClick = { navController.navigateToTab(item.route) },
                    )
                },
            )
        },
    ) {
        // Each screen applies its own insets (see AppNavShell); Onboarding paints the status bar and
        // handles the navigation bar inset on its own
        composable<OnboardingRoute> { entry ->
            val fromProfile = entry.toRoute<OnboardingRoute>().fromProfile
            OnboardingScreen(
                onFinished = {
                    if (fromProfile) {
                        // A replay: back to the Profile tab it was started from
                        navController.popBackStack()
                    } else {
                        navController.navigate(SwipeRoute(today.toString())) {
                            popUpTo<OnboardingRoute> { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<SwipeRoute> { entry ->
            // Remembered so Timeline can reload this day once the user is back on it
            LaunchedEffect(entry) { lastSortedDate = entry.toRoute<SwipeRoute>().date }
            Box(Modifier.fillMaxSize().fullScreenInsets()) {
                SwipeScreen(
                    onNavigateToReview = { date, totalPhotosCount ->
                        navController.navigate(ReviewRoute(date.toString(), totalPhotosCount)) {
                            popUpTo<SwipeRoute> { inclusive = true }
                        }
                    },
                    onNavigateToTimeline = { navController.navigateToTimeline() },
                )
            }
        }
        composable<ReviewRoute> { entry ->
            LaunchedEffect(entry) { lastSortedDate = entry.toRoute<ReviewRoute>().date }
            Box(Modifier.fillMaxSize().fullScreenInsets()) { ReviewScreen() }
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
            Box(Modifier.fillMaxSize().tabScreenInsets()) {
                TimelineScreen(
                    onNavigateToSwipe = { date -> navController.navigate(SwipeRoute(date.toString())) },
                    viewModel = timelineViewModel,
                )
            }
        }
        composable<AchievementsRoute> {
            Box(Modifier.fillMaxSize().tabScreenInsets()) {
                AchievementsScreen(onNavigateToTimeline = { navController.navigateToTab(TimelineRoute) })
            }
        }
        composable<ProfileRoute> {
            Box(Modifier.fillMaxSize().tabScreenInsets()) {
                ProfileScreen(
                    // Stacked on top of Profile, so finishing or skipping returns to it
                    onNavigateToOnboarding = { navController.navigate(OnboardingRoute(fromProfile = true)) },
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
    StartDestination.Onboarding -> OnboardingRoute()
    StartDestination.Swipe -> SwipeRoute(today.toString())
    StartDestination.Main -> TimelineRoute
}

/** Switches to a bottom bar tab; shared by the bar and by screens that send the user to another tab. */
internal fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        // Anchored to Timeline, not the graph's start, which may be Swipe or Onboarding
        popUpTo<TimelineRoute> {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
