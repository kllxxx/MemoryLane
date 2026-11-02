package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotSelected
import com.kllxx.memorylane.core.domain.language.AppLanguage
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The language card under its own header: three segments, the chosen one selected, a tap sends the intent. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class LanguageCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<ProfileIntent>()

    private fun show(language: AppLanguage) {
        composeRule.setContent {
            MemoryLaneTheme {
                LanguageCard(ProfileState.Content(language = language), onIntent = { intents += it })
            }
        }
    }

    @Test
    fun `the language in force is the selected segment`() {
        show(AppLanguage.Russian)

        composeRule.onNodeWithTag(languageSegmentTag(AppLanguage.Russian)).assertIsSelected()
        composeRule.onNodeWithTag(languageSegmentTag(AppLanguage.English)).assertIsNotSelected()
        composeRule.onNodeWithTag(languageSegmentTag(AppLanguage.System)).assertIsNotSelected()
    }

    @Test
    fun `tapping a segment sends its language`() {
        show(AppLanguage.System)

        composeRule.onNodeWithTag(languageSegmentTag(AppLanguage.English)).performClick()
        composeRule.onNodeWithTag(languageSegmentTag(AppLanguage.Russian)).performClick()

        assertEquals(
            listOf(ProfileIntent.SelectLanguage(AppLanguage.English), ProfileIntent.SelectLanguage(AppLanguage.Russian)),
            intents,
        )
    }

    @Test
    fun `the two languages are named in their own language, the system one in the language of the screen`() {
        show(AppLanguage.System)

        composeRule.onNodeWithText("System").assertExists()
        composeRule.onNodeWithText("Русский").assertExists()
        composeRule.onNodeWithText("English").assertExists()
    }

    @Test
    @Config(qualifiers = "ru")
    fun `on a Russian screen the title and the system segment are Russian, the names of the languages stay`() {
        show(AppLanguage.System)

        composeRule.onNodeWithText("Системный").assertExists()
        composeRule.onNodeWithText("Русский").assertExists()
        composeRule.onNodeWithText("English").assertExists()
    }
}
