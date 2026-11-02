package com.kllxx.memorylane.feature.swipe.impl.result.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.result.ResultState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The result screen after a day, in English. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
class ResultContentEnglishTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = ResultState(
        deck = DeckKey.Day(LocalDate.of(2026, 10, 28)),
        deletedCount = 11,
        keptCount = 37,
        totalCount = 48,
        freed = FormattedSize("2.5", SizeUnit.GB),
        streakDays = 8,
    )

    private fun show(state: ResultState) {
        composeRule.setContent { MemoryLaneTheme { ResultContent(state = state, onIntent = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `the freed space has a point, a unit and an English caption`() {
        show(state)

        composeRule.onNodeWithTag(ResultTags.OVERLINE).assertTextEquals("FREED")
        composeRule.onNodeWithTag(ResultTags.VALUE).assertTextEquals("2.5")
        composeRule.onNodeWithTag(ResultTags.UNIT).assertTextEquals("GB")
        composeRule.onNodeWithTag(ResultTags.CAPTION).assertTextEquals("28 October · 48 photos")
        composeRule.onNodeWithTag(ResultTags.DONE).assertTextEquals("Done")
    }

    @Test
    fun `the plurals of the caption and of the streak follow English`() {
        show(state.copy(totalCount = 1, streakDays = 1))

        composeRule.onNodeWithTag(ResultTags.CAPTION).assertTextEquals("28 October · 1 photo")
        composeRule.onNodeWithText("Current streak: 1 day").assertExists()
    }

    @Test
    fun `an undated deck is captioned Undated`() {
        show(state.copy(deck = DeckKey.Undated, totalCount = 3))

        composeRule.onNodeWithTag(ResultTags.CAPTION).assertTextEquals("Undated · 3 photos")
    }
}
