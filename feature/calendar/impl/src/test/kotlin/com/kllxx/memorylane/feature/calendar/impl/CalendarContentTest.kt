package com.kllxx.memorylane.feature.calendar.impl

import android.net.Uri
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.photos.domain.model.DayCover
import com.kllxx.memorylane.core.photos.domain.model.DayMemories
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.calendar.impl.R
import com.kllxx.memorylane.feature.calendar.impl.ui.CalendarContent
import com.kllxx.memorylane.feature.calendar.impl.ui.EMPTY_STATE_TAG
import com.kllxx.memorylane.feature.calendar.impl.ui.WEEKDAY_HEADER_TAG
import com.kllxx.memorylane.feature.calendar.impl.ui.dayTestTag
import com.kllxx.memorylane.feature.calendar.impl.ui.sortedTestTag
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CalendarContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sent = mutableListOf<CalendarIntent>()
    private val intents get() = sent.toList()

    // February 2027 starts on a Monday and has four full weeks
    private val today = LocalDate.of(2027, 2, 10)
    private fun day(d: Int) = LocalDate.of(2027, 2, d)
    private fun item(d: Int) = DayItem(day(d))

    private val uri = Uri.parse("content://media/external/images/media/1")

    private fun memories(count: Int) = DayMemories(count, DayCover(1, uri, LocalDate.of(2020, 2, 1)))

    // Feb 20 has photos in earlier years but has not happened yet
    private val index = OnThisDayIndex(
        2027,
        mapOf(
            MonthDay.of(2, 3) to memories(5),
            MonthDay.of(2, 10) to memories(2),
            MonthDay.of(2, 20) to memories(7),
        ),
    )

    private fun show(state: CalendarState = baseState()) {
        composeRule.setContent {
            MemoryLaneTheme {
                CalendarContent(state = state, listState = rememberLazyListState(listIndexOfMonth(state.months, YearMonth.from(today))), onIntent = { sent += it })
            }
        }
    }

    private fun baseState() = CalendarState(
        months = buildYearGrid(2027).withMemories(index, today),
        today = today,
        phase = CalendarPhase.Content,
        sortedDates = persistentSetOf(day(3)),
        currentStreak = 7,
        hasPermission = true,
    )

    private fun string(id: Int, vararg args: Any) = RuntimeEnvironment.getApplication().getString(id, *args)

    @Test
    fun `shows the month title, weekday header and streak`() {
        show()
        composeRule.onNodeWithText("FEBRUARY 2027").assertIsDisplayed()
        composeRule.onNodeWithTag(WEEKDAY_HEADER_TAG).onChildren().assertCountEquals(7)
        composeRule.onNodeWithContentDescription(string(R.string.calendar_streak_icon)).assertExists()
    }

    @Test
    fun `a day with photos shows its count and opens the day on tap`() {
        show()
        composeRule.onNodeWithContentDescription(string(R.string.calendar_photo_count, 5)).assertIsDisplayed()
        composeRule.onNodeWithTag(dayTestTag(item(3))).performClick()
        assertEquals(listOf<CalendarIntent>(CalendarIntent.OpenDay(day(3))), intents)
    }

    @Test
    fun `days without photos and future days do not react to taps`() {
        show()
        listOf(4, 5, 20).forEach { composeRule.onNodeWithTag(dayTestTag(item(it))).performClick() }
        assertTrue(intents.none { it is CalendarIntent.OpenDay })
    }

    @Test
    fun `only the sorted day shows the dot`() {
        show()
        composeRule.onNodeWithTag(sortedTestTag(item(3))).assertExists()
        composeRule.onNodeWithTag(sortedTestTag(item(10))).assertDoesNotExist()
    }

    @Test
    fun `without permission a prompt replaces the grid`() {
        show(baseState().copy(hasPermission = false))
        composeRule.onNodeWithTag(WEEKDAY_HEADER_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.calendar_permission_button)).performClick()
        assertEquals(listOf<CalendarIntent>(CalendarIntent.RequestPermission), intents)
    }

    @Test
    fun `a load error offers a retry`() {
        show(baseState().copy(error = CalendarError.LoadFailed))
        composeRule.onNodeWithText(string(R.string.calendar_error_retry)).performClick()
        assertEquals(listOf<CalendarIntent>(CalendarIntent.LoadDays), intents)
    }

    @Test
    fun `while loading only the top bar is shown, no grid and no empty message`() {
        show(baseState().copy(phase = CalendarPhase.Loading))

        composeRule.onNodeWithContentDescription(string(R.string.calendar_streak_icon)).assertExists()
        composeRule.onNodeWithTag(WEEKDAY_HEADER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(EMPTY_STATE_TAG).assertDoesNotExist()
    }

    @Test
    fun `an empty gallery shows the message without a button and no grid`() {
        show(baseState().copy(phase = CalendarPhase.Empty))

        composeRule.onNodeWithTag(EMPTY_STATE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.calendar_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.calendar_empty_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(WEEKDAY_HEADER_TAG).assertDoesNotExist()
    }

    @Test
    fun `a list started on the current month shows it and not January`() {
        show()

        composeRule.onNodeWithText("FEBRUARY 2027").assertIsDisplayed()
        composeRule.onNodeWithText("JANUARY 2027").assertDoesNotExist()
    }
}
