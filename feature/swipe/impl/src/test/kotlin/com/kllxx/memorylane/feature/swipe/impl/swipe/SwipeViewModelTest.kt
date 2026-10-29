package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.model.PhotoLocation
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.domain.format.formatTakenAt
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.of(2026, 10, 15)
    private val today = LocalDate.of(2026, 10, 20)
    private val earlier = LocalDate.of(2026, 10, 16)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val uri1 = mockk<Uri>(relaxed = true)
    private val uri2 = mockk<Uri>(relaxed = true)
    private val uri3 = mockk<Uri>(relaxed = true)
    private val photo1 = Photo(1, uri1, LocalDateTime.of(2016, 1, 7, 21, 15), sizeBytes = 100)
    private val photo2 = Photo(2, uri2, LocalDateTime.of(2017, 1, 7, 8, 5), sizeBytes = 200)
    private val photo3 = Photo(3, uri3, LocalDateTime.of(2018, 1, 7, 12, 0), sizeBytes = 300)
    private var photos = listOf(photo1, photo2)
    private val settings = MutableStateFlow(AppSettings())
    private lateinit var photoRepository: PhotoRepository
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var statsRepository: StatsRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var viewModel: SwipeViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private val locationRequests = mutableListOf<List<Long>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        photoRepository = mockk()
        swipeRepository = mockk(relaxed = true)
        statsRepository = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        coEvery { photoRepository.getPhotosForDate(date) } answers { photos }
        coEvery { photoRepository.getTrashedPhotos(any()) } returns emptyList()
        every { photoRepository.resolveLocations(any()) } answers {
            locationRequests += firstArg<List<Photo>>().map { it.id }
            emptyFlow()
        }
        coEvery { swipeRepository.getDecisionsByDate(date) } returns emptyMap()
        every { swipeRepository.getCompletedSessions() } returns flowOf(emptyList())
        every { appSettingsRepository.getSettings() } returns settings
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // The ViewModel starts loading as soon as it is created, so tests stub the repositories first
    private fun createViewModel(restoredIndex: Int? = null, restoredUndo: List<String>? = null) {
        savedStateHandle = SavedStateHandle(
            buildMap {
                put("date", date.toString())
                restoredIndex?.let { put(SwipeViewModel.CURRENT_INDEX_KEY, it) }
                restoredUndo?.let { put(SwipeViewModel.UNDO_STACK_KEY, ArrayList(it)) }
            },
        )
        viewModel = SwipeViewModel(
            photoRepository, swipeRepository, statsRepository, appSettingsRepository, clock, savedStateHandle,
        )
    }

    // Photos for the route date are requested by the ViewModel's init
    private fun TestScope.load() {
        if (!::viewModel.isInitialized) createViewModel()
        advanceUntilIdle()
    }

    private fun TestScope.swipe(vararg directions: SwipeDirection) {
        directions.forEach { viewModel.onIntent(SwipeIntent.Swipe(it)) }
        advanceUntilIdle()
    }

    private val state get() = viewModel.state.value

    private fun decisions() = state.deck.map { it.decision }

    // --- loading ---

    @Test
    fun `photos for the route date are loaded without any intent`() = runTest(dispatcher) {
        load()

        assertEquals(listOf(1L, 2L), state.deck.map { it.id })
        assertEquals(false, state.isLoading)
        assertEquals(date, state.date)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `each photo carries its capture time as text`() = runTest(dispatcher) {
        load()

        assertEquals(
            listOf(formatTakenAt(photo1.takenAt, Locale.getDefault()), formatTakenAt(photo2.takenAt, Locale.getDefault())),
            state.deck.map { it.takenAtText },
        )
    }

    @Test
    fun `stored decisions are on the deck and the visit resumes at the first undecided photo`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        coEvery { swipeRepository.getDecisionsByDate(date) } returns
            mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)

        load()

        assertEquals(listOf(PhotoDecision.KEEP, PhotoDecision.DELETE, null), decisions())
        assertEquals(2, state.currentIndex)
        assertFalse(state.isDayCompleted)
    }

    @Test
    fun `a day that is completely decided opens on its first photo and stays on the screen`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns
            mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)
        createViewModel()

        viewModel.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(0, state.currentIndex)
        assertTrue(state.isDayCompleted)
        assertFalse(state.isFinished)
        // Opening a completed day records nothing
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `photos in the trash are loaded for the strip only`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns mapOf(50L to PhotoDecision.TRASHED)
        val trashed = Photo(50, mockk(relaxed = true), LocalDateTime.of(2015, 1, 7, 9, 0), sizeBytes = 500)
        coEvery { photoRepository.getTrashedPhotos(setOf(50L)) } returns listOf(trashed)

        load()

        assertEquals(listOf(1L, 2L), state.deck.map { it.id })
        assertEquals(listOf(50L), state.trashed.map { it.id })
        assertEquals(PhotoDecision.TRASHED, state.trashed.single().decision)
        assertEquals(formatTakenAt(trashed.takenAt, Locale.getDefault()), state.trashed.single().takenAtText)
    }

    @Test
    fun `a photo that is back in the library after being trashed is undecided again`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns
            mapOf(1L to PhotoDecision.TRASHED, 2L to PhotoDecision.DELETED)

        load()

        assertEquals(listOf(null, null), decisions())
        assertTrue(state.trashed.isEmpty())
        // Nothing is asked of the trash: the photos are in the library
        coVerify(exactly = 0) { photoRepository.getTrashedPhotos(any()) }
    }

    @Test
    fun `a failing trash query still gives the deck`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns mapOf(50L to PhotoDecision.TRASHED)
        coEvery { photoRepository.getTrashedPhotos(any()) } throws IllegalStateException("provider died")

        load()

        assertEquals(listOf(1L, 2L), state.deck.map { it.id })
        assertTrue(state.trashed.isEmpty())
    }

    @Test
    fun `an empty day records no session and goes to calendar, never to review`() = runTest(dispatcher) {
        photos = emptyList()
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToCalendar, awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `a failed photo load goes to calendar without recording a session`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws SecurityException("nope")
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToCalendar, awaitItem())
        }
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `an effect sent before anyone collects is not lost`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws PermissionDeniedException()
        createViewModel()
        advanceUntilIdle()
        viewModel.effects.test {
            assertEquals(SwipeEffect.PermissionRequired, awaitItem())
        }
    }

    @Test
    fun `missing permission asks for it, records no session and does not navigate`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws PermissionDeniedException()
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.PermissionRequired, awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    // --- swiping ---

    @Test
    fun `swiping left saves a DELETE decision and moves to the next photo`() = runTest(dispatcher) {
        load()

        swipe(SwipeDirection.Left)

        assertEquals(listOf(PhotoDecision.DELETE, null), decisions())
        assertEquals(1, state.currentIndex)
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.DELETE) }
    }

    @Test
    fun `swiping right saves a KEEP decision and moves to the next photo`() = runTest(dispatcher) {
        load()

        swipe(SwipeDirection.Right)

        assertEquals(listOf(PhotoDecision.KEEP, null), decisions())
        assertEquals(1, state.currentIndex)
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.KEEP) }
    }

    @Test
    fun `nothing is saved before a swipe`() = runTest(dispatcher) {
        load()

        coVerify(exactly = 0) { swipeRepository.saveDecision(any(), any(), any()) }
        coVerify(exactly = 0) { statsRepository.markReviewed(any()) }
    }

    @Test
    fun `swiping up or down is not a decision`() = runTest(dispatcher) {
        load()

        swipe(SwipeDirection.Up, SwipeDirection.Down)

        assertEquals(0, state.currentIndex)
        coVerify(exactly = 0) { swipeRepository.saveDecision(any(), any(), any()) }
        coVerify(exactly = 0) { statsRepository.markReviewed(any()) }
    }

    @Test
    fun `every swipe marks its photo as reviewed, in both directions`() = runTest(dispatcher) {
        load()

        swipe(SwipeDirection.Left, SwipeDirection.Right)

        coVerify(exactly = 1) { statsRepository.markReviewed(1L) }
        coVerify(exactly = 1) { statsRepository.markReviewed(2L) }
    }

    @Test
    fun `a failing stats write does not stop the swipe or the decision`() = runTest(dispatcher) {
        coEvery { statsRepository.markReviewed(any()) } throws IllegalStateException("disk full")
        load()

        swipe(SwipeDirection.Left)

        assertEquals(1, state.currentIndex)
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.DELETE) }
    }

    @Test
    fun `a failing decision write does not stop swiping`() = runTest(dispatcher) {
        coEvery { swipeRepository.saveDecision(any(), any(), any()) } throws IllegalStateException("disk full")
        load()

        swipe(SwipeDirection.Left)

        assertEquals(1, state.currentIndex)
        assertEquals(PhotoDecision.DELETE, state.deck[0].decision)
    }

    // --- jump ---

    @Test
    fun `jumping, then swiping, overwrites the decision and continues from there`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        coEvery { swipeRepository.getDecisionsByDate(date) } returns mapOf(1L to PhotoDecision.DELETE)
        load()
        assertEquals(1, state.currentIndex)

        viewModel.onIntent(SwipeIntent.JumpTo(0))
        swipe(SwipeDirection.Right)

        assertEquals(PhotoDecision.KEEP, state.deck[0].decision)
        assertEquals(1, state.currentIndex)
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.KEEP) }
    }

    @Test
    fun `jumping saves nothing`() = runTest(dispatcher) {
        load()

        viewModel.onIntent(SwipeIntent.JumpTo(1))
        advanceUntilIdle()

        assertEquals(1, state.currentIndex)
        coVerify(exactly = 0) { swipeRepository.saveDecision(any(), any(), any()) }
    }

    // --- undo ---

    @Test
    fun `undo of a swipe on an undecided photo removes the stored decision`() = runTest(dispatcher) {
        load()
        swipe(SwipeDirection.Left)
        assertTrue(state.canUndo)

        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()

        assertEquals(listOf(null, null), decisions())
        assertEquals(0, state.currentIndex)
        assertFalse(state.canUndo)
        coVerify(exactly = 1) { swipeRepository.removeDecision(1L) }
    }

    @Test
    fun `undo restores the decision the photo had before the swipe`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns
            mapOf(1L to PhotoDecision.DELETE, 2L to PhotoDecision.KEEP)
        load()

        viewModel.onIntent(SwipeIntent.JumpTo(0))
        swipe(SwipeDirection.Right)
        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()

        assertEquals(PhotoDecision.DELETE, state.deck[0].decision)
        // The previous decision is written back, last
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.KEEP) }
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.DELETE) }
        coVerify(exactly = 0) { swipeRepository.removeDecision(any()) }
    }

    @Test
    fun `several undos in a row step back through the swipes`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        load()
        swipe(SwipeDirection.Left, SwipeDirection.Right, SwipeDirection.Left)
        assertEquals(listOf(PhotoDecision.DELETE, PhotoDecision.KEEP, PhotoDecision.DELETE), decisions())

        viewModel.onIntent(SwipeIntent.Undo)
        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()

        assertEquals(listOf(PhotoDecision.DELETE, null, null), decisions())
        assertEquals(1, state.currentIndex)
        assertTrue(state.canUndo)
        coVerify(exactly = 1) { swipeRepository.removeDecision(3L) }
        coVerify(exactly = 1) { swipeRepository.removeDecision(2L) }

        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()
        assertFalse(state.canUndo)
    }

    @Test
    fun `undo with nothing to undo does nothing`() = runTest(dispatcher) {
        load()

        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()

        assertEquals(0, state.currentIndex)
        coVerify(exactly = 0) { swipeRepository.removeDecision(any()) }
        coVerify(exactly = 0) { swipeRepository.saveDecision(any(), any(), any()) }
    }

    @Test
    fun `undo never takes a photo back from the stats`() = runTest(dispatcher) {
        load()
        swipe(SwipeDirection.Left)

        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()

        // Viewed photos only ever count up
        coVerify(exactly = 1) { statsRepository.markReviewed(1L) }
    }

    @Test
    fun `swiping again after an undo is a new decision`() = runTest(dispatcher) {
        load()
        swipe(SwipeDirection.Left)
        viewModel.onIntent(SwipeIntent.Undo)

        swipe(SwipeDirection.Right)

        assertEquals(PhotoDecision.KEEP, state.deck[0].decision)
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.KEEP) }
    }

    // --- end of the deck ---

    @Test
    fun `finishing with nothing to delete saves a completed session and opens the result`() = runTest(dispatcher) {
        load()
        viewModel.effects.test {
            swipe(SwipeDirection.Right, SwipeDirection.Right)
            assertTrue(state.isFinished)
            assertEquals(SwipeEffect.NavigateToResult(date, keptCount = 2, totalCount = 2), awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0, today)) }
    }

    @Test
    fun `finishing with only earlier marks pending opens before-delete`() = runTest(dispatcher) {
        coEvery { swipeRepository.getPendingDeletes() } returns mapOf(LocalDate.of(2014, 1, 7) to setOf(99L))
        load()
        viewModel.effects.test {
            swipe(SwipeDirection.Right, SwipeDirection.Right)
            assertEquals(SwipeEffect.NavigateToBeforeDelete(date, totalPhotosCount = 2), awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0, today)) }
    }

    @Test
    fun `finishing with no mark anywhere opens the result with nothing deleted`() = runTest(dispatcher) {
        coEvery { swipeRepository.getPendingDeletes() } returns emptyMap()
        load()
        viewModel.effects.test {
            swipe(SwipeDirection.Right, SwipeDirection.Right)
            assertEquals(SwipeEffect.NavigateToResult(date, keptCount = 2, totalCount = 2), awaitItem())
        }
    }

    @Test
    fun `finishing with photos marked for deletion saves a completed session and opens before-delete`() = runTest(dispatcher) {
        load()
        viewModel.effects.test {
            swipe(SwipeDirection.Left, SwipeDirection.Right)
            assertEquals(SwipeEffect.NavigateToBeforeDelete(date, totalPhotosCount = 2), awaitItem())
        }
        // A mark is a decision: the day is completed by the swipes, a cancelled dialog does not reopen it
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0, today)) }
    }

    @Test
    fun `the last photo goes back to the first undecided one while any is left`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        coEvery { swipeRepository.getDecisionsByDate(date) } returns
            mapOf(1L to PhotoDecision.KEEP, 3L to PhotoDecision.KEEP)
        load()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.JumpTo(2))
            // 2 is undecided and sits before the last photo, which is swiped
            swipe(SwipeDirection.Left)
            assertEquals(1, state.currentIndex)
            assertFalse(state.isFinished)
            expectNoEvents()
        }
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `after jumping around, finishing the last undecided photo routes by what is marked`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        coEvery { swipeRepository.getDecisionsByDate(date) } returns mapOf(1L to PhotoDecision.KEEP)
        load()
        viewModel.effects.test {
            // Swipe 2, then 3 (the last one): the day is complete and one photo is marked
            swipe(SwipeDirection.Left, SwipeDirection.Right)
            assertEquals(SwipeEffect.NavigateToBeforeDelete(date, totalPhotosCount = 3), awaitItem())
        }
    }

    @Test
    fun `a day already completed on its own date keeps that date and its result when it is finished again`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns
            flowOf(listOf(SwipeSession(date, true, 300, completedAt = date, deletedCount = 4)))
        load()

        swipe(SwipeDirection.Left, SwipeDirection.Left)

        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 300, date, deletedCount = 4)) }
    }

    @Test
    fun `finishing a day completed on its own date with nothing to delete keeps that date`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(SwipeSession(date, true, 300, date)))
        load()

        swipe(SwipeDirection.Right, SwipeDirection.Right)

        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 300, date)) }
    }

    @Test
    fun `a day completed on another date is completed on the date it is finished, so it can become a real day`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(SwipeSession(date, true, 300, earlier)))
        load()

        swipe(SwipeDirection.Right, SwipeDirection.Right)

        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 300, today)) }
    }

    @Test
    fun `a day finished on its own date is completed on its own date`() = runTest(dispatcher) {
        val onItsOwnDate = Clock.fixed(date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        viewModel = SwipeViewModel(
            photoRepository, swipeRepository, statsRepository, appSettingsRepository, onItsOwnDate,
            SavedStateHandle(mapOf("date" to date.toString())),
        )
        advanceUntilIdle()

        swipe(SwipeDirection.Right, SwipeDirection.Right)

        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0, date)) }
    }

    @Test
    fun `a day finished on another date than its own is completed on that date`() = runTest(dispatcher) {
        load()

        swipe(SwipeDirection.Right, SwipeDirection.Right)

        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0, today)) }
    }

    @Test
    fun `a failing session write still navigates once everything is decided`() = runTest(dispatcher) {
        coEvery { swipeRepository.saveSession(any()) } throws IllegalStateException("disk full")
        load()
        viewModel.effects.test {
            swipe(SwipeDirection.Left, SwipeDirection.Left)
            assertEquals(SwipeEffect.NavigateToBeforeDelete(date, totalPhotosCount = 2), awaitItem())
        }
    }

    @Test
    fun `a re-opened completed day leads to before-delete again for a new mark`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns
            mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)
        load()
        viewModel.effects.test {
            swipe(SwipeDirection.Right, SwipeDirection.Left)
            assertEquals(SwipeEffect.NavigateToBeforeDelete(date, totalPhotosCount = 2), awaitItem())
        }
    }

    // --- close, share, open in gallery ---

    @Test
    fun `Close navigates to calendar`() = runTest(dispatcher) {
        createViewModel()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.Close)
            assertEquals(SwipeEffect.NavigateToCalendar, awaitItem())
        }
    }

    @Test
    fun `Share sends the uri of the current photo`() = runTest(dispatcher) {
        load()
        swipe(SwipeDirection.Right)
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.Share)
            assertEquals(SwipeEffect.ShareImage(uri2), awaitItem())
        }
    }

    @Test
    fun `OpenInGallery sends the uri of the current photo`() = runTest(dispatcher) {
        load()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.OpenInGallery)
            assertEquals(SwipeEffect.OpenInGallery(uri1), awaitItem())
        }
    }

    @Test
    fun `Share and OpenInGallery do nothing before there is a photo`() = runTest(dispatcher) {
        createViewModel()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.Share)
            viewModel.onIntent(SwipeIntent.OpenInGallery)
            expectNoEvents()
        }
    }

    // --- buttons mode ---

    @Test
    fun `buttons mode follows the stored setting`() = runTest(dispatcher) {
        load()
        assertFalse(state.buttonsMode)

        settings.value = AppSettings(swipeButtonsEnabled = true)
        advanceUntilIdle()

        assertTrue(state.buttonsMode)
    }

    @Test
    fun `buttons mode is on from the start when it was stored on`() = runTest(dispatcher) {
        settings.value = AppSettings(swipeButtonsEnabled = true)

        load()

        assertTrue(state.buttonsMode)
    }

    @Test
    fun `toggling buttons mode persists the opposite setting`() = runTest(dispatcher) {
        load()

        viewModel.onIntent(SwipeIntent.ToggleButtonsMode)
        advanceUntilIdle()

        coVerify(exactly = 1) { appSettingsRepository.setSwipeButtonsEnabled(true) }
    }

    @Test
    fun `toggling twice turns the mode on and off again`() = runTest(dispatcher) {
        coEvery { appSettingsRepository.setSwipeButtonsEnabled(any()) } answers {
            settings.value = AppSettings(swipeButtonsEnabled = firstArg())
        }
        load()

        viewModel.onIntent(SwipeIntent.ToggleButtonsMode)
        advanceUntilIdle()
        assertTrue(state.buttonsMode)

        viewModel.onIntent(SwipeIntent.ToggleButtonsMode)
        advanceUntilIdle()
        assertFalse(state.buttonsMode)
        coVerify(exactly = 1) { appSettingsRepository.setSwipeButtonsEnabled(true) }
        coVerify(exactly = 1) { appSettingsRepository.setSwipeButtonsEnabled(false) }
    }

    @Test
    fun `a failing settings write does not break the screen`() = runTest(dispatcher) {
        coEvery { appSettingsRepository.setSwipeButtonsEnabled(any()) } throws IllegalStateException("disk full")
        load()

        viewModel.onIntent(SwipeIntent.ToggleButtonsMode)
        advanceUntilIdle()

        assertFalse(state.buttonsMode)
        swipe(SwipeDirection.Right)
        assertEquals(1, state.currentIndex)
    }

    // --- saved state ---

    @Test
    fun `the current index is saved while swiping`() = runTest(dispatcher) {
        load()
        assertEquals(0, savedStateHandle.get<Int>(SwipeViewModel.CURRENT_INDEX_KEY))

        swipe(SwipeDirection.Right)

        assertEquals(1, savedStateHandle.get<Int>(SwipeViewModel.CURRENT_INDEX_KEY))
    }

    @Test
    fun `a saved index is restored and not overwritten while loading`() = runTest(dispatcher) {
        createViewModel(restoredIndex = 1)
        // Loading has not finished yet: the saved value must still be the restored one
        assertEquals(1, savedStateHandle.get<Int>(SwipeViewModel.CURRENT_INDEX_KEY))
        advanceUntilIdle()

        assertEquals(1, state.currentIndex)
        assertEquals(1, savedStateHandle.get<Int>(SwipeViewModel.CURRENT_INDEX_KEY))
    }

    @Test
    fun `the undo stack is saved with the decisions of this visit`() = runTest(dispatcher) {
        load()

        swipe(SwipeDirection.Left, SwipeDirection.Right)

        assertEquals(arrayListOf("1:-", "2:-"), savedStateHandle.get<ArrayList<String>>(SwipeViewModel.UNDO_STACK_KEY))
    }

    @Test
    fun `an undo stack survives process death`() = runTest(dispatcher) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns mapOf(1L to PhotoDecision.KEEP)
        load()
        viewModel.onIntent(SwipeIntent.JumpTo(0))
        swipe(SwipeDirection.Left)
        val savedIndex = savedStateHandle.get<Int>(SwipeViewModel.CURRENT_INDEX_KEY)
        val savedUndo = savedStateHandle.get<ArrayList<String>>(SwipeViewModel.UNDO_STACK_KEY)!!
        assertEquals(listOf("1:KEEP"), savedUndo)

        // The process dies and the screen comes back with what was saved; the decision itself is in the database
        coEvery { swipeRepository.getDecisionsByDate(date) } returns mapOf(1L to PhotoDecision.DELETE)
        createViewModel(restoredIndex = savedIndex, restoredUndo = savedUndo)
        advanceUntilIdle()

        assertTrue(state.canUndo)
        assertEquals(1, state.currentIndex)
        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()
        assertEquals(PhotoDecision.KEEP, state.deck[0].decision)
        coVerify(exactly = 1) { swipeRepository.saveDecision(1L, date, PhotoDecision.KEEP) }
    }

    @Test
    fun `a damaged saved undo entry is ignored`() = runTest(dispatcher) {
        createViewModel(restoredUndo = listOf("nonsense", "1:SOMETHING_NEW", "x:KEEP", "2:KEEP"))
        advanceUntilIdle()

        assertEquals(listOf(UndoEntry(2, PhotoDecision.KEEP)), state.undoStack)
    }

    @Test
    fun `undo entries are encoded and decoded without loss`() {
        listOf(UndoEntry(7, null), UndoEntry(8, PhotoDecision.DELETE), UndoEntry(9, PhotoDecision.TRASHED)).forEach {
            assertEquals(it, SwipeViewModel.decodeUndoEntry(SwipeViewModel.encodeUndoEntry(it)))
        }
        assertNull(SwipeViewModel.decodeUndoEntry("7"))
    }

    // --- locations ---

    private fun placeFor(photo: Photo) = PhotoLocation(photo.id, "place ${photo.id}")

    @Test
    fun `the places of the whole day are looked up, the photo on top first`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)

        createViewModel(restoredIndex = 1)
        advanceUntilIdle()

        assertEquals(listOf(listOf(2L, 3L, 1L)), locationRequests)
    }

    @Test
    fun `each place goes into the state as it arrives`() = runTest(dispatcher) {
        every { photoRepository.resolveLocations(any()) } answers {
            flow {
                emit(placeFor(photo1))
                awaitCancellation()
            }
        }

        load()

        assertEquals("place 1", state.deck[0].photo.location)
        assertNull(state.deck[1].photo.location)
    }

    @Test
    fun `a photo without a place stays without one and is not asked for again`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        every { photoRepository.resolveLocations(any()) } answers {
            locationRequests += firstArg<List<Photo>>().map { it.id }
            flow {
                emit(PhotoLocation(1, null))
                awaitCancellation()
            }
        }
        load()

        viewModel.onIntent(SwipeIntent.JumpTo(2))
        advanceUntilIdle()

        assertNull(state.deck[0].photo.location)
        // The second request starts from the photo that was jumped to and leaves out photo 1
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(3L, 2L)), locationRequests)
    }

    @Test
    fun `jumping restarts the lookup from the new photo and skips the places that have arrived`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        every { photoRepository.resolveLocations(any()) } answers {
            locationRequests += firstArg<List<Photo>>().map { it.id }
            flow {
                emit(placeFor(photo1))
                awaitCancellation()
            }
        }
        load()

        viewModel.onIntent(SwipeIntent.JumpTo(2))
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(3L, 2L)), locationRequests)
        assertEquals("place 1", state.deck[0].photo.location)
    }

    @Test
    fun `swiping does not restart the lookup`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        load()

        swipe(SwipeDirection.Right, SwipeDirection.Right)

        assertEquals(1, locationRequests.size)
    }

    @Test
    fun `undo restarts the lookup from the photo it goes back to`() = runTest(dispatcher) {
        photos = listOf(photo1, photo2, photo3)
        every { photoRepository.resolveLocations(any()) } answers {
            locationRequests += firstArg<List<Photo>>().map { it.id }
            flow { awaitCancellation() }
        }
        load()
        swipe(SwipeDirection.Right, SwipeDirection.Right)

        viewModel.onIntent(SwipeIntent.Undo)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(2L, 3L, 1L)), locationRequests)
    }

    @Test
    fun `a failing location lookup does not stop swiping`() = runTest(dispatcher) {
        every { photoRepository.resolveLocations(any()) } returns flow { throw IllegalStateException("no network") }
        load()

        swipe(SwipeDirection.Right)

        assertEquals(1, state.currentIndex)
    }

    @Test
    fun `no lookup is made for an empty day`() = runTest(dispatcher) {
        photos = emptyList()

        load()

        assertTrue(locationRequests.isEmpty())
    }


    // --- back from «Перед удалением» ---

    @Test
    fun `back on the deck reads the decisions again and shows the last photo`() = runTest(dispatcher) {
        load()
        swipe(SwipeDirection.Left, SwipeDirection.Left)
        assertTrue(state.isFinished)
        // In the review the first photo was unmarked
        coEvery { swipeRepository.getDecisionsByDate(date) } returns
            mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)

        viewModel.onIntent(SwipeIntent.ReturnedToDeck)
        advanceUntilIdle()

        assertFalse(state.isFinished)
        assertEquals(1, state.currentIndex)
        assertEquals(listOf(PhotoDecision.KEEP, PhotoDecision.DELETE), decisions())
        assertFalse(state.canUndo)
    }
}
