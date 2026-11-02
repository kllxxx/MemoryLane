package com.kllxx.memorylane.feature.swipe.impl.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The message that a decision could not be saved: in both languages, once, and the flag goes after it. */
@RunWith(RobolectricTestRunner::class)
class SaveFailedSnackbarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var shown = 0

    private fun show(saveFailed: Boolean) {
        composeRule.setContent {
            MemoryLaneTheme { SaveFailedSnackbar(saveFailed = saveFailed, onShown = { shown++ }) {} }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English message`() {
        show(saveFailed = true)

        composeRule.onNodeWithText("Couldn't save your choice. We'll try again.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the Russian message`() {
        show(saveFailed = true)

        composeRule.onNodeWithText("Не удалось сохранить решение. Попробуем ещё раз.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `nothing is shown without the flag`() {
        show(saveFailed = false)

        composeRule.onNodeWithText("Couldn't save your choice. We'll try again.").assertDoesNotExist()
        assertEquals(0, shown)
    }

    @Test
    @Config(qualifiers = "en")
    fun `the flag is cleared once the message is gone`() {
        show(saveFailed = true)

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()

        assertEquals(1, shown)
    }
}
