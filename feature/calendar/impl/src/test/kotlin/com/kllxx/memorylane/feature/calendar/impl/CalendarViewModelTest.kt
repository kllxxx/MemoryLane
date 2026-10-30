package com.kllxx.memorylane.feature.calendar.impl

import com.kllxx.memorylane.core.domain.model.DeckKey
import android.net.Uri
import app.cash.turbine.test
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.streak.Streaks
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DayCover
import com.kllxx.memorylane.core.photos.domain.model.DayMemories
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.model.PhotoLocation
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.domain.usecase.AdoptDatedDecisions
import com.kllxx.memorylane.core.photos.domain.usecase.PhotosToReview
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    var todayPhotos: List<Photo> = emptyList()
    var todayFailure: Exception? = null
    val photosDatesQueried = mutableListOf<LocalDate>()

    override suspend fun getPhotosForDate(date: LocalDate): List<Photo> {
        todayFailure?.let { throw it }
        photosDatesQueried += date
        return todayPhotos
    }
    override suspend fun getUndatedPhotos(): List<Photo> = emptyList()

    /** The days of the photos that have one; a test sets it. */
    var photoDates: Map<Long, LocalDate> = emptyMap()
    val datesAsked = mutableListOf<Collection<Long>>()

    override suspend fun getPhotoDates(photoIds: Collection<Long>): Map<Long, LocalDate> {
        datesAsked += photoIds
        return photoDates.filterKeys { it in photoIds }
    }
    override suspend fun getTrashedPhotos(ids: Collection<Long>): List<Photo> = emptyList()
    override fun resolveLocations(photos: List<Photo>): Flow<PhotoLocation> = emptyFlow()
    override suspend fun getPhotoSizes(photoIds: Collection<Long>): Map<Long, Long> = emptyMap()
    override suspend fun deletePhotos(photoIds: List<Long>): DeleteResult = DeleteResult.Deleted
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = MutableClock(today.atStartOfDay().toInstant(ZoneOffset.UTC))
    private val todayProvider = ClockTodayProvider(clock)
    private val uri = mockk<Uri>(relaxed = true)
    private lateinit var repository: FakePhotoRepository
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var statsRepository: StatsRepository
    private lateinit var viewModel: CalendarViewModel

    // What the completed days and the stored decisions of today are; a test changes them and emits the sessions again
    private val sessions = MutableSharedFlow<List<SwipeSession>>(replay = 1)
    private val streaks = MutableStateFlow(Streaks.None)
    private var decisions = emptyMap<Long, PhotoDecision>()
    private var undatedDecisions = emptyMap<Long, PhotoDecision>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePhotoRepository()
        swipeRepository = mockk()
        statsRepository = mockk()
        sessions.tryEmit(emptyList())
        every { swipeRepository.getCompletedSessions() } returns sessions
        coEvery { swipeRepository.getDecisions(any()) } answers {
            if (firstArg<DeckKey>() == DeckKey.Undated) undatedDecisions else decisions
        }
        coEvery { swipeRepository.adoptUndatedDecisions(any(), any()) } returns Unit
        every { statsRepository.streaks } returns streaks
        viewModel = createViewModel()
    }

    private fun createViewModel() = CalendarViewModel(repository, swipeRepository, statsRepository, PhotosToReview(repository, swipeRepository), AdoptDatedDecisions(repository, swipeRepository), todayProvider)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun indexWith(vararg days: Pair<MonthDay, Int>) = OnThisDayIndex(
        currentYear = 2026,
        entries = days.associate { (day, count) -> day to DayMemories(count, DayCover(1, uri, LocalDate.of(2020, 1, 1))) },
    )

    private fun CalendarState.days() = months.flatMap { it.rows }.flatten().filterNotNull()

    // --- the banner of the photos without a date ---

    private fun indexWithUndated(vararg ids: Long) = OnThisDayIndex(2026, emptyMap(), undatedIds = ids.toSet())

    private suspend fun TestScope.showIndex(index: OnThisDayIndex) {
        repository.next = index
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()
    }

    @Test
    fun `no undated photos, no banner`() = runTest(dispatcher) {
        showIndex(indexWith(MonthDay.of(3, 9) to 2))

        assertNull(viewModel.state.value.undatedBanner)
    }

    @Test
    fun `the banner counts the undated photos without a decision`() = runTest(dispatcher) {
        showIndex(indexWithUndated(1, 2, 3))

        assertEquals(UndatedBanner(3), viewModel.state.value.undatedBanner)
    }

    @Test
    fun `decided photos are not counted, a mark for deletion is a decision`() = runTest(dispatcher) {
        undatedDecisions = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)

        showIndex(indexWithUndated(1, 2, 3))

        assertEquals(UndatedBanner(1), viewModel.state.value.undatedBanner)
    }

    @Test
    fun `the banner is gone when every undated photo has a decision`() = runTest(dispatcher) {
        undatedDecisions = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)

        showIndex(indexWithUndated(1, 2))

        assertNull(viewModel.state.value.undatedBanner)
    }

    @Test
    fun `decisions of photos that are not undated any more do not take from the count`() = runTest(dispatcher) {
        undatedDecisions = mapOf(9L to PhotoDecision.KEEP, 10L to PhotoDecision.TRASHED)

        showIndex(indexWithUndated(1, 2))

        assertEquals(UndatedBanner(2), viewModel.state.value.undatedBanner)
    }

    @Test
    fun `coming back to the screen counts again after the deck was sorted`() = runTest(dispatcher) {
        showIndex(indexWithUndated(1, 2, 3))
        undatedDecisions = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertEquals(UndatedBanner(1), viewModel.state.value.undatedBanner)
    }

    @Test
    fun `a photo that got a date leaves the count with the next index`() = runTest(dispatcher) {
        showIndex(indexWithUndated(1, 2, 3))

        showIndex(indexWithUndated(1, 3))

        assertEquals(UndatedBanner(2), viewModel.state.value.undatedBanner)
    }

    @Test
    fun `no permission, no banner`() = runTest(dispatcher) {
        showIndex(indexWithUndated(1, 2))
        repository.permission = false

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertNull(viewModel.state.value.undatedBanner)
    }

    @Test
    fun `a failure to read the decisions hides the banner and does not crash`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisions(DeckKey.Undated) } throws IllegalStateException("db")

        showIndex(indexWithUndated(1, 2))

        assertNull(viewModel.state.value.undatedBanner)
    }

    @Test
    fun `tapping the banner opens the undated deck`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.OpenUndated)

            assertEquals(CalendarEffect.NavigateToUndated, awaitItem())
        }
    }

    @Test
    fun `the banner does not move the anchor or the current month index`() = runTest(dispatcher) {
        showIndex(indexWith(MonthDay.of(3, 9) to 2))
        viewModel.onScrolled(index = 5, offset = 20)
        val currentMonth = viewModel.currentMonthIndex

        showIndex(indexWithUndated(1, 2, 3))

        assertEquals(ScrollAnchor(5, 20), viewModel.scrollAnchor)
        assertEquals(currentMonth, viewModel.currentMonthIndex)
        assertEquals(12, viewModel.state.value.months.size)
    }

    @Test
    fun `the banner of today and the banner of the undated photos are independent`() = runTest(dispatcher) {
        repository.todayPhotos = listOf(Photo(10, uri, LocalDateTime.of(2020, 10, 15, 9, 0), sizeBytes = 1))

        showIndex(indexWithUndated(1, 2))

        assertEquals(TodayBanner(1), viewModel.state.value.todayBanner)
        assertEquals(UndatedBanner(2), viewModel.state.value.undatedBanner)
    }


    // --- the day changes while the screen is open ---

    private fun TestScope.passMidnight(to: LocalDate) {
        clock.set(to.atStartOfDay().toInstant(ZoneOffset.UTC))
        todayProvider.tick()
        advanceUntilIdle()
    }

    @Test
    fun `after midnight today, the highlight and the banner move to the new day`() = runTest(dispatcher) {
        repository.todayPhotos = listOf(Photo(10, uri, LocalDateTime.of(2020, 10, 15, 9, 0), sizeBytes = 1))
        showIndex(indexWith(MonthDay.of(10, 16) to 2))
        assertEquals(TodayBanner(1), viewModel.state.value.todayBanner)
        // A day that has not happened yet shows no photos
        assertNull(viewModel.state.value.days().first { it.date == today.plusDays(1) }.memory)

        passMidnight(today.plusDays(1))

        assertEquals(today.plusDays(1), viewModel.state.value.today)
        assertEquals(today.plusDays(1), repository.photosDatesQueried.last())
        assertNotNull(viewModel.state.value.days().first { it.date == today.plusDays(1) }.memory)
    }

    @Test
    fun `after midnight a day is opened by the new today, not the old one`() = runTest(dispatcher) {
        passMidnight(today.plusDays(1))

        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.OpenDay(today.plusDays(1)))
            assertEquals(CalendarEffect.NavigateToSwipe(today.plusDays(1)), awaitItem())
            viewModel.onIntent(CalendarIntent.OpenDay(today.plusDays(2)))
            expectNoEvents()
        }
    }

    @Test
    fun `a plain day change keeps where the user scrolled`() = runTest(dispatcher) {
        viewModel.onScrolled(index = 5, offset = 20)

        passMidnight(today.plusDays(1))

        assertEquals(ScrollAnchor(5, 20), viewModel.scrollAnchor)
    }

    @Test
    fun `new year builds the grid of the new year and goes to its current month`() = runTest(dispatcher) {
        val newYearsEve = LocalDate.of(2026, 12, 31)
        clock.set(newYearsEve.atStartOfDay().toInstant(ZoneOffset.UTC))
        todayProvider.tick()
        viewModel = createViewModel()
        showIndex(indexWith(MonthDay.of(1, 1) to 3))
        assertEquals(2026, viewModel.state.value.months.first().yearMonth.year)
        viewModel.onScrolled(index = 40, offset = 0)

        passMidnight(LocalDate.of(2027, 1, 1))

        val state = viewModel.state.value
        assertEquals(LocalDate.of(2027, 1, 1), state.today)
        assertTrue(state.months.all { it.yearMonth.year == 2027 })
        assertEquals(12, state.months.size)
        // The memories of the index are on the new grid, and January 1st has already happened
        assertEquals(3, state.days().first { it.date == LocalDate.of(2027, 1, 1) }.memory?.count)
        assertEquals(ScrollAnchor(viewModel.currentMonthIndex, 0), viewModel.scrollAnchor)
        assertEquals(LocalDate.of(2027, 1, 1), repository.photosDatesQueried.last())
    }
    // --- photos that got a date take their decisions to their day ---

    @Test
    fun `an undated photo marked for deletion that got a date moves to its day when the index says so`() = runTest(dispatcher) {
        undatedDecisions = mapOf(1L to PhotoDecision.DELETE, 2L to PhotoDecision.KEEP)
        showIndex(indexWithUndated(1, 2))
        // The banner of today moves nothing of its own: an empty list is its call
        coVerify(exactly = 0) { swipeRepository.adoptUndatedDecisions(match { it.isNotEmpty() }, any()) }

        // Photo 1 got a date: the next index does not have it among the undated photos
        repository.photoDates = mapOf(1L to LocalDate.of(2021, 6, 22))
        showIndex(indexWithUndated(2))

        coVerify(exactly = 1) { swipeRepository.adoptUndatedDecisions(listOf(1L), LocalDate.of(2021, 6, 22)) }
    }

    @Test
    fun `photos that are still undated are not asked about`() = runTest(dispatcher) {
        undatedDecisions = mapOf(1L to PhotoDecision.DELETE)

        showIndex(indexWithUndated(1, 2))

        assertTrue(repository.datesAsked.isEmpty())
    }

    @Test
    fun `a photo that is gone is not moved anywhere`() = runTest(dispatcher) {
        undatedDecisions = mapOf(1L to PhotoDecision.DELETE)
        repository.photoDates = emptyMap()

        showIndex(indexWithUndated(2))

        // The banner of today moves nothing of its own: an empty list is its call
        coVerify(exactly = 0) { swipeRepository.adoptUndatedDecisions(match { it.isNotEmpty() }, any()) }
    }

    @Test
    fun `the decisions are moved at start too, for a photo that got a date while the app was closed`() = runTest(dispatcher) {
        undatedDecisions = mapOf(1L to PhotoDecision.DELETE)
        repository.photoDates = mapOf(1L to LocalDate.of(2021, 6, 22))

        showIndex(indexWithUndated(2))

        coVerify(exactly = 1) { swipeRepository.adoptUndatedDecisions(listOf(1L), LocalDate.of(2021, 6, 22)) }
    }

    @Test
    fun `a failure to move the decisions does not break the screen`() = runTest(dispatcher) {
        undatedDecisions = mapOf(1L to PhotoDecision.DELETE)
        repository.photoDates = mapOf(1L to LocalDate.of(2021, 6, 22))
        coEvery { swipeRepository.adoptUndatedDecisions(any(), any()) } throws IllegalStateException("db")

        showIndex(indexWithUndated(2))

        assertEquals(CalendarPhase.Empty, viewModel.state.value.phase)
        assertEquals(UndatedBanner(1), viewModel.state.value.undatedBanner)
    }

    // --- scroll position ---

    @Test
    fun `the list starts on the current month`() {
        val expected = listIndexOfMonth(viewModel.state.value.months, YearMonth.of(2026, 10))

        assertEquals(ScrollAnchor(expected, offset = 0), viewModel.scrollAnchor)
        assertEquals(expected, viewModel.currentMonthIndex)
    }

    @Test
    fun `the anchor follows the scrolling and is what a new list starts from`() {
        viewModel.onScrolled(index = 1, offset = 40)

        assertEquals(ScrollAnchor(1, 40), viewModel.scrollAnchor)
    }

    @Test
    fun `entering the screen again does not move the anchor back to the current month`() = runTest(dispatcher) {
        repository.next = indexWith(MonthDay.of(3, 9) to 2)
        viewModel.onScrolled(index = 1, offset = 40)

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertEquals(ScrollAnchor(1, 40), viewModel.scrollAnchor)
        assertTrue(viewModel.currentMonthIndex != 1)
    }

    @Test
    fun `scrolling does not emit a state`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem()

            viewModel.onScrolled(index = 5, offset = 0)
            viewModel.onScrolled(index = 6, offset = 12)

            expectNoEvents()
        }
    }

    @Test
    fun `a new index keeps the anchor`() = runTest(dispatcher) {
        viewModel.onScrolled(index = 1, offset = 40)
        repository.next = indexWith(MonthDay.of(3, 9) to 2)

        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()

        assertEquals(CalendarPhase.Content, viewModel.state.value.phase)
        assertEquals(ScrollAnchor(1, 40), viewModel.scrollAnchor)
    }

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
    fun `completed sessions give the sorted dates, the streak is the one of the stats`() = runTest(dispatcher) {
        sessions.tryEmit(
            listOf(
                SwipeSession(LocalDate.of(2026, 3, 3), true, 0, completedAt = LocalDate.of(2026, 10, 15)),
                SwipeSession(LocalDate.of(2026, 5, 9), true, 0, completedAt = LocalDate.of(2026, 10, 14)),
                // Saved before completedAt was tracked: still a sorted day
                SwipeSession(LocalDate.of(2026, 7, 1), true, 0, completedAt = LocalDate.EPOCH),
            ),
        )
        streaks.value = Streaks(current = 2, best = 5)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 5, 9), LocalDate.of(2026, 7, 1)), state.sortedDates)
        assertEquals(2, state.currentStreak)
    }

    @Test
    fun `the streak follows the stats`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.currentStreak)

        streaks.value = Streaks(current = 4, best = 4)
        advanceUntilIdle()

        assertEquals(4, viewModel.state.value.currentStreak)
    }

    @Test
    fun `a failing sessions flow leaves no sorted days`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flow { throw IllegalStateException("db") }

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sortedDates.isEmpty())
        assertEquals(0, viewModel.state.value.currentStreak)
    }

    @Test
    fun `a failing streak leaves the calendar as it is`() = runTest(dispatcher) {
        every { statsRepository.streaks } returns flow { throw IllegalStateException("db") }

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.currentStreak)
    }

    // --- the banner of today ---

    private fun photo(id: Long) = Photo(id, uri, LocalDateTime.of(2020, 10, 15, 9, id.toInt()), sizeBytes = 1)

    private val todayDay = MonthDay.from(today)

    /** Today has [count] photos in the index and in the library, and they are [decided] by the stored decisions. */
    private fun givenToday(count: Int, decided: Map<Long, PhotoDecision> = emptyMap(), indexCount: Int = count) {
        repository.todayPhotos = (1L..count).map(::photo)
        repository.next = indexWith(todayDay to indexCount)
        decisions = decided
    }

    private fun TestScope.enter() {
        viewModel.onIntent(CalendarIntent.ScreenEntered)
        advanceUntilIdle()
    }

    private val banner get() = viewModel.state.value.todayBanner

    @Test
    fun `today with photos that are not decided has the banner, with the count of the photos`() = runTest(dispatcher) {
        givenToday(count = 3)

        enter()

        assertEquals(TodayBanner(3), banner)
    }

    @Test
    fun `there is no banner before the photos are known`() = runTest(dispatcher) {
        advanceUntilIdle()

        assertNull(banner)
    }

    @Test
    fun `today without photos has no banner`() = runTest(dispatcher) {
        repository.next = indexWith(MonthDay.of(3, 9) to 2)

        enter()

        assertNull(banner)
    }

    @Test
    fun `there is no banner without access to the photos`() = runTest(dispatcher) {
        givenToday(count = 3)
        repository.permission = false

        enter()

        assertNull(banner)
    }

    @Test
    fun `a day that is completed has no banner`() = runTest(dispatcher) {
        givenToday(count = 2, decided = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP))

        enter()

        assertNull(banner)
    }

    @Test
    fun `trashed and deleted photos are decided`() = runTest(dispatcher) {
        givenToday(count = 2, decided = mapOf(1L to PhotoDecision.TRASHED, 2L to PhotoDecision.DELETED))

        enter()

        assertNull(banner)
    }

    @Test
    fun `a photo marked for deletion is decided, so the banner hides even with the dialog cancelled`() = runTest(dispatcher) {
        givenToday(count = 2, decided = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE))

        enter()

        assertNull(banner)
    }

    @Test
    fun `one photo without a decision keeps the banner`() = runTest(dispatcher) {
        givenToday(count = 2, decided = mapOf(1L to PhotoDecision.KEEP))

        enter()

        assertEquals(TodayBanner(2), banner)
    }

    @Test
    fun `the banner hides as soon as the day is completed`() = runTest(dispatcher) {
        givenToday(count = 2, decided = mapOf(1L to PhotoDecision.KEEP))
        enter()
        assertEquals(TodayBanner(2), banner)

        decisions = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)
        sessions.tryEmit(listOf(SwipeSession(today, true, 0, completedAt = today)))
        advanceUntilIdle()

        assertNull(banner)
    }

    @Test
    fun `new photos make the day incomplete again and the banner comes back`() = runTest(dispatcher) {
        givenToday(count = 2, decided = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP))
        enter()
        assertNull(banner)

        // A third photo shows up in the library and in the next index
        repository.todayPhotos = (1L..3L).map(::photo)
        repository.next = indexWith(todayDay to 3)
        enter()

        assertEquals(TodayBanner(3), banner)
    }

    @Test
    fun `returning from a day with more decisions hides the banner`() = runTest(dispatcher) {
        givenToday(count = 2)
        enter()
        assertEquals(TodayBanner(2), banner)

        decisions = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)
        enter()

        assertNull(banner)
    }

    @Test
    fun `the count is the number of photos that exist, not the one of the index`() = runTest(dispatcher) {
        // The index can be ahead of the library query (photos dated from their files) or behind it: the banner says
        // what the deck will hold
        givenToday(count = 2, indexCount = 5)

        enter()

        assertEquals(TodayBanner(2), banner)
    }

    @Test
    fun `the banner does not need an entry of today in the index`() = runTest(dispatcher) {
        repository.todayPhotos = (1L..2L).map(::photo)
        repository.next = indexWith(MonthDay.of(3, 9) to 2)

        enter()

        assertEquals(TodayBanner(2), banner)
    }

    @Test
    fun `the banner does not need an index at all`() = runTest(dispatcher) {
        repository.todayPhotos = (1L..2L).map(::photo)
        repository.next = indexWith()

        enter()

        assertEquals(TodayBanner(2), banner)
    }

    @Test
    fun `photos that are gone have no banner even when the index still counts them`() = runTest(dispatcher) {
        repository.next = indexWith(todayDay to 2)
        repository.todayPhotos = emptyList()

        enter()

        assertNull(banner)
    }

    @Test
    fun `decisions of photos that no longer exist do not matter`() = runTest(dispatcher) {
        givenToday(count = 2, decided = mapOf(1L to PhotoDecision.KEEP, 9L to PhotoDecision.KEEP))

        enter()

        assertEquals(TodayBanner(2), banner)
    }

    @Test
    fun `the query time of the banner is logged`() = runTest(dispatcher) {
        val lines = mutableListOf<String>()
        PerfLog.setEnabled(true)
        PerfLog.sink = { lines += it }
        try {
            givenToday(count = 3)

            enter()

            assertTrue(lines.any { "todayBanner" in it && "photosToReview=3" in it && "query=" in it })
        } finally {
            PerfLog.setEnabled(false)
        }
    }

    @Test
    fun `a failure to read the photos of today leaves no banner`() = runTest(dispatcher) {
        givenToday(count = 2)
        repository.todayFailure = IllegalStateException("query failed")

        enter()

        assertNull(banner)
    }

    @Test
    fun `a tap on the banner opens the day of today`() = runTest(dispatcher) {
        givenToday(count = 2)
        enter()

        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.OpenDay(viewModel.state.value.today))

            assertEquals(CalendarEffect.NavigateToSwipe(today), awaitItem())
        }
    }
}
