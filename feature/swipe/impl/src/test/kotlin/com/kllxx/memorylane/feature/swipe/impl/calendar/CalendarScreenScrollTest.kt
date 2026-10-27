package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.DayCover
import com.kllxx.memorylane.core.photos.domain.model.DayMemories
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The screen is composed, left and composed again with the same ViewModel, as happens when a day is opened and the
 * user comes back: the list must be where it was left, not on the current month.
 */
@RunWith(RobolectricTestRunner::class)
class CalendarScreenScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val uri = Uri.parse("content://media/external/images/media/1")
    private val reselected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun viewModel(): CalendarViewModel {
        val repository = mockk<PhotoRepository>()
        every { repository.hasMediaPermission() } returns true
        every { repository.onThisDayIndex } returns MutableStateFlow(
            OnThisDayIndex(
                2026,
                mapOf(MonthDay.of(3, 9) to DayMemories(1, DayCover(1, uri, LocalDate.of(2020, 3, 9)))),
            ),
        )
        coEvery { repository.refreshOnThisDayIndex() } returns Unit
        val swipeRepository = mockk<SwipeRepository>()
        every { swipeRepository.getCompletedSessions() } returns flowOf(emptyList())
        return CalendarViewModel(repository, swipeRepository, clock)
    }

    private fun show(viewModel: CalendarViewModel, visible: () -> Boolean = { true }) {
        composeRule.setContent {
            MemoryLaneTheme {
                if (visible()) {
                    CalendarScreen(onNavigateToSwipe = {}, reselected = reselected, viewModel = viewModel)
                }
            }
        }
    }

    private fun scrollToJanuary() {
        // Item 0 is the weekday header, item 1 the January separator
        composeRule.onNode(hasScrollAction()).performScrollToIndex(1)
        composeRule.waitForIdle()
    }

    @Test
    fun `the first display opens on the current month`() {
        show(viewModel())

        composeRule.onNodeWithText("OCTOBER 2026").assertIsDisplayed()
        composeRule.onNodeWithText("JANUARY 2026").assertDoesNotExist()
    }

    @Test
    fun `coming back keeps the position instead of jumping to the current month`() {
        val viewModel = viewModel()
        var visible by mutableStateOf(true)
        show(viewModel) { visible }
        scrollToJanuary()
        composeRule.onNodeWithText("JANUARY 2026").assertIsDisplayed()

        // A day is opened: the screen leaves the composition, then it returns
        composeRule.runOnIdle { visible = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("JANUARY 2026").assertIsDisplayed()
        composeRule.onNodeWithText("OCTOBER 2026").assertDoesNotExist()
    }

    @Test
    fun `the exact offset is kept too`() {
        val viewModel = viewModel()
        var visible by mutableStateOf(true)
        show(viewModel) { visible }
        scrollToJanuary()
        val left = viewModel.scrollAnchor

        composeRule.runOnIdle { visible = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible = true }
        composeRule.waitForIdle()

        assertEquals(left, viewModel.scrollAnchor)
    }

    @Test
    fun `selecting the Calendar tab again scrolls to the current month`() {
        show(viewModel())
        scrollToJanuary()
        composeRule.onNodeWithText("OCTOBER 2026").assertDoesNotExist()

        composeRule.runOnIdle { reselected.tryEmit(Unit) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("OCTOBER 2026").assertIsDisplayed()
    }
}
