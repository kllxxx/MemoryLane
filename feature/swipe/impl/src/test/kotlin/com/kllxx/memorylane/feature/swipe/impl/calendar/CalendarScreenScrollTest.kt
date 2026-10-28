package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.Streaks
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.TODAY_BANNER_TAG
import java.time.LocalDateTime
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

    // The completed days: a new list makes the banner of today be worked out again
    private val sessions = MutableSharedFlow<List<SwipeSession>>(replay = 1).also { it.tryEmit(emptyList()) }
    private var decisions = emptyMap<Long, PhotoDecision>()

    private fun viewModel(withTodayPhotos: Boolean = false): CalendarViewModel {
        val repository = mockk<PhotoRepository>()
        every { repository.hasMediaPermission() } returns true
        val entries = buildMap {
            put(MonthDay.of(3, 9), DayMemories(1, DayCover(1, uri, LocalDate.of(2020, 3, 9))))
            if (withTodayPhotos) put(MonthDay.from(today), DayMemories(2, DayCover(2, uri, LocalDate.of(2020, 10, 15))))
        }
        every { repository.onThisDayIndex } returns MutableStateFlow(OnThisDayIndex(2026, entries))
        coEvery { repository.refreshOnThisDayIndex() } returns Unit
        coEvery { repository.getPhotosForDate(today) } returns listOf(
            Photo(10, uri, LocalDateTime.of(2020, 10, 15, 9, 0), sizeBytes = 1),
            Photo(11, uri, LocalDateTime.of(2021, 10, 15, 9, 0), sizeBytes = 1),
        )
        val swipeRepository = mockk<SwipeRepository>()
        every { swipeRepository.getCompletedSessions() } returns sessions
        coEvery { swipeRepository.getDecisionsByDate(today) } answers { decisions }
        val stats = mockk<StatsRepository>()
        every { stats.streaks } returns flowOf(Streaks.None)
        return CalendarViewModel(repository, swipeRepository, stats, clock)
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

    // --- the banner of today comes and goes ---

    private fun completeToday() {
        decisions = mapOf(10L to PhotoDecision.KEEP, 11L to PhotoDecision.KEEP)
        composeRule.runOnIdle { sessions.tryEmit(listOf(SwipeSession(today, true, 0, completedAt = today))) }
        composeRule.waitForIdle()
    }

    private fun uncompleteToday() {
        decisions = mapOf(10L to PhotoDecision.KEEP)
        composeRule.runOnIdle { sessions.tryEmit(emptyList()) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the banner is the first item of the list`() {
        show(viewModel(withTodayPhotos = true))

        composeRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TODAY_BANNER_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Сегодняшние фото").assertIsDisplayed()
    }

    @Test
    fun `the list opens on the current month also when the banner is there`() {
        show(viewModel(withTodayPhotos = true))

        composeRule.onNodeWithText("OCTOBER 2026").assertIsDisplayed()
        composeRule.onNodeWithTag(TODAY_BANNER_TAG).assertDoesNotExist()
    }

    @Test
    fun `the anchor is the same with the banner as without it`() {
        val withBanner = viewModel(withTodayPhotos = true)
        show(withBanner)

        // With the banner: the banner, the weekday header, then the January separator, which is item 1 of a list without it
        composeRule.onNode(hasScrollAction()).performScrollToIndex(2)
        composeRule.waitForIdle()

        assertEquals(1, withBanner.scrollAnchor.index)
    }

    @Test
    fun `the position stays when the banner goes away`() {
        val viewModel = viewModel(withTodayPhotos = true)
        show(viewModel)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("JANUARY 2026").assertIsDisplayed()
        val before = viewModel.scrollAnchor

        completeToday()

        composeRule.onNodeWithTag(TODAY_BANNER_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("JANUARY 2026").assertIsDisplayed()
        assertEquals(before, viewModel.scrollAnchor)
    }

    @Test
    fun `the position stays when the banner comes back`() {
        val viewModel = viewModel(withTodayPhotos = true)
        show(viewModel)
        completeToday()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("JANUARY 2026").assertIsDisplayed()
        val before = viewModel.scrollAnchor

        uncompleteToday()

        composeRule.onNodeWithText("JANUARY 2026").assertIsDisplayed()
        assertEquals(before, viewModel.scrollAnchor)
    }

    @Test
    fun `selecting the tab again goes to the current month with the banner there`() {
        show(viewModel(withTodayPhotos = true))
        composeRule.onNode(hasScrollAction()).performScrollToIndex(2)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("OCTOBER 2026").assertDoesNotExist()

        composeRule.runOnIdle { reselected.tryEmit(Unit) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("OCTOBER 2026").assertIsDisplayed()
    }

    @Test
    fun `coming back with the banner there keeps the position`() {
        val viewModel = viewModel(withTodayPhotos = true)
        var visible by mutableStateOf(true)
        show(viewModel) { visible }
        composeRule.onNode(hasScrollAction()).performScrollToIndex(2)
        composeRule.waitForIdle()

        composeRule.runOnIdle { visible = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("JANUARY 2026").assertIsDisplayed()
    }
}
