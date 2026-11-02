package com.kllxx.memorylane.feature.achievements.impl.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.achievements.impl.AchievementsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Achievements screen in English: the decimal point, the units and the empty state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class AchievementsContentEnglishTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: AchievementsState) {
        composeRule.setContent {
            MemoryLaneTheme(themeMode = ThemeMode.Light) {
                AchievementsContent(state = state, onIntent = {})
            }
        }
    }

    @Test
    fun `the freed space has a decimal point and the English unit`() {
        setContent(AchievementsState.Stats(7, 21, FormattedSize("2.4", SizeUnit.GB), "1,248", "312"))

        composeRule.onNodeWithTag(FREED_SPACE_TAG).assertTextEquals("2.4")
        composeRule.onNodeWithTag(FREED_UNIT_TAG).assertTextEquals("GB")
        composeRule.onNodeWithText("Space freed").assertExists()
    }

    @Test
    fun `the empty state is in English`() {
        setContent(AchievementsState.Empty)

        composeRule.onNodeWithText("Your stats will show up here").assertExists()
        composeRule.onNodeWithText("Go to calendar").assertExists()
    }
}
