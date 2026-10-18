package com.kllxx.memorylane.feature.onboarding.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OnboardingContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<OnboardingIntent>()

    private var page by mutableIntStateOf(0)
    private var isLoading by mutableStateOf(false)

    private fun show(page: Int, isLoading: Boolean = false) {
        this.page = page
        this.isLoading = isLoading
        composeRule.setContent {
            MemoryLaneTheme {
                OnboardingContent(
                    state = OnboardingState(currentPage = this.page, isLoading = this.isLoading, isLastPage = this.page == 3),
                    onIntent = { intents += it },
                )
            }
        }
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `first slide has no back button and a next button`() {
        show(page = 0)
        composeRule.onNodeWithContentDescription(string(R.string.onboarding_back)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.onboarding_skip)).assertExists()
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()
        assertEquals(listOf<OnboardingIntent>(OnboardingIntent.NextPage), intents)
    }

    @Test
    fun `second slide shows back and next`() {
        show(page = 1)
        composeRule.onNodeWithContentDescription(string(R.string.onboarding_back)).performClick()
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()
        assertEquals(listOf(OnboardingIntent.PreviousPage, OnboardingIntent.NextPage), intents)
    }

    @Test
    fun `gallery slide button requests the photo permission`() {
        show(page = 2)
        composeRule.onNodeWithText(string(R.string.onboarding_allow)).performClick()
        assertEquals(listOf<OnboardingIntent>(OnboardingIntent.RequestPhotoPermission), intents)
    }

    @Test
    fun `notification slide button requests the notification permission`() {
        show(page = 3)
        composeRule.onNodeWithText(string(R.string.onboarding_allow)).performClick()
        assertEquals(listOf<OnboardingIntent>(OnboardingIntent.RequestNotificationPermission), intents)
    }

    @Test
    fun `skip is available on every slide`() {
        show(page = 0)
        (0..3).forEach { slide ->
            page = slide
            intents.clear()
            composeRule.onNodeWithText(string(R.string.onboarding_skip)).performClick()
            assertEquals(listOf<OnboardingIntent>(OnboardingIntent.Skip), intents)
        }
    }

    @Test
    fun `button is disabled while a permission request is running`() {
        show(page = 2, isLoading = true)
        composeRule.onNodeWithText(string(R.string.onboarding_allow)).assertIsNotEnabled()
    }

    @Test
    fun `button is enabled when idle`() {
        show(page = 2)
        composeRule.onNodeWithText(string(R.string.onboarding_allow)).assertIsEnabled()
    }
}
