package com.kllxx.memorylane.navigation

import androidx.compose.runtime.Composable
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.feature.achievements.impl.AchievementsScreen
import com.kllxx.memorylane.feature.calendar.impl.CalendarScreen
import com.kllxx.memorylane.feature.onboarding.impl.OnboardingScreen
import com.kllxx.memorylane.feature.profile.impl.ProfileScreen
import com.kllxx.memorylane.feature.swipe.impl.SwipeScreen
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.BeforeDeleteScreen
import com.kllxx.memorylane.feature.swipe.impl.result.ResultScreen
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The screen of every destination of [AppNavHost], each taking the callbacks the host wires. The host owns the
 * navigation (routes, back stack rules, insets); a slot only draws the screen and calls back. [Real] is the app; a test
 * passes stand-ins that call the callbacks, to check the wiring without the ViewModels of the features.
 */
internal class AppScreens(
    val onboarding: @Composable (onFinished: () -> Unit) -> Unit,
    val swipe: @Composable (
        onNavigateToBeforeDelete: (DeckKey) -> Unit,
        onNavigateToResult: (DeckKey, Int, Int) -> Unit,
        onNavigateToCalendar: () -> Unit,
    ) -> Unit,
    val beforeDelete: @Composable (
        onBack: () -> Unit,
        onNavigateToResult: (DeckKey, Int, Long, Int, Int) -> Unit,
        onNavigateToCalendar: () -> Unit,
    ) -> Unit,
    val result: @Composable (onNavigateToCalendar: () -> Unit) -> Unit,
    val calendar: @Composable (
        onNavigateToSwipe: (LocalDate) -> Unit,
        onNavigateToUndated: () -> Unit,
        reselected: Flow<Unit>,
    ) -> Unit,
    val achievements: @Composable (onNavigateToCalendar: () -> Unit) -> Unit,
    val profile: @Composable (onNavigateToOnboarding: () -> Unit) -> Unit,
) {
    companion object {
        val Real = AppScreens(
            onboarding = { onFinished -> OnboardingScreen(onFinished = onFinished) },
            swipe = { toBeforeDelete, toResult, toCalendar ->
                SwipeScreen(
                    onNavigateToBeforeDelete = toBeforeDelete,
                    onNavigateToResult = toResult,
                    onNavigateToCalendar = toCalendar,
                )
            },
            beforeDelete = { onBack, toResult, toCalendar ->
                BeforeDeleteScreen(onBack = onBack, onNavigateToResult = toResult, onNavigateToCalendar = toCalendar)
            },
            result = { toCalendar -> ResultScreen(onNavigateToCalendar = toCalendar) },
            calendar = { toSwipe, toUndated, reselected ->
                CalendarScreen(
                    onNavigateToSwipe = toSwipe,
                    onNavigateToUndated = toUndated,
                    reselected = reselected,
                )
            },
            achievements = { toCalendar -> AchievementsScreen(onNavigateToCalendar = toCalendar) },
            profile = { toOnboarding -> ProfileScreen(onNavigateToOnboarding = toOnboarding) },
        )
    }
}
