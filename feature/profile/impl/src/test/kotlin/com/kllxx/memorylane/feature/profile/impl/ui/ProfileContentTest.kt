package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileReducer
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders the screen content with a state that follows the intents, as the ViewModel would. */
@RunWith(RobolectricTestRunner::class)
class ProfileContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<ProfileIntent>()

    private fun setContent(
        initial: ProfileState.Content = ProfileState.Content(),
        themeMode: ThemeMode = ThemeMode.Light,
        is24Hour: Boolean = true,
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initial) }
            MemoryLaneTheme(themeMode = themeMode) {
                ProfileContent(
                    state = state,
                    onIntent = {
                        intents += it
                        state = ProfileReducer.reduce(state, it)
                    },
                    snackbarHostState = SnackbarHostState(),
                    is24Hour = is24Hour,
                )
            }
        }
    }

    @Test
    fun `selecting a goal chip moves the selection`() {
        setContent(ProfileState.Content(goal = StreakGoal.D30))
        composeRule.onNodeWithTag(goalChipTag(StreakGoal.D30)).assertIsSelected()

        composeRule.onNodeWithTag(goalChipTag(StreakGoal.D100)).performClick()

        composeRule.onNodeWithTag(goalChipTag(StreakGoal.D100)).assertIsSelected()
        assertEquals(listOf<ProfileIntent>(ProfileIntent.SelectGoal(StreakGoal.D100)), intents)
    }

    @Test
    fun `selecting a theme segment moves the selection`() {
        setContent(ProfileState.Content(themeMode = ThemeMode.Dark))
        composeRule.onNodeWithTag(themeSegmentTag(ThemeMode.Dark)).performScrollTo().assertIsSelected()

        composeRule.onNodeWithTag(themeSegmentTag(ThemeMode.Light)).performScrollTo().performClick()

        composeRule.onNodeWithTag(themeSegmentTag(ThemeMode.Light)).assertIsSelected()
        assertEquals(listOf<ProfileIntent>(ProfileIntent.SelectTheme(ThemeMode.Light)), intents)
    }

    @Test
    fun `time row opens the picker while notifications are on`() {
        setContent(ProfileState.Content(userNotificationsEnabled = true, systemNotificationsEnabled = true))

        composeRule.onNodeWithTag(REMINDER_TIME_ROW_TAG).performScrollTo().performClick()

        assertEquals(listOf<ProfileIntent>(ProfileIntent.OpenTimePicker), intents)
    }


    @Test
    fun `time row shows the time on a 24-hour clock like the picker does`() {
        setContent(ProfileState.Content(reminderTime = LocalTime.of(19, 30)), is24Hour = true)

        composeRule.onNodeWithTag(REMINDER_TIME_ROW_TAG).performScrollTo()
        composeRule.onNodeWithText("19:30").assertExists()
    }

    @Test
    fun `time row shows the time on a 12-hour clock like the picker does`() {
        setContent(ProfileState.Content(reminderTime = LocalTime.of(19, 30)), is24Hour = false)

        composeRule.onNodeWithTag(REMINDER_TIME_ROW_TAG).performScrollTo()
        composeRule.onNodeWithText(formatReminderTime(LocalTime.of(19, 30), is24Hour = false, Locale.US)).assertExists()
        composeRule.onNodeWithText("19:30").assertDoesNotExist()
    }
    @Test
    fun `time row is not clickable while notifications are off`() {
        setContent(ProfileState.Content(userNotificationsEnabled = false))

        composeRule.onNodeWithTag(REMINDER_TIME_ROW_TAG).performScrollTo().performClick()

        assertTrue(intents.isEmpty())
    }

    @Test
    fun `time row is not clickable while the system blocks notifications`() {
        setContent(ProfileState.Content(userNotificationsEnabled = true, systemNotificationsEnabled = false))

        composeRule.onNodeWithTag(REMINDER_TIME_ROW_TAG).performScrollTo().performClick()

        assertTrue(intents.isEmpty())
    }

    @Test
    fun `switch shows on only when the user and the system allow notifications`() {
        setContent(ProfileState.Content(userNotificationsEnabled = true, systemNotificationsEnabled = true))
        composeRule.onNodeWithTag(NOTIFICATIONS_SWITCH_TAG).performScrollTo().assertIsOn()
    }

    @Test
    fun `switch click sends the toggle intent`() {
        setContent(ProfileState.Content(userNotificationsEnabled = true, systemNotificationsEnabled = true))

        composeRule.onNodeWithTag(NOTIFICATIONS_SWITCH_TAG).performScrollTo().performClick()

        assertEquals(listOf<ProfileIntent>(ProfileIntent.NotificationsToggled(on = false)), intents)
    }

    @Test
    fun `about rows send their intents`() {
        setContent()

        composeRule.onNodeWithTag(REPLAY_ONBOARDING_ROW_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(CONTACT_SUPPORT_ROW_TAG).performScrollTo().performClick()

        assertEquals(
            listOf(ProfileIntent.ReplayOnboardingClicked, ProfileIntent.ContactSupportClicked),
            intents,
        )
    }

    @Test
    fun `the buttons row is a switch that is off by default`() {
        setContent(ProfileState.Content(swipeButtonsEnabled = false))

        val row = composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).performScrollTo()
        row.assertIsOff()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        composeRule.onNodeWithTag(BUTTONS_MODE_SWITCH_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the buttons row shows the setting that is on`() {
        setContent(ProfileState.Content(swipeButtonsEnabled = true))

        composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).performScrollTo().assertIsOn()
        composeRule.onNodeWithTag(BUTTONS_MODE_SWITCH_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a tap anywhere on the buttons row toggles it and sends the intent`() {
        setContent(ProfileState.Content(swipeButtonsEnabled = false))

        composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).assertIsOn()
        composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).performClick()
        composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).assertIsOff()

        assertEquals(
            listOf<ProfileIntent>(ProfileIntent.SwipeButtonsToggled(true), ProfileIntent.SwipeButtonsToggled(false)),
            intents,
        )
    }

    @Test
    fun `the review section sits between notifications and appearance with its texts`() {
        setContent(ProfileState.Content())

        composeRule.onNodeWithText("РАЗБОР ФОТО").performScrollTo().assertExists()
        composeRule.onNodeWithText("Кнопки на экране свайпа").assertExists()
        composeRule.onNodeWithText("Показывать ✕ и ✓ на фото вместо одних свайпов").assertExists()
        val notifications = composeRule.onNodeWithTag(REMINDER_TIME_ROW_TAG).getUnclippedBoundsInRoot().bottom
        val review = composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).getUnclippedBoundsInRoot().top
        val appearance = composeRule.onNodeWithTag(themeSegmentTag(ThemeMode.Dark)).getUnclippedBoundsInRoot().top
        assertTrue(notifications < review && review < appearance)
    }

    @Test
    fun `the buttons row also renders in the dark theme`() {
        setContent(ProfileState.Content(swipeButtonsEnabled = true), themeMode = ThemeMode.Dark)

        composeRule.onNodeWithTag(BUTTONS_MODE_ROW_TAG).performScrollTo().assertIsOn()
    }

    @Test
    fun `dark theme renders the whole screen`() {
        setContent(ProfileState.Content(currentStreak = 7), themeMode = ThemeMode.Dark)

        composeRule.onNodeWithTag(goalChipTag(StreakGoal.D30)).assertIsSelected()
    }

    @Test
    fun `while loading only the title is shown, not the default settings`() {
        composeRule.setContent {
            MemoryLaneTheme(themeMode = ThemeMode.Light) {
                ProfileContent(
                    state = ProfileState.Loading,
                    onIntent = { intents += it },
                    snackbarHostState = SnackbarHostState(),
                    is24Hour = true,
                )
            }
        }

        composeRule.onNodeWithText("Профиль").assertExists()
        composeRule.onNodeWithTag(goalChipTag(StreakGoal.D30)).assertDoesNotExist()
        composeRule.onNodeWithTag(REPLAY_ONBOARDING_ROW_TAG).assertDoesNotExist()
        assertTrue(intents.isEmpty())
    }
}
