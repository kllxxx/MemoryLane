package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Profile screen in both languages: the title, the section headers and the language row. */
@RunWith(RobolectricTestRunner::class)
class ProfileContentLanguagesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show() {
        composeRule.setContent {
            MemoryLaneTheme {
                ProfileContent(
                    state = ProfileState.Content(),
                    onIntent = {},
                    snackbarHostState = SnackbarHostState(),
                    is24Hour = true,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English screen`() {
        show()

        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("APPEARANCE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Language").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PERMISSIONS").assertExists()
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the Russian screen`() {
        show()

        composeRule.onNodeWithText("Профиль").assertIsDisplayed()
        composeRule.onNodeWithText("ОФОРМЛЕНИЕ").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Язык").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("РАЗРЕШЕНИЯ").assertExists()
    }
}
