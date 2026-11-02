package com.kllxx.memorylane.feature.calendar.impl

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.calendar.impl.ui.CalendarContent
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Calendar tab in Russian: the month names come from the platform, the other texts from the resources. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class CalendarContentRussianTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2027, 2, 10)

    private fun baseState() = CalendarState(
        months = buildYearGrid(2027).withMemories(OnThisDayIndex(2027, emptyMap()), today),
        today = today,
        phase = CalendarPhase.Content,
        sortedDates = persistentSetOf(),
        currentStreak = 7,
        hasPermission = true,
    )

    private fun show(state: CalendarState) {
        composeRule.setContent {
            MemoryLaneTheme {
                CalendarContent(
                    state = state,
                    listState = rememberLazyListState(listIndexOfMonth(state.months, YearMonth.from(today))),
                    onIntent = {},
                )
            }
        }
    }

    @Test
    fun `the month, the streak and the weekday header are Russian`() {
        show(baseState())

        composeRule.onNodeWithText("ФЕВРАЛЬ 2027").assertIsDisplayed()
        composeRule.onNodeWithText("7 дней").assertExists()
    }

    @Test
    fun `without permission the prompt is Russian`() {
        show(baseState().copy(hasPermission = false))

        composeRule.onNodeWithText("Нужен доступ ко всем фото").assertExists()
        composeRule.onNodeWithText("Разрешить доступ").assertExists()
    }
}
