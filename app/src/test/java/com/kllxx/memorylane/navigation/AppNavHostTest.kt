package com.kllxx.memorylane.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kllxx.memorylane.AppEffect
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.feature.achievements.api.AchievementsRoute
import com.kllxx.memorylane.feature.calendar.api.CalendarRoute
import com.kllxx.memorylane.feature.onboarding.api.OnboardingRoute
import com.kllxx.memorylane.feature.profile.api.ProfileRoute
import com.kllxx.memorylane.feature.swipe.api.BeforeDeleteRoute
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import com.kllxx.memorylane.feature.swipe.impl.toBeforeDeleteRoute
import com.kllxx.memorylane.feature.swipe.impl.toResultRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The real [AppNavHost] (routes, callbacks, back stack rules, bottom bar) with stand-in screens. Each stand-in has a
 * button per callback of the real screen, so a click does what the screen would do when it finishes.
 */
@RunWith(RobolectricTestRunner::class)
class AppNavHostTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate.of(2026, 10, 15)
    private val deck = DeckKey.Day(day)
    private lateinit var navController: NavHostController
    private var reselects by mutableIntStateOf(0)

    @Composable
    private fun Stub(name: String, vararg actions: Pair<String, () -> Unit>) {
        Column(Modifier.testTag("screen-$name")) {
            actions.forEach { (label, action) ->
                Button(onClick = action, modifier = Modifier.testTag("$name-$label")) { Text(label) }
            }
        }
    }

    private val screens = AppScreens(
        onboarding = { onFinished -> Stub("onboarding", "finish" to onFinished) },
        swipe = { toBeforeDelete, toResult, toCalendar ->
            Stub(
                "swipe",
                "before-delete" to { toBeforeDelete(deck) },
                "result" to { toResult(deck, 4, 9) },
                "calendar" to toCalendar,
            )
        },
        beforeDelete = { back, toResult, toCalendar ->
            Stub(
                "before-delete",
                "back" to back,
                "result" to { toResult(deck, 3, 1_500L, 6, 9) },
                "calendar" to toCalendar,
            )
        },
        result = { toCalendar -> Stub("result", "calendar" to toCalendar) },
        calendar = { toSwipe, toUndated, reselected ->
            val count by rememberUpdatedState(reselects)
            LaunchedEffect(reselected) { reselected.collect { reselects = count + 1 } }
            Stub("calendar", "day" to { toSwipe(day) }, "undated" to toUndated)
        },
        achievements = { toCalendar -> Stub("achievements", "calendar" to toCalendar) },
        profile = { toOnboarding -> Stub("profile", "onboarding" to toOnboarding) },
    )

    private fun setContent(
        start: StartDestination = StartDestination.Main,
        effects: Flow<AppEffect> = emptyFlow(),
    ) {
        rule.setContent {
            navController = rememberNavController()
            AppNavHost(
                startDestination = start,
                appEffects = effects,
                navController = navController,
                screens = screens,
            )
        }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun clickTab(label: String) {
        rule.onNodeWithContentDescription(label).performClick()
        rule.waitForIdle()
    }

    private fun backStack(): List<String> = navController.currentBackStack.value.mapNotNull { entry ->
        when {
            entry.destination.hasRoute<OnboardingRoute>() -> "onboarding"
            entry.destination.hasRoute<CalendarRoute>() -> "calendar"
            entry.destination.hasRoute<AchievementsRoute>() -> "achievements"
            entry.destination.hasRoute<ProfileRoute>() -> "profile"
            entry.destination.hasRoute<SwipeRoute>() -> "swipe"
            entry.destination.hasRoute<BeforeDeleteRoute>() -> "before-delete"
            entry.destination.hasRoute<ResultRoute>() -> "result"
            else -> null
        }
    }

    private fun barIsShown(): Boolean = rule.onNodeWithContentDescription("Calendar").let {
        runCatching { it.assertExists() }.isSuccess
    }

    private fun openSwipeFromCalendar() = click("calendar-day")

    // --- where it starts ---

    @Test
    fun `a set up app starts on calendar with the bottom bar`() {
        setContent(StartDestination.Main)

        assertEquals(listOf("calendar"), backStack())
        assertEquals(true, barIsShown())
    }

    @Test
    fun `a new app starts on onboarding without the bottom bar`() {
        setContent(StartDestination.Onboarding)

        assertEquals(listOf("onboarding"), backStack())
        assertEquals(false, barIsShown())
    }

    @Test
    fun `finishing the first onboarding leaves calendar alone on the stack`() {
        setContent(StartDestination.Onboarding)

        click("onboarding-finish")

        assertEquals(listOf("calendar"), backStack())
        assertEquals(true, barIsShown())
    }

    @Test
    fun `an onboarding replayed from profile goes back to profile when it is finished`() {
        setContent()
        clickTab("Profile")
        click("profile-onboarding")

        assertEquals(listOf("calendar", "profile", "onboarding"), backStack())
        assertEquals(OnboardingRoute(fromProfile = true), navController.currentBackStackEntry!!.toRoute<OnboardingRoute>())

        click("onboarding-finish")

        assertEquals(listOf("calendar", "profile"), backStack())
    }

    // --- calendar to the deck ---

    @Test
    fun `a day of calendar opens its deck above calendar and hides the bar`() {
        setContent()

        openSwipeFromCalendar()

        assertEquals(listOf("calendar", "swipe"), backStack())
        assertEquals(SwipeRoute.day(day), navController.currentBackStackEntry!!.toRoute<SwipeRoute>())
        assertEquals(false, barIsShown())
    }

    @Test
    fun `the undated banner opens the undated deck`() {
        setContent()

        click("calendar-undated")

        assertEquals(SwipeRoute.undated(), navController.currentBackStackEntry!!.toRoute<SwipeRoute>())
    }

    // --- the deck, before delete and the result ---

    @Test
    fun `before delete is stacked on the deck and back returns to the deck`() {
        setContent()
        openSwipeFromCalendar()

        click("swipe-before-delete")

        assertEquals(listOf("calendar", "swipe", "before-delete"), backStack())
        assertEquals(deck.toBeforeDeleteRoute(), navController.currentBackStackEntry!!.toRoute<BeforeDeleteRoute>())
        assertEquals(false, barIsShown())

        click("before-delete-back")

        assertEquals(listOf("calendar", "swipe"), backStack())
    }

    @Test
    fun `the result replaces before delete and carries the numbers, the deck stays below it`() {
        setContent()
        openSwipeFromCalendar()
        click("swipe-before-delete")

        click("before-delete-result")

        assertEquals(listOf("calendar", "swipe", "result"), backStack())
        assertEquals(deck.toResultRoute(3, 1_500L, 6, 9), navController.currentBackStackEntry!!.toRoute<ResultRoute>())
    }

    @Test
    fun `a deck finished without deleting goes straight to a result with nothing freed`() {
        setContent()
        openSwipeFromCalendar()

        click("swipe-result")

        assertEquals(listOf("calendar", "swipe", "result"), backStack())
        assertEquals(deck.toResultRoute(0, 0L, 4, 9), navController.currentBackStackEntry!!.toRoute<ResultRoute>())
    }

    @Test
    fun `done on the result leaves calendar alone on the stack`() {
        setContent()
        openSwipeFromCalendar()
        click("swipe-before-delete")
        click("before-delete-result")

        click("result-calendar")

        assertEquals(listOf("calendar"), backStack())
        assertEquals(true, barIsShown())
    }

    @Test
    fun `an empty deck or a missing permission sends the deck back to calendar`() {
        setContent()
        openSwipeFromCalendar()

        click("swipe-calendar")

        assertEquals(listOf("calendar"), backStack())
    }

    @Test
    fun `before delete with nothing to delete sends the user to calendar and drops the deck`() {
        setContent()
        openSwipeFromCalendar()
        click("swipe-before-delete")

        click("before-delete-calendar")

        assertEquals(listOf("calendar"), backStack())
    }

    // --- the bar and the tabs ---

    @Test
    fun `the bar is shown on the three tabs`() {
        setContent()

        listOf("Achievements", "Profile", "Calendar").forEach { tab ->
            clickTab(tab)
            assertEquals("the bar on $tab", true, barIsShown())
        }
    }

    @Test
    fun `achievements leads back to the calendar tab`() {
        setContent()
        clickTab("Achievements")
        assertEquals(listOf("calendar", "achievements"), backStack())

        click("achievements-calendar")

        assertEquals(listOf("calendar"), backStack())
    }

    @Test
    fun `a tap on the calendar tab while it is shown asks the list to go to the current month`() {
        setContent()

        clickTab("Calendar")
        clickTab("Calendar")

        assertEquals(2, reselects)
        assertEquals(listOf("calendar"), backStack())
    }

    // --- the reminder ---

    @Test
    fun `the reminder opens the deck of the day above calendar`() {
        val effects = MutableSharedFlow<AppEffect>(extraBufferCapacity = 1)
        setContent(effects = effects)

        effects.tryEmit(AppEffect.OpenToday(day))
        rule.waitForIdle()

        assertEquals(listOf("calendar", "swipe"), backStack())
        assertEquals(SwipeRoute.day(day), navController.currentBackStackEntry!!.toRoute<SwipeRoute>())
    }

    @Test
    fun `the reminder while another tab is shown still puts the deck right above calendar`() {
        val effects = MutableSharedFlow<AppEffect>(extraBufferCapacity = 1)
        setContent(effects = effects)
        clickTab("Profile")

        effects.tryEmit(AppEffect.OpenToday(day))
        rule.waitForIdle()

        assertEquals(listOf("calendar", "swipe"), backStack())
    }

    @Test
    fun `the reminder during onboarding does not crash and the deck opens`() {
        // MainActivity only sends the event after a set up start (see AppViewModel); the graph itself must still cope
        val effects = MutableSharedFlow<AppEffect>(extraBufferCapacity = 1)
        setContent(start = StartDestination.Onboarding, effects = effects)

        effects.tryEmit(AppEffect.OpenToday(day))
        rule.waitForIdle()

        assertEquals("swipe", backStack().last())
    }
}
