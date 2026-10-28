package com.kllxx.memorylane.feature.achievements.impl.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.achievements.impl.AchievementsIntent
import com.kllxx.memorylane.feature.achievements.impl.AchievementsState
import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AchievementsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<AchievementsIntent>()

    private fun setContent(state: AchievementsState) {
        composeRule.setContent {
            MemoryLaneTheme(themeMode = ThemeMode.Light) {
                AchievementsContent(state = state, onIntent = { intents += it })
            }
        }
    }

    @Test
    fun `the stats state shows the four values`() {
        setContent(AchievementsState.Stats(7, 21, FormattedSize("2,4", SizeUnit.GB), "1 248", "312"))

        composeRule.onNodeWithTag(CURRENT_STREAK_TAG).assertTextEquals("7")
        composeRule.onNodeWithTag(BEST_STREAK_TAG).assertTextEquals("21")
        composeRule.onNodeWithTag(FREED_SPACE_TAG).assertTextEquals("2,4")
        composeRule.onNodeWithTag(FREED_UNIT_TAG).assertTextEquals("ГБ")
        composeRule.onNodeWithTag(VIEWED_TAG).assertTextEquals("1 248")
        composeRule.onNodeWithTag(DELETED_TAG).assertTextEquals("312")
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the streak unit follows the Russian plural rules`() {
        fun stats(days: Int) = AchievementsState.Stats(days, days, FormattedSize("0", SizeUnit.MB), "1", "0")
        var state by mutableStateOf<AchievementsState>(stats(1))
        composeRule.setContent {
            MemoryLaneTheme(themeMode = ThemeMode.Light) { AchievementsContent(state = state, onIntent = {}) }
        }

        fun unitOf(days: Int): String {
            composeRule.runOnIdle { state = stats(days) }
            val node = composeRule.onNodeWithTag(streakUnitTag(CURRENT_STREAK_TAG))
            return node.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        }

        assertEquals("день", unitOf(1))
        assertEquals("дня", unitOf(2))
        assertEquals("дней", unitOf(5))
        assertEquals("дней", unitOf(11))
        assertEquals("день", unitOf(21))
        assertEquals("дня", unitOf(22))
    }

    @Test
    fun `the stats state has no empty state`() {
        setContent(AchievementsState.Stats(0, 0, FormattedSize("0", SizeUnit.MB), "1", "0"))

        composeRule.onNodeWithTag(FREED_UNIT_TAG).assertTextEquals("МБ")
        composeRule.onNodeWithTag(EMPTY_STATE_TAG).assertDoesNotExist()
    }

    @Test
    fun `the empty state button sends the calendar intent`() {
        setContent(AchievementsState.Empty)

        composeRule.onNodeWithTag(EMPTY_STATE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(GO_TO_CALENDAR_TAG).performClick()

        assertEquals(listOf<AchievementsIntent>(AchievementsIntent.GoToCalendarClicked), intents)
    }

    @Test
    fun `the empty state shows no stats`() {
        setContent(AchievementsState.Empty)

        composeRule.onNodeWithTag(VIEWED_TAG).assertDoesNotExist()
    }

    @Test
    fun `the loading state shows neither the stats nor the empty state`() {
        setContent(AchievementsState.Loading)

        composeRule.onNodeWithTag(VIEWED_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(EMPTY_STATE_TAG).assertDoesNotExist()
    }
}
