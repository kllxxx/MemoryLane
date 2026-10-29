package com.kllxx.memorylane.feature.calendar.impl

import com.kllxx.memorylane.feature.calendar.impl.ui.UNDATED_BANNER_COUNT_TAG
import com.kllxx.memorylane.feature.calendar.impl.ui.UNDATED_BANNER_TAG
import com.kllxx.memorylane.feature.calendar.impl.ui.UndatedBannerCard
import com.kllxx.memorylane.feature.calendar.impl.ui.bannerChevronTag
import com.kllxx.memorylane.feature.calendar.impl.ui.bannerOverlineTag
import com.kllxx.memorylane.feature.calendar.impl.ui.bannerTitleTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.calendar.impl.ui.TODAY_BANNER_COUNT_TAG
import com.kllxx.memorylane.feature.calendar.impl.ui.TODAY_BANNER_TAG
import com.kllxx.memorylane.feature.calendar.impl.ui.TodayBannerCard
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // --- the banner of the undated photos has no overline, and its title is on the line of the trailing block ---

    private fun showUndated(count: Int = 3) {
        rule.setContent { MemoryLaneTheme { UndatedBannerCard(count = count, onClick = {}) } }
    }

    private fun centerOf(tag: String): Float {
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
        return (bounds.top.value + bounds.bottom.value) / 2f
    }

    @Test
    fun `the undated banner has the title and the count and no overline`() {
        showUndated()

        rule.onNodeWithTag(UNDATED_BANNER_TAG).assertIsDisplayed()
        rule.onNodeWithTag(bannerTitleTag(UNDATED_BANNER_TAG), useUnmergedTree = true).assertTextEquals("Фото без даты")
        rule.onNodeWithTag(UNDATED_BANNER_COUNT_TAG, useUnmergedTree = true).assertTextEquals("3 фото")
        rule.onNodeWithTag(bannerOverlineTag(UNDATED_BANNER_TAG), useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("БЕЗ ДАТЫ", useUnmergedTree = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `the title of the undated banner is on the center line of the count and the chevron`() {
        showUndated()

        val title = centerOf(bannerTitleTag(UNDATED_BANNER_TAG))

        assertEquals(title, centerOf(UNDATED_BANNER_COUNT_TAG), 0.5f)
        assertEquals(title, centerOf(bannerChevronTag(UNDATED_BANNER_TAG)), 0.5f)
    }

    @Test
    fun `the title of the undated banner is on the center line of the card`() {
        showUndated()

        val card = rule.onNodeWithTag(UNDATED_BANNER_TAG).getBoundsInRoot()

        assertEquals((card.top.value + card.bottom.value) / 2f, centerOf(bannerTitleTag(UNDATED_BANNER_TAG)), 0.5f)
    }

    @Test
    fun `the banner of today still has both lines, the overline above the title`() {
        show(count = 5)

        rule.onNodeWithTag(bannerOverlineTag(TODAY_BANNER_TAG), useUnmergedTree = true).assertTextEquals("28 ОКТЯБРЯ")
        rule.onNodeWithTag(bannerTitleTag(TODAY_BANNER_TAG), useUnmergedTree = true).assertTextEquals("Сегодняшние фото")
        val overline = rule.onNodeWithTag(bannerOverlineTag(TODAY_BANNER_TAG), useUnmergedTree = true).getBoundsInRoot()
        val title = rule.onNodeWithTag(bannerTitleTag(TODAY_BANNER_TAG), useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the overline is above the title", overline.bottom.value <= title.top.value + 0.5f)
    }

    @Test
    fun `the two lines of the banner of today are as a block on the center line of the count`() {
        show(count = 5)

        val overline = rule.onNodeWithTag(bannerOverlineTag(TODAY_BANNER_TAG), useUnmergedTree = true).getBoundsInRoot()
        val title = rule.onNodeWithTag(bannerTitleTag(TODAY_BANNER_TAG), useUnmergedTree = true).getBoundsInRoot()
        val block = (overline.top.value + title.bottom.value) / 2f

        assertEquals(block, centerOf(TODAY_BANNER_COUNT_TAG), 0.5f)
    }

    @Test
    fun `the height of the banner of today is the same as before, 92dp with its margins`() {
        show(count = 5)

        val card = rule.onNodeWithTag(TODAY_BANNER_TAG).getBoundsInRoot()

        assertEquals(72f, card.bottom.value - card.top.value, 0.5f)
    }
}
