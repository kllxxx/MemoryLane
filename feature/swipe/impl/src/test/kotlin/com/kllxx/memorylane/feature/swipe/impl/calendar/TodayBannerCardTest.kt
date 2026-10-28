package com.kllxx.memorylane.feature.swipe.impl.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.TODAY_BANNER_COUNT_TAG
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.TODAY_BANNER_TAG
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.TodayBannerCard
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-rRU-w360dp-h800dp-xxhdpi")
class TodayBannerCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val date = LocalDate.of(2026, 10, 28)

    private fun show(count: Int, onClick: () -> Unit = {}) {
        rule.setContent {
            MemoryLaneTheme { TodayBannerCard(date = date, count = count, onClick = onClick) }
        }
    }

    @Test
    fun `shows the date, the title and the count`() {
        show(count = 5)

        rule.onNodeWithTag(TODAY_BANNER_TAG).assertIsDisplayed()
        rule.onNodeWithText("28 ОКТЯБРЯ", substring = true, ignoreCase = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Сегодняшние фото", substring = true, ignoreCase = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(TODAY_BANNER_COUNT_TAG, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("5 фото", substring = true, ignoreCase = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a single photo is counted too`() {
        show(count = 1)

        rule.onNodeWithText("1 фото", substring = true, ignoreCase = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a tap on the banner is reported once`() {
        var taps = 0
        show(count = 3, onClick = { taps++ })

        rule.onNodeWithTag(TODAY_BANNER_TAG).performClick()

        assertEquals(1, taps)
    }
}
