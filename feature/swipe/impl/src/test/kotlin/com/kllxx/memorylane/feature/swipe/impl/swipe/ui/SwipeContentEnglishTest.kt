package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeIntent
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeReducer
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Swipe screen in English: the dates of the deck written for the language, the labels from the resources. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h780dp-xhdpi")
class SwipeContentEnglishTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: SwipeState) {
        val english = SwipeReducer.reduce(state, SwipeIntent.LocaleChanged(Locale.US))
        composeRule.setContent { MemoryLaneTheme { SwipeContent(state = english, onIntent = {}) } }
    }

    @Test
    fun `the date of the current photo is written in English`() {
        show(swipeState(count = 3, currentIndex = 1, location = "Paris, France"))

        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_DATE).assertTextEquals("7 January 2017, 21:15")
        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_LOCATION).assertTextEquals("Paris, France")
    }

    @Test
    fun `the undated deck is headed Undated`() {
        show(swipeState(count = 2).copy(deckKey = DeckKey.Undated))

        composeRule.onNodeWithTag(SwipeTags.TOP_BAR_DATE).assertTextEquals("Undated")
    }

    @Test
    fun `the strip and the actions have English descriptions`() {
        show(swipeState(count = 2, buttonsMode = true))

        composeRule.onNodeWithTag(SwipeTags.STRIP).assertContentDescriptionEquals("All photos of the day")
        composeRule.onNodeWithContentDescription("Photo 1 of 2, not sorted").assertExists()
        composeRule.onNodeWithContentDescription("Delete photo").assertExists()
        composeRule.onNodeWithContentDescription("Keep photo").assertExists()
        composeRule.onNodeWithContentDescription("Undo the last decision").assertExists()
        composeRule.onNodeWithContentDescription("Close").assertExists()
    }
}
