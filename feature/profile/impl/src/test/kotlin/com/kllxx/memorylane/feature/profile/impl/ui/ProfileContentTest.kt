package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
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

    private fun setContent(initial: ProfileState.Content = ProfileState.Content(), themeMode: ThemeMode = ThemeMode.Light) {
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
                    is24Hour = true,
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
