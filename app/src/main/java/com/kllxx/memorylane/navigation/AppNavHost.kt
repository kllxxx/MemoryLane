package com.kllxx.memorylane.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kllxx.memorylane.AppEffect
import com.kllxx.memorylane.R
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.feature.achievements.api.AchievementsRoute
import com.kllxx.memorylane.feature.achievements.impl.AchievementsScreen
import com.kllxx.memorylane.feature.calendar.api.CalendarRoute
import com.kllxx.memorylane.feature.calendar.impl.CalendarScreen
import com.kllxx.memorylane.feature.onboarding.api.OnboardingRoute
import com.kllxx.memorylane.feature.onboarding.impl.OnboardingScreen
import com.kllxx.memorylane.feature.profile.api.ProfileRoute
import com.kllxx.memorylane.feature.profile.impl.ProfileScreen
import com.kllxx.memorylane.feature.swipe.api.BeforeDeleteRoute
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import com.kllxx.memorylane.feature.swipe.impl.SwipeScreen
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.BeforeDeleteScreen
import com.kllxx.memorylane.feature.swipe.impl.result.ResultScreen
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

private data class BottomNavItem(
    val route: Any,
    val routeClass: KClass<*>,
    val labelRes: Int,
    @DrawableRes val outlinedIcon: Int,
    @DrawableRes val filledIcon: Int,
)

private val bottomNavItems = listOf(
    BottomNavItem(
        CalendarRoute, CalendarRoute::class, R.string.nav_calendar,
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
    appEffects: Flow<AppEffect>,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(navController, appEffects) {
        appEffects.collect { effect ->
            when (effect) {
                is AppEffect.OpenToday -> navController.openToday(effect.date)
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Bottom bar only on the tab screens; hidden during Onboarding and Swipe.
    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        bottomNavItems.any { destination.hasRoute(it.routeClass) }
    } == true

    // A tap on the Calendar tab while Calendar is shown: the list goes back to the current month
    val calendarReselected = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    AppNavShell(
        navController = navController,
        startDestination = startDestination.toRoute(),
        showBottomBar = showBottomBar,
        bottomBar = {
            BottomNavBar(
                tabs = bottomNavItems.map { item ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                    BottomBarTab(
                        label = stringResource(item.labelRes),
                        outlinedIcon = item.outlinedIcon,
                        filledIcon = item.filledIcon,
                        selected = selected,
                        onClick = {
                            if (selected && item.route == CalendarRoute) {
                                calendarReselected.tryEmit(Unit)
                            } else {
                                navController.navigateToTab(item.route)
                            }
                        },
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
                        navController.navigate(CalendarRoute) {
                            popUpTo<OnboardingRoute> { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<SwipeRoute> {
            Box(Modifier.fillMaxSize().fullScreenInsets()) {
                SwipeScreen(
                    // Swipe stays on the back stack: Back from «Перед удалением» returns to the deck
                    onNavigateToBeforeDelete = { date -> navController.navigate(BeforeDeleteRoute(date.toString())) },
                    onNavigateToResult = { date, keptCount, totalCount ->
                        navController.navigate(ResultRoute(date.toString(), 0, 0L, keptCount, totalCount))
                    },
                    onNavigateToCalendar = { navController.navigateToCalendar() },
                )
            }
        }
        composable<BeforeDeleteRoute> {
            Box(Modifier.fillMaxSize().fullScreenInsets()) {
                BeforeDeleteScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToResult = { date, deletedCount, freedBytes, keptCount, totalCount ->
                        navController.navigate(ResultRoute(date.toString(), deletedCount, freedBytes, keptCount, totalCount)) {
                            popUpTo<BeforeDeleteRoute> { inclusive = true }
                        }
                    },
                    onNavigateToCalendar = { navController.navigateToCalendar() },
                )
            }
        }
        composable<ResultRoute> {
            Box(Modifier.fillMaxSize().fullScreenInsets()) {
                ResultScreen(onNavigateToCalendar = { navController.navigateToCalendar() })
            }
        }
        composable<CalendarRoute> {
            Box(Modifier.fillMaxSize().tabScreenInsets()) {
                CalendarScreen(
                    onNavigateToSwipe = { date -> navController.navigate(SwipeRoute(date.toString())) },
                    reselected = calendarReselected,
                )
            }
        }
        composable<AchievementsRoute> {
            Box(Modifier.fillMaxSize().tabScreenInsets()) {
                AchievementsScreen(onNavigateToCalendar = { navController.navigateToTab(CalendarRoute) })
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
 * Leaves Swipe/Review for Calendar without ever stacking a second Calendar: Calendar is always the
 * root below them, so everything above it is popped.
 */
private fun NavHostController.navigateToCalendar() {
    navigate(CalendarRoute) {
        popUpTo<CalendarRoute> { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * Opens the deck of [date] from outside (the reminder). Calendar stays below it, a second tap does not stack a
 * second deck, and any other screen above Calendar is replaced; Back goes to Calendar.
 */
internal fun NavHostController.openToday(date: LocalDate) {
    navigate(SwipeRoute(date.toString())) {
        popUpTo<CalendarRoute> { inclusive = false }
        launchSingleTop = true
    }
}

private fun StartDestination.toRoute(): Any = when (this) {
    StartDestination.Onboarding -> OnboardingRoute()
    StartDestination.Main -> CalendarRoute
}

/** Switches to a bottom bar tab; shared by the bar and by screens that send the user to another tab. */
internal fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        // Anchored to Calendar, not the graph's start, which may be Swipe or Onboarding
        popUpTo<CalendarRoute> {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
