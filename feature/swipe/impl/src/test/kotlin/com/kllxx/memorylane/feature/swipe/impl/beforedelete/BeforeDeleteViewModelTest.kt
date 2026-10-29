package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.Manifest
import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.DeletionOutcome
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A [SwipeRepository] in memory that moves decisions and records a deletion the way the real transaction does. */
private class FakeSwipeRepository(
    val decisions: MutableMap<Long, PhotoDecision>,
    private val defaultDate: LocalDate,
) : SwipeRepository {
    /** The day of a decision, [defaultDate] unless set. */
    val days = mutableMapOf<Long, LocalDate>()

    val saved = mutableListOf<Pair<Long, PhotoDecision>>()
    val removed = mutableListOf<Long>()
    val sessions = mutableListOf<SwipeSession>()
    val confirmed = mutableListOf<Pair<Map<Long, Long>, PhotoDecision>>()

    // What the stats of the day have got from confirmed deletions
    var deletedCount = 0
    var freedBytes = 0L

    override suspend fun saveDecision(photoId: Long, date: LocalDate, decision: PhotoDecision) {
        saved += photoId to decision
        decisions[photoId] = decision
        days[photoId] = date
    }

    override suspend fun getDecisionsByDate(date: LocalDate): Map<Long, PhotoDecision> =
        decisions.filterKeys { (days[it] ?: defaultDate) == date }

    override suspend fun getPendingDeletes(): Map<LocalDate, Set<Long>> =
        decisions.filterValues { it == PhotoDecision.DELETE }.keys.groupBy { days[it] ?: defaultDate }.mapValues { it.value.toSet() }

    override suspend fun removeDecision(photoId: Long) {
        removed += photoId
        decisions.remove(photoId)
    }

    override suspend fun setDecisions(photoIds: Collection<Long>, decision: PhotoDecision) = Unit

    override suspend fun confirmDeletion(bytesById: Map<Long, Long>, decision: PhotoDecision): DeletionOutcome {
        confirmed += bytesById to decision
        val applied = bytesById.keys.filter { decisions[it] == PhotoDecision.DELETE }
        applied.forEach { decisions[it] = decision }
        val freed = applied.sumOf { bytesById.getValue(it) }
        deletedCount += applied.size
        freedBytes += freed
        return DeletionOutcome(applied.size, freed)
    }

    override suspend fun saveSession(session: SwipeSession) {
        sessions += session
    }

    override suspend fun getSessionByCompletedAt(date: LocalDate): SwipeSession? = null

    override fun getCompletedSessions(): Flow<List<SwipeSession>> = flowOf(emptyList())
}

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BeforeDeleteViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.of(2015, 1, 15)
    private val today = LocalDate.of(2026, 10, 29)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val application: Application = RuntimeEnvironment.getApplication()
    private val sender = mockk<IntentSender>(relaxed = true)

    private fun photo(id: Long, size: Long) =
        Photo(id, mockk<Uri>(relaxed = true), LocalDateTime.of(2015, 1, 15, 10, id.toInt()), sizeBytes = size)

    private val photo1 = photo(1, 150_000_000)
    private val photo2 = photo(2, 250_000_000)
    private val photo3 = photo(3, 10_000_000)
    private var photos = listOf(photo1, photo2, photo3)

    // 1 and 2 are marked, 3 was kept
    private lateinit var swipeRepository: FakeSwipeRepository
    private lateinit var photoRepository: PhotoRepository
    private lateinit var viewModel: BeforeDeleteViewModel
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        swipeRepository = FakeSwipeRepository(
            mutableMapOf(1L to PhotoDecision.DELETE, 2L to PhotoDecision.DELETE, 3L to PhotoDecision.KEEP),
            date,
        )
        photoRepository = mockk()
        coEvery { photoRepository.getPhotosForDate(date) } answers { photos }
        coEvery { photoRepository.getPhotoSizes(any()) } answers { photos.associate { it.id to it.sizeBytes } }
        coEvery { photoRepository.deletePhotos(any()) } returns DeleteResult.RequiresConfirmation(sender)
        coEvery { photoRepository.refreshOnThisDayIndex() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun create(extra: Map<String, Any> = emptyMap()) {
        savedStateHandle = SavedStateHandle(mapOf<String, Any>("date" to date.toString()) + extra)
        viewModel = BeforeDeleteViewModel(swipeRepository, photoRepository, clock, application, savedStateHandle)
    }

    private fun TestScope.load(extra: Map<String, Any> = emptyMap()) {
        create(extra)
        advanceUntilIdle()
    }

    private fun TestScope.send(intent: BeforeDeleteIntent) {
        viewModel.onIntent(intent)
        advanceUntilIdle()
    }

    private val state get() = viewModel.state.value

    private fun marked() = state.cells.filter { it.marked }.map { it.photoId }

    // --- the grid ---

    @Test
    fun `the grid is the marked photos of the day, oldest first, all marked`() = runTest(dispatcher) {
        load()

        assertFalse(state.isLoading)
        assertEquals(listOf(1L, 2L), state.cells.map { it.photoId })
        assertEquals(listOf(1L, 2L), marked())
        assertEquals(listOf(150_000_000L, 250_000_000L), state.cells.map { it.sizeBytes })
    }

    @Test
    fun `a tap on a marked photo keeps it and saves KEEP at once`() = runTest(dispatcher) {
        load()

        send(BeforeDeleteIntent.Toggle(2))

        assertEquals(listOf(1L), marked())
        assertEquals(listOf(2L to PhotoDecision.KEEP), swipeRepository.saved)
        // The photo stays in the grid so that it can be marked again
        assertEquals(listOf(1L, 2L), state.cells.map { it.photoId })
    }

    @Test
    fun `a tap on a kept photo marks it again and saves DELETE`() = runTest(dispatcher) {
        load()
        send(BeforeDeleteIntent.Toggle(2))

        send(BeforeDeleteIntent.Toggle(2))

        assertEquals(listOf(1L, 2L), marked())
        assertEquals(listOf(2L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE), swipeRepository.saved)
    }

    @Test
    fun `the button follows the number and the size of the marked photos`() = runTest(dispatcher) {
        load()
        assertEquals(DeleteButton.Delete(2, com.kllxx.memorylane.core.domain.format.formatSize(400_000_000)), state.toButton())

        send(BeforeDeleteIntent.Toggle(2))
        assertEquals(DeleteButton.Delete(1, com.kllxx.memorylane.core.domain.format.formatSize(150_000_000)), state.toButton())

        send(BeforeDeleteIntent.Toggle(1))
        assertEquals(DeleteButton.Done, state.toButton())
    }

    @Test
    fun `a photo that left the library is dropped from the marks and does not show`() = runTest(dispatcher) {
        photos = listOf(photo1, photo3)

        load()

        assertEquals(listOf(1L), state.cells.map { it.photoId })
        assertEquals(listOf(2L), swipeRepository.removed)
    }

    @Test
    fun `when none of the marked photos is left the screen is not shown`() = runTest(dispatcher) {
        photos = listOf(photo3)
        create()

        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(BeforeDeleteEffect.NavigateToCalendar, awaitItem())
        }
    }

    @Test
    fun `without access to the library the screen is left for the calendar`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws PermissionDeniedException("no access")
        create()

        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(BeforeDeleteEffect.NavigateToCalendar, awaitItem())
        }
    }

    @Test
    fun `the grid is locked while a delete is on its way`() = runTest(dispatcher) {
        load()
        send(BeforeDeleteIntent.DeleteClicked)

        send(BeforeDeleteIntent.Toggle(1))

        assertEquals(listOf(1L, 2L), marked())
        assertTrue(swipeRepository.saved.isEmpty())
    }

    // --- marks left over from earlier days ---

    private val earlier = LocalDate.of(2014, 1, 15)
    private val older1 = Photo(11, mockk<Uri>(relaxed = true), LocalDateTime.of(2014, 1, 15, 9, 0), sizeBytes = 1_000_000)
    private val older2 = Photo(12, mockk<Uri>(relaxed = true), LocalDateTime.of(2014, 1, 15, 9, 1), sizeBytes = 2_000_000)
    private var earlierPhotos = listOf(older1, older2)

    /** Photos 11 and 12 of the earlier day, marked in an earlier visit whose dialog was cancelled. */
    private fun markEarlier(vararg ids: Long) {
        ids.forEach {
            swipeRepository.decisions[it] = PhotoDecision.DELETE
            swipeRepository.days[it] = earlier
        }
        coEvery { photoRepository.getPhotosForDate(earlier) } answers { earlierPhotos }
        coEvery { photoRepository.getPhotoSizes(any()) } answers { (photos + earlierPhotos).associate { it.id to it.sizeBytes } }
    }

    @Test
    fun `marks of an earlier day appear after the current day under their own group`() = runTest(dispatcher) {
        markEarlier(11, 12)

        load()

        assertEquals(listOf(1L, 2L, 11L, 12L), marked())
        assertEquals(listOf(date, earlier), state.groups.map { it.date })
        assertEquals(listOf(true, false), state.groups.map { it.isCurrent })
        assertEquals(listOf(listOf(1L, 2L), listOf(11L, 12L)), state.groups.map { g -> g.cells.map { it.photoId } })
    }

    @Test
    fun `earlier groups are newest first`() = runTest(dispatcher) {
        val oldest = LocalDate.of(2012, 1, 15)
        markEarlier(11)
        swipeRepository.decisions[21] = PhotoDecision.DELETE
        swipeRepository.days[21] = oldest
        val photo21 = Photo(21, mockk<Uri>(relaxed = true), LocalDateTime.of(2012, 1, 15, 9, 0), sizeBytes = 5)
        coEvery { photoRepository.getPhotosForDate(oldest) } returns listOf(photo21)

        load()

        assertEquals(listOf(date, earlier, oldest), state.groups.map { it.date })
    }

    @Test
    fun `a tap in an earlier group saves the decision in that day`() = runTest(dispatcher) {
        markEarlier(11, 12)
        load()

        send(BeforeDeleteIntent.Toggle(12))

        assertEquals(listOf(12L to PhotoDecision.KEEP), swipeRepository.saved)
        assertEquals(earlier, swipeRepository.days[12])
        assertEquals(listOf(1L, 2L, 11L), marked())
    }

    @Test
    fun `the button counts and sizes the marks of every day`() = runTest(dispatcher) {
        markEarlier(11, 12)
        load()

        assertEquals(DeleteButton.Delete(4, com.kllxx.memorylane.core.domain.format.formatSize(403_000_000)), state.toButton())
    }

    @Test
    fun `only earlier marks still open the grid`() = runTest(dispatcher) {
        swipeRepository.decisions[1] = PhotoDecision.KEEP
        swipeRepository.decisions[2] = PhotoDecision.KEEP
        markEarlier(11, 12)

        load()

        assertEquals(listOf(earlier), state.groups.map { it.date })
        assertEquals(listOf(11L, 12L), marked())
    }

    @Test
    fun `an earlier mark whose photo is gone is dropped from the grid`() = runTest(dispatcher) {
        markEarlier(11, 12)
        earlierPhotos = listOf(older1)

        load()

        assertEquals(listOf(1L, 2L, 11L), marked())
        assertEquals(listOf(12L), swipeRepository.removed)
    }

    @Test
    fun `one confirmation trashes every day, and the result counts them all but keeps the current day only`() = runTest(dispatcher) {
        markEarlier(11, 12)
        load()
        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)
            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())

            send(BeforeDeleteIntent.SystemDeleteResult(confirmed = true))

            assertEquals(
                BeforeDeleteEffect.NavigateToResult(date, deletedCount = 4, freedBytes = 403_000_000, keptCount = 1, totalCount = 3),
                awaitItem(),
            )
        }
        coVerify { photoRepository.deletePhotos(listOf(1L, 2L, 11L, 12L)) }
        assertEquals(PhotoDecision.TRASHED, swipeRepository.decisions[1])
        assertEquals(PhotoDecision.TRASHED, swipeRepository.decisions[11])
        assertEquals(PhotoDecision.TRASHED, swipeRepository.decisions[12])
        assertEquals(earlier, swipeRepository.days[12])
    }

    // --- nothing marked ---

    @Test
    fun `with nothing marked the button finishes the day and opens the result without deleting`() = runTest(dispatcher) {
        load()
        send(BeforeDeleteIntent.Toggle(1))
        send(BeforeDeleteIntent.Toggle(2))

        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)

            assertEquals(
                BeforeDeleteEffect.NavigateToResult(date, deletedCount = 0, freedBytes = 0, keptCount = 3, totalCount = 3),
                awaitItem(),
            )
        }
        assertEquals(listOf(SwipeSession(date, completed = true, savedBytes = 0, completedAt = today)), swipeRepository.sessions)
        coVerify(exactly = 0) { photoRepository.deletePhotos(any()) }
    }

    // --- the trash request (API 30+) ---

    @Test
    fun `the button asks for the trash request after reading the sizes, and nothing is recorded yet`() = runTest(dispatcher) {
        load()

        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)

            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())
        }
        coVerifyOrder {
            photoRepository.getPhotoSizes(listOf(1L, 2L))
            photoRepository.deletePhotos(listOf(1L, 2L))
        }
        assertTrue(state.isDeleting)
        assertTrue(swipeRepository.confirmed.isEmpty())
        assertEquals(listOf(1L, 2L), savedStateHandle.get<LongArray>(BeforeDeleteViewModel.PENDING_IDS)!!.toList())
        assertEquals(listOf(150_000_000L, 250_000_000L), savedStateHandle.get<LongArray>(BeforeDeleteViewModel.PENDING_BYTES)!!.toList())
    }

    @Test
    fun `a confirmed request trashes the photos, records the stats once, refreshes the index and opens the result`() = runTest(dispatcher) {
        load()
        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)
            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())

            send(BeforeDeleteIntent.SystemDeleteResult(confirmed = true))

            assertEquals(
                BeforeDeleteEffect.NavigateToResult(date, deletedCount = 2, freedBytes = 400_000_000, keptCount = 1, totalCount = 3),
                awaitItem(),
            )
        }
        assertEquals(listOf(mapOf(1L to 150_000_000L, 2L to 250_000_000L) to PhotoDecision.TRASHED), swipeRepository.confirmed)
        assertEquals(PhotoDecision.TRASHED, swipeRepository.decisions[1])
        assertEquals(PhotoDecision.TRASHED, swipeRepository.decisions[2])
        assertEquals(PhotoDecision.KEEP, swipeRepository.decisions[3])
        coVerify(exactly = 1) { photoRepository.refreshOnThisDayIndex() }
        assertNull(savedStateHandle.get<LongArray>(BeforeDeleteViewModel.PENDING_IDS))
    }

    @Test
    fun `an unmarked photo is not part of the request`() = runTest(dispatcher) {
        load()
        send(BeforeDeleteIntent.Toggle(2))

        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)
            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())
            send(BeforeDeleteIntent.SystemDeleteResult(true))

            assertEquals(
                BeforeDeleteEffect.NavigateToResult(date, deletedCount = 1, freedBytes = 150_000_000, keptCount = 2, totalCount = 3),
                awaitItem(),
            )
        }
        coVerify { photoRepository.deletePhotos(listOf(1L)) }
    }

    @Test
    fun `the same result delivered twice counts the deletion once and opens the result once`() = runTest(dispatcher) {
        load()
        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)
            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())

            send(BeforeDeleteIntent.SystemDeleteResult(true))
            send(BeforeDeleteIntent.SystemDeleteResult(true))

            assertTrue(awaitItem() is BeforeDeleteEffect.NavigateToResult)
            expectNoEvents()
        }
        assertEquals(1, swipeRepository.confirmed.size)
        assertEquals(2, swipeRepository.deletedCount)
        assertEquals(400_000_000L, swipeRepository.freedBytes)
        coVerify(exactly = 1) { photoRepository.refreshOnThisDayIndex() }
    }

    @Test
    fun `cancelling the system dialog changes nothing and lets the user try again`() = runTest(dispatcher) {
        load()
        send(BeforeDeleteIntent.DeleteClicked)

        send(BeforeDeleteIntent.SystemDeleteResult(confirmed = false))

        assertFalse(state.isDeleting)
        assertEquals(listOf(1L, 2L), marked())
        assertTrue(swipeRepository.confirmed.isEmpty())
        assertEquals(PhotoDecision.DELETE, swipeRepository.decisions[1])
        assertNull(savedStateHandle.get<LongArray>(BeforeDeleteViewModel.PENDING_IDS))
        coVerify(exactly = 0) { photoRepository.refreshOnThisDayIndex() }
    }

    @Test
    fun `after a cancel the button can ask again`() = runTest(dispatcher) {
        load()

        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)
            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())
            send(BeforeDeleteIntent.SystemDeleteResult(false))

            send(BeforeDeleteIntent.DeleteClicked)

            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())
        }
    }

    @Test
    fun `a failed request is an error, nothing is recorded and the marks stay`() = runTest(dispatcher) {
        coEvery { photoRepository.deletePhotos(any()) } throws SecurityException("no")
        load()

        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)

            assertEquals(BeforeDeleteEffect.ShowDeleteError, awaitItem())
        }
        assertFalse(state.isDeleting)
        assertEquals(listOf(1L, 2L), marked())
        assertTrue(swipeRepository.confirmed.isEmpty())
        assertNull(savedStateHandle.get<LongArray>(BeforeDeleteViewModel.PENDING_IDS))
    }

    @Test
    fun `a failure to record the deletion is an error and keeps the screen`() = runTest(dispatcher) {
        val failing = object : SwipeRepository by swipeRepository {
            override suspend fun confirmDeletion(bytesById: Map<Long, Long>, decision: PhotoDecision): DeletionOutcome =
                throw IllegalStateException("disk full")
        }
        savedStateHandle = SavedStateHandle(mapOf("date" to date.toString()))
        viewModel = BeforeDeleteViewModel(failing, photoRepository, clock, application, savedStateHandle)
        advanceUntilIdle()

        viewModel.effects.test {
            send(BeforeDeleteIntent.DeleteClicked)
            assertEquals(BeforeDeleteEffect.LaunchTrashRequest(sender), awaitItem())
            send(BeforeDeleteIntent.SystemDeleteResult(true))

            assertEquals(BeforeDeleteEffect.ShowDeleteError, awaitItem())
        }
        assertFalse(state.isDeleting)
    }

    // --- the result arrives after the screen was recreated ---

    @Test
    fun `a result after the process died is understood from the saved request, and no mark is dropped meanwhile`() = runTest(dispatcher) {
        // The photos are in the trash already: the library no longer lists them, but their decisions are still DELETE
        photos = listOf(photo3)
        val saved = mapOf<String, Any>(
            BeforeDeleteViewModel.GRID_IDS to longArrayOf(1, 2),
            BeforeDeleteViewModel.PENDING_IDS to longArrayOf(1, 2),
            BeforeDeleteViewModel.PENDING_BYTES to longArrayOf(150_000_000, 250_000_000),
        )
        create(saved)

        viewModel.effects.test {
            advanceUntilIdle()
            // The dialog is open: the button is locked and nothing was removed as stale
            assertTrue(state.isDeleting)
            assertTrue(swipeRepository.removed.isEmpty())

            send(BeforeDeleteIntent.SystemDeleteResult(confirmed = true))

            assertEquals(
                BeforeDeleteEffect.NavigateToResult(date, deletedCount = 2, freedBytes = 400_000_000, keptCount = 1, totalCount = 3),
                awaitItem(),
            )
        }
        assertEquals(listOf(mapOf(1L to 150_000_000L, 2L to 250_000_000L) to PhotoDecision.TRASHED), swipeRepository.confirmed)
    }

    @Test
    fun `a result that arrives before the grid is loaded still records the deletion`() = runTest(dispatcher) {
        val saved = mapOf<String, Any>(
            BeforeDeleteViewModel.PENDING_IDS to longArrayOf(1, 2),
            BeforeDeleteViewModel.PENDING_BYTES to longArrayOf(150_000_000, 250_000_000),
        )
        create(saved)

        viewModel.effects.test {
            // No advanceUntilIdle() before: the answer is the first thing the view model hears
            viewModel.onIntent(BeforeDeleteIntent.SystemDeleteResult(true))
            advanceUntilIdle()

            assertTrue(awaitItem() is BeforeDeleteEffect.NavigateToResult)
        }
        assertEquals(1, swipeRepository.confirmed.size)
    }

    @Test
    fun `a result when no request is open is ignored`() = runTest(dispatcher) {
        load()

        viewModel.effects.test {
            send(BeforeDeleteIntent.SystemDeleteResult(confirmed = true))

            expectNoEvents()
        }
        assertTrue(swipeRepository.confirmed.isEmpty())
    }

    // --- below API 30: no trash, no system dialog ---

    @Test
    @Config(sdk = [29])
    fun `below API 30 the button opens our own dialog and deletes nothing yet`() = runTest(dispatcher) {
        load()

        send(BeforeDeleteIntent.DeleteClicked)

        assertTrue(state.showPermanentDialog)
        coVerify(exactly = 0) { photoRepository.deletePhotos(any()) }
        assertTrue(swipeRepository.confirmed.isEmpty())
    }

    @Test
    @Config(sdk = [29])
    fun `dismissing our dialog changes nothing`() = runTest(dispatcher) {
        load()
        send(BeforeDeleteIntent.DeleteClicked)

        send(BeforeDeleteIntent.PermanentDeleteDismissed)

        assertFalse(state.showPermanentDialog)
        assertFalse(state.isDeleting)
        coVerify(exactly = 0) { photoRepository.deletePhotos(any()) }
    }

    @Test
    @Config(sdk = [29])
    fun `with the permission, confirming our dialog deletes and records the photos as DELETED`() = runTest(dispatcher) {
        shadowOf(application).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        coEvery { photoRepository.deletePhotos(any()) } returns DeleteResult.Deleted
        load()
        send(BeforeDeleteIntent.DeleteClicked)

        viewModel.effects.test {
            send(BeforeDeleteIntent.PermanentDeleteConfirmed)

            assertEquals(
                BeforeDeleteEffect.NavigateToResult(date, deletedCount = 2, freedBytes = 400_000_000, keptCount = 1, totalCount = 3),
                awaitItem(),
            )
        }
        assertFalse(state.showPermanentDialog)
        assertEquals(listOf(mapOf(1L to 150_000_000L, 2L to 250_000_000L) to PhotoDecision.DELETED), swipeRepository.confirmed)
        assertEquals(PhotoDecision.DELETED, swipeRepository.decisions[1])
        coVerifyOrder {
            photoRepository.getPhotoSizes(listOf(1L, 2L))
            photoRepository.deletePhotos(listOf(1L, 2L))
        }
        coVerify(exactly = 1) { photoRepository.refreshOnThisDayIndex() }
    }

    @Test
    @Config(sdk = [28])
    fun `without the permission, confirming our dialog asks for it first and deletes once it is granted`() = runTest(dispatcher) {
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        coEvery { photoRepository.deletePhotos(any()) } returns DeleteResult.Deleted
        load()
        send(BeforeDeleteIntent.DeleteClicked)

        viewModel.effects.test {
            send(BeforeDeleteIntent.PermanentDeleteConfirmed)
            assertEquals(BeforeDeleteEffect.RequestWritePermission, awaitItem())
            coVerify(exactly = 0) { photoRepository.deletePhotos(any()) }

            send(BeforeDeleteIntent.WritePermissionResult(granted = true))

            assertTrue(awaitItem() is BeforeDeleteEffect.NavigateToResult)
        }
        coVerify(exactly = 1) { photoRepository.deletePhotos(listOf(1L, 2L)) }
    }

    @Test
    @Config(sdk = [28])
    fun `a refused permission deletes nothing and says so`() = runTest(dispatcher) {
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        load()
        send(BeforeDeleteIntent.DeleteClicked)

        viewModel.effects.test {
            send(BeforeDeleteIntent.PermanentDeleteConfirmed)
            assertEquals(BeforeDeleteEffect.RequestWritePermission, awaitItem())

            send(BeforeDeleteIntent.WritePermissionResult(granted = false))

            assertEquals(BeforeDeleteEffect.ShowPermissionDenied, awaitItem())
        }
        assertFalse(state.isDeleting)
        coVerify(exactly = 0) { photoRepository.deletePhotos(any()) }
        assertTrue(swipeRepository.confirmed.isEmpty())
    }

    @Test
    @Config(sdk = [29])
    fun `a failed delete below API 30 keeps the marks and says so`() = runTest(dispatcher) {
        shadowOf(application).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        coEvery { photoRepository.deletePhotos(any()) } throws SecurityException("not yours")
        load()
        send(BeforeDeleteIntent.DeleteClicked)

        viewModel.effects.test {
            send(BeforeDeleteIntent.PermanentDeleteConfirmed)

            assertEquals(BeforeDeleteEffect.ShowDeleteError, awaitItem())
        }
        assertFalse(state.isDeleting)
        assertEquals(listOf(1L, 2L), marked())
        assertTrue(swipeRepository.confirmed.isEmpty())
    }
}
