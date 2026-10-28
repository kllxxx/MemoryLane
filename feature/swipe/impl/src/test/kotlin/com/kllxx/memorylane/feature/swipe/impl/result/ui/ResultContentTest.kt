package com.kllxx.memorylane.feature.swipe.impl.result.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.result.ResultIntent
import com.kllxx.memorylane.feature.swipe.impl.result.ResultState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-rRU-w360dp-h800dp-xxhdpi")
class ResultContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<ResultIntent>()

    private val freedState = ResultState(
        date = LocalDate.of(2026, 10, 28),
        deletedCount = 11,
        keptCount = 37,
        totalCount = 48,
        freed = FormattedSize("320", SizeUnit.MB),
        streakDays = 8,
    )

    private fun show(state: ResultState) {
        composeRule.setContent {
            MemoryLaneTheme { ResultContent(state = state, onIntent = { intents += it }) }
        }
        composeRule.waitForIdle()
    }

    private fun text(tag: String) = composeRule.onNodeWithTag(tag)

    // --- what was freed ---

    @Test
    fun `the freed space is a number and a unit in separate texts under ОСВОБОЖДЕНО`() {
        show(freedState)

        text(ResultTags.OVERLINE).assertTextEquals("ОСВОБОЖДЕНО")
        text(ResultTags.VALUE).assertTextEquals("320")
        text(ResultTags.UNIT).assertTextEquals("МБ")
    }

    @Test
    fun `a size in gigabytes has its own unit`() {
        show(freedState.copy(freed = FormattedSize("2,5", SizeUnit.GB)))

        text(ResultTags.VALUE).assertTextEquals("2,5")
        text(ResultTags.UNIT).assertTextEquals("ГБ")
    }

    @Test
    fun `the caption has the day and the number of photos`() {
        show(freedState)

        text(ResultTags.CAPTION).assertTextEquals("28 октября · 48 фото")
    }

    @Test
    fun `the caption has the month in the genitive`() {
        show(freedState.copy(date = LocalDate.of(2026, 1, 8), totalCount = 1))

        text(ResultTags.CAPTION).assertTextEquals("8 января · 1 фото")
    }

    // --- the two cards ---

    @Test
    fun `the deleted card and the kept card show their counts`() {
        show(freedState)

        text(ResultTags.DELETED_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("11").assertIsDisplayed()
        composeRule.onNodeWithText("удалено").assertIsDisplayed()
        text(ResultTags.KEPT_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("37").assertIsDisplayed()
        composeRule.onNodeWithText("оставлено").assertIsDisplayed()
    }

    // --- the streak chip ---

    @Test
    fun `the chip shows the current streak with its plural`() {
        show(freedState.copy(streakDays = 8))

        text(ResultTags.STREAK).assertIsDisplayed()
        composeRule.onNodeWithText("Текущий стрик: 8 дней").assertIsDisplayed()
    }

    @Test
    fun `the plural of the chip follows the number`() {
        show(freedState.copy(streakDays = 1))
        composeRule.onNodeWithText("Текущий стрик: 1 день").assertIsDisplayed()
    }

    @Test
    fun `without a streak there is no chip`() {
        show(freedState.copy(streakDays = null))

        text(ResultTags.STREAK).assertDoesNotExist()
    }

    // --- nothing deleted ---

    @Test
    fun `when nothing was deleted the overline and the value say the day is sorted`() {
        show(freedState.copy(deletedCount = 0, freed = FormattedSize("0", SizeUnit.MB), keptCount = 48))

        text(ResultTags.OVERLINE).assertTextEquals("ДЕНЬ РАЗОБРАН")
        text(ResultTags.VALUE).assertTextEquals("Все фото оставлены")
        composeRule.onNodeWithTag(ResultTags.UNIT).assertDoesNotExist()
    }

    @Test
    fun `when nothing was deleted the deleted card is hidden and the kept card stays`() {
        show(freedState.copy(deletedCount = 0, freed = FormattedSize("0", SizeUnit.MB), keptCount = 48))

        composeRule.onNodeWithTag(ResultTags.DELETED_CARD).assertDoesNotExist()
        text(ResultTags.KEPT_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("48").assertIsDisplayed()
    }

    // --- the button ---

    @Test
    fun `the button is Готово and sends Done`() {
        show(freedState)

        text(ResultTags.DONE).assertTextEquals("Готово")
        text(ResultTags.DONE).performClick()

        assertEquals(listOf(ResultIntent.Done), intents)
    }
}
