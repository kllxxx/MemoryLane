package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.CalendarContent
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.WEEKDAY_HEADER_TAG
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.dayTestTag
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.sortedTestTag
import java.time.LocalDate
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
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

    // Scrolling data requests are sent on display; the tests care about user actions
    private val intents get() = sent.filterNot { it is CalendarIntent.LoadRange }

    // February 2027 starts on a Monday and has four full weeks
    private val today = LocalDate.of(2027, 2, 10)
    private fun day(d: Int) = LocalDate.of(2027, 2, d)

    private fun photoDay(d: Int, count: Int) = DayItem(
        day(d),
        DayLoadState.Loaded(Photo(d.toLong(), Uri.parse("content://media/external/images/media/$d"), day(d), 10), count),
    )

    private val items = (1..28).map { d ->
        when (d) {
            3 -> photoDay(3, count = 5)
            10 -> photoDay(10, count = 2)
            // Not yet happened, even though earlier years have photos for it
            20 -> photoDay(20, count = 7)
            4 -> DayItem(day(4), DayLoadState.Empty)
            else -> DayItem(day(d), DayLoadState.NotLoaded)
        }
    }

    private fun show(state: CalendarState = baseState()) {
        composeRule.setContent {
            MemoryLaneTheme {
                CalendarContent(state = state, onIntent = { sent += it })
            }
        }
    }

    private fun baseState() = CalendarState(
        days = items.toImmutableList(),
        today = today,
        sortedDates = persistentSetOf(day(3)),
        currentStreak = 7,
        hasPermission = true,
    )

    @Test
    fun `shows the month title, weekday header and streak`() {
        show()
        composeRule.onNodeWithText("FEBRUARY 2027").assertIsDisplayed()
        composeRule.onNodeWithTag(WEEKDAY_HEADER_TAG).onChildren().assertCountEquals(7)
        composeRule.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(R.string.calendar_streak_icon)).assertExists()
    }

    @Test
    fun `a day with photos shows its count and opens the day on tap`() {
        show()
        composeRule.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(R.string.calendar_photo_count, 5)).assertIsDisplayed()
        composeRule.onNodeWithTag(dayTestTag(items[2])).performClick()
        assertEquals(listOf<CalendarIntent>(CalendarIntent.OpenDay(day(3))), intents)
    }

    @Test
    fun `empty, not loaded and future days do not react to taps`() {
        show()
        listOf(4, 5, 20).forEach { composeRule.onNodeWithTag(dayTestTag(items[it - 1])).performClick() }
        assertTrue(intents.none { it is CalendarIntent.OpenDay })
    }

    @Test
    fun `only the sorted day shows the dot`() {
        show()
        composeRule.onNodeWithTag(sortedTestTag(items[2])).assertExists()
        composeRule.onNodeWithTag(sortedTestTag(items[9])).assertDoesNotExist()
    }

    @Test
    fun `without permission a prompt replaces the grid`() {
        show(baseState().copy(hasPermission = false))
        composeRule.onNodeWithTag(WEEKDAY_HEADER_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.calendar_permission_button)).performClick()
        assertEquals(listOf<CalendarIntent>(CalendarIntent.RequestPermission), intents)
    }

    @Test
    fun `a load error offers a retry`() {
        show(baseState().copy(error = CalendarError.LoadFailed))
        composeRule.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.calendar_error_retry)).performClick()
        assertEquals(listOf<CalendarIntent>(CalendarIntent.LoadDays), intents)
    }

    @Test
    fun `the visible days are requested`() {
        show()
        composeRule.waitForIdle()
        assertTrue(sent.any { it is CalendarIntent.LoadRange })
    }
}
