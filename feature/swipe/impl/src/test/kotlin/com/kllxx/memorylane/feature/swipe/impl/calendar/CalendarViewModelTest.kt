package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.net.Uri
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DayCover
import com.kllxx.memorylane.core.photos.domain.model.DayMemories
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A photo repository that counts the gallery queries: a refresh is one query. */
private class FakePhotoRepository : PhotoRepository {
    var permission = true
    var cached: OnThisDayIndex? = null
    var next: OnThisDayIndex = OnThisDayIndex(2026, emptyMap())
    var failure: Exception? = null
    var queries = 0

    private val _index = MutableStateFlow<OnThisDayIndex?>(null)
    override val onThisDayIndex: StateFlow<OnThisDayIndex?> = _index


    fun publishCached() {
        _index.value = cached
    }

    override fun hasMediaPermission() = permission

    override suspend fun refreshOnThisDayIndex() {
        queries++
        failure?.let { throw it }
        _index.value = next
    }

    override suspend fun getPhotosForDate(date: LocalDate): List<Photo> = error("not used by the calendar")
    override suspend fun getPhotoLocation(photoId: Long): String? = null
    override suspend fun deletePhotos(photoIds: List<Long>): DeleteResult = DeleteResult.Deleted
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val uri = mockk<Uri>(relaxed = true)
    private lateinit var repository: FakePhotoRepository
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePhotoRepository()
        swipeRepository = mockk()
        every { swipeRepository.getCompletedSessions() } returns flowOf(emptyList())
        viewModel = createViewModel()
    }

    private fun createViewModel() = CalendarViewModel(repository, swipeRepository, clock)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun indexWith(vararg days: Pair<MonthDay, Int>) = OnThisDayIndex(
        currentYear = 2026,
        entries = days.associate { (day, count) -> day to DayMemories(count, DayCover(1, uri, LocalDate.of(2020, 1, 1))) },
    )

    private fun CalendarState.days() = months.flatMap { it.rows }.flatten().filterNotNull()

    @Test
    fun `before the photos are known the state is Loading with the full grid`() {
        val state = viewModel.state.value

        assertEquals(CalendarPhase.Loading, state.phase)
        assertEquals(12, state.months.size)
        assertEquals(365, state.days().size)
        assertTrue(state.days().all { it.memory == null })
    }

    @Test
    fun `the grid is there from the start and never depends on photos`() = runTest(dispatcher) {
        repository.next = indexWith(MonthDay.of(3, 9) to 2)

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertEquals(12, viewModel.state.value.months.size)
    }

    @Test
    fun `an empty index gives Empty`() = runTest(dispatcher) {
        repository.next = indexWith()

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertEquals(CalendarPhase.Empty, viewModel.state.value.phase)
    }

    @Test
    fun `an index with photos gives Content with all 12 months, also the ones without photos`() = runTest(dispatcher) {
        repository.next = indexWith(MonthDay.of(3, 9) to 4)

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(CalendarPhase.Content, state.phase)
        assertEquals(12, state.months.size)
        val withPhotos = state.days().filter { it.memory != null }
        assertEquals(listOf(LocalDate.of(2026, 3, 9)), withPhotos.map { it.date })
        assertEquals(4, withPhotos.single().memory?.count)
        // January has no photo at all and is still there
        assertTrue(state.months[0].rows.flatten().filterNotNull().all { it.memory == null })
    }

    @Test
    fun `the index reaches the state in one emission`() = runTest(dispatcher) {
        repository.next = indexWith(MonthDay.of(3, 9) to 4, MonthDay.of(5, 1) to 1, MonthDay.of(10, 3) to 2)

        viewModel.state.test {
            assertEquals(CalendarPhase.Loading, awaitItem().phase)
            viewModel.onIntent(CalendarIntent.ScreenEntered)
            advanceUntilIdle()

            // Straight from the empty grid to the grid with all its photos, nothing in between
            val loaded = awaitItem()
            assertEquals(CalendarPhase.Content, loaded.phase)
            assertEquals(3, loaded.days().count { it.memory != null })
            expectNoEvents()
        }
    }

    @Test
    fun `the cached index is shown without waiting for a refresh`() = runTest(dispatcher) {
        repository.cached = indexWith(MonthDay.of(3, 9) to 4)
        repository.publishCached()

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(CalendarPhase.Content, viewModel.state.value.phase)
        assertEquals(0, repository.queries)
    }

    @Test
    fun `creating the ViewModel queries nothing`() = runTest(dispatcher) {
        advanceUntilIdle()

        assertEquals(0, repository.queries)
    }

    @Test
    fun `entering the screen makes exactly one gallery query`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertEquals(1, repository.queries)
    }

    @Test
    fun `every entry of the screen reads the photos again`() = runTest(dispatcher) {
        repository.next = indexWith(MonthDay.of(3, 9) to 1)
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        // A photo was deleted meanwhile
        repository.next = indexWith(MonthDay.of(3, 9) to 3)
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertEquals(2, repository.queries)
        val march9 = viewModel.state.value.days().single { it.date == LocalDate.of(2026, 3, 9) }
        assertEquals(3, march9.memory?.count)
    }

    @Test
    fun `granting the permission loads the photos once`() = runTest(dispatcher) {
        repository.permission = false
        viewModel = createViewModel()
        assertFalse(viewModel.state.value.hasPermission)

        repository.permission = true
        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasPermission)
        assertEquals(1, repository.queries)
    }

    @Test
    fun `without permission nothing is queried and the state stays Loading`() = runTest(dispatcher) {
        repository.permission = false
        viewModel = createViewModel()

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        viewModel.onIntent(CalendarIntent.LoadDays)
        advanceUntilIdle()

        assertEquals(0, repository.queries)
        assertFalse(viewModel.state.value.hasPermission)
        assertEquals(CalendarPhase.Loading, viewModel.state.value.phase)
    }

    @Test
    fun `a permission revoked while away is noticed on entry`() = runTest(dispatcher) {
        assertTrue(viewModel.state.value.hasPermission)

        repository.permission = false
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasPermission)
        assertEquals(0, repository.queries)
    }

    @Test
    fun `a denied permission found by the query shows the prompt, not an empty calendar`() = runTest(dispatcher) {
        repository.failure = PermissionDeniedException("revoked")

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasPermission)
        assertEquals(CalendarPhase.Loading, viewModel.state.value.phase)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a failed load shows the grid with an error instead of a blank screen`() = runTest(dispatcher) {
        repository.failure = IllegalStateException("boom")

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(CalendarError.LoadFailed, state.error)
        assertEquals(CalendarPhase.Content, state.phase)
        assertEquals(12, state.months.size)
    }

    @Test
    fun `a retry that works clears the error`() = runTest(dispatcher) {
        repository.failure = IllegalStateException("boom")
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        repository.failure = null
        repository.next = indexWith(MonthDay.of(3, 9) to 1)
        viewModel.onIntent(CalendarIntent.LoadDays)
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(2, repository.queries)
        assertNotNull(viewModel.state.value.days().single { it.date == LocalDate.of(2026, 3, 9) }.memory)
    }

    @Test
    fun `RequestPermission emits launcher effect`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.RequestPermission)
            assertEquals(CalendarEffect.RequestPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `OpenDay emits NavigateToSwipe`() = runTest(dispatcher) {
        val date = LocalDate.of(2026, 3, 3)
        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.OpenDay(date))
            assertEquals(CalendarEffect.NavigateToSwipe(date), awaitItem())
        }
    }

    @Test
    fun `an effect sent before anyone collects is not lost`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.RequestPermission)
        viewModel.effects.test {
            assertEquals(CalendarEffect.RequestPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `OpenDay ignores a day in the future and opens today`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.OpenDay(today.plusDays(1)))
            expectNoEvents()
            viewModel.onIntent(CalendarIntent.OpenDay(today))
            assertEquals(CalendarEffect.NavigateToSwipe(today), awaitItem())
            viewModel.onIntent(CalendarIntent.OpenDay(today.minusDays(30)))
            assertEquals(CalendarEffect.NavigateToSwipe(today.minusDays(30)), awaitItem())
        }
    }

    @Test
    fun `today comes from the clock`() {
        assertEquals(today, viewModel.state.value.today)
    }

    @Test
    fun `completed sessions give the sorted dates and the streak`() = runTest(dispatcher) {
        val sessions = listOf(
            SwipeSession(LocalDate.of(2026, 3, 3), true, 0, completedAt = LocalDate.of(2026, 10, 15)),
            SwipeSession(LocalDate.of(2026, 5, 9), true, 0, completedAt = LocalDate.of(2026, 10, 14)),
            // Saved before completedAt was tracked: counts as sorted but not for the streak
            SwipeSession(LocalDate.of(2026, 7, 1), true, 0, completedAt = LocalDate.EPOCH),
        )
        every { swipeRepository.getCompletedSessions() } returns flowOf(sessions)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 5, 9), LocalDate.of(2026, 7, 1)), state.sortedDates)
        assertEquals(2, state.currentStreak)
    }

    @Test
    fun `a failing sessions flow leaves no sorted days`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flow { throw IllegalStateException("db") }

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sortedDates.isEmpty())
        assertEquals(0, viewModel.state.value.currentStreak)
    }
}
