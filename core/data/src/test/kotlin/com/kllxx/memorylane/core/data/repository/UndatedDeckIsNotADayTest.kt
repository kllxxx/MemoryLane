package com.kllxx.memorylane.core.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.streak.Streaks
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The undated deck is stored like a day but is never one. Real in-memory Room, because what keeps it out of the streaks,
 * the sorted days and the daily routing is the queries.
 */
@RunWith(RobolectricTestRunner::class)
class UndatedDeckIsNotADayTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val zone = ZoneId.of("UTC")
    private val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)
    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var swipe: SwipeRepositoryImpl
    private lateinit var stats: StatsRepositoryImpl
    private val photoRepository = mockk<PhotoRepository>()
    private val index = MutableStateFlow<OnThisDayIndex?>(null)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        every { photoRepository.onThisDayIndex } returns index
        swipe = SwipeRepositoryImpl(database.photoDecisionDao(), database.swipeSessionDao(), database.deletionDao(), clock)
        stats = StatsRepositoryImpl(database.reviewedPhotoDao(), database.swipeSessionDao(), photoRepository, clock)
    }

    @After
    fun tearDown() = database.close()

    private val undatedMarks = mapOf(21L to 4_000L, 22L to 6_000L)

    /** Two photos of the undated deck are marked and the deletion is confirmed. */
    private suspend fun deleteFromUndatedDeck() {
        swipe.saveDecision(21, DeckKey.Undated, PhotoDecision.DELETE)
        swipe.saveDecision(22, DeckKey.Undated, PhotoDecision.DELETE)
        swipe.confirmDeletion(undatedMarks, PhotoDecision.TRASHED)
    }

    private suspend fun realDay(date: LocalDate) = swipe.saveSession(SwipeSession(date, true, 0, completedAt = date))

    // --- decisions ---

    @Test
    fun `the decisions of the undated deck and of a day are apart`() = runTest {
        swipe.saveDecision(1, DeckKey.Undated, PhotoDecision.KEEP)
        swipe.saveDecision(2, DeckKey.Day(today), PhotoDecision.DELETE)

        assertEquals(mapOf(1L to PhotoDecision.KEEP), swipe.getDecisions(DeckKey.Undated))
        assertEquals(mapOf(2L to PhotoDecision.DELETE), swipe.getDecisions(DeckKey.Day(today)))
    }

    @Test
    fun `the pending deletes tell the undated deck from the days`() = runTest {
        swipe.saveDecision(1, DeckKey.Undated, PhotoDecision.DELETE)
        swipe.saveDecision(2, DeckKey.Day(today), PhotoDecision.DELETE)
        swipe.saveDecision(3, DeckKey.Undated, PhotoDecision.KEEP)

        assertEquals(
            mapOf(DeckKey.Undated to setOf(1L), DeckKey.Day(today) to setOf(2L)),
            swipe.getPendingDeletes(),
        )
    }

    // --- a photo that gets a date moves to its day and keeps its decision ---

    @Test
    fun `a photo that got a date takes its decision to its day and leaves the undated deck`() = runTest {
        swipe.saveDecision(1, DeckKey.Undated, PhotoDecision.KEEP)
        swipe.saveDecision(2, DeckKey.Undated, PhotoDecision.DELETE)
        swipe.saveDecision(3, DeckKey.Undated, PhotoDecision.KEEP)

        swipe.adoptUndatedDecisions(listOf(1, 2), LocalDate.of(2021, 6, 22))

        val day = DeckKey.Day(LocalDate.of(2021, 6, 22))
        assertEquals(mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE), swipe.getDecisions(day))
        assertEquals(mapOf(3L to PhotoDecision.KEEP), swipe.getDecisions(DeckKey.Undated))
        assertEquals(mapOf(day to setOf(2L)), swipe.getPendingDeletes())
    }

    @Test
    fun `adopting leaves the decisions made in a day alone`() = runTest {
        swipe.saveDecision(1, DeckKey.Day(today), PhotoDecision.KEEP)

        swipe.adoptUndatedDecisions(listOf(1), LocalDate.of(2021, 6, 22))

        assertEquals(mapOf(1L to PhotoDecision.KEEP), swipe.getDecisions(DeckKey.Day(today)))
        assertTrue(swipe.getDecisions(DeckKey.Day(LocalDate.of(2021, 6, 22))).isEmpty())
    }

    @Test
    fun `adopting a photo without a decision creates none`() = runTest {
        swipe.adoptUndatedDecisions(listOf(1), LocalDate.of(2021, 6, 22))

        assertTrue(swipe.getDecisions(DeckKey.Day(LocalDate.of(2021, 6, 22))).isEmpty())
        assertTrue(swipe.getDecisions(DeckKey.Undated).isEmpty())
    }

    // --- deleting from the undated deck goes to the stats ---

    @Test
    fun `deleting from the undated deck is counted in the deleted photos and the freed bytes`() = runTest {
        deleteFromUndatedDeck()

        val achievements = stats.stats.first()

        assertEquals(2, achievements.deletedPhotos)
        assertEquals(10_000L, achievements.freedBytes)
        assertEquals(10_000L, stats.freedBytes.first())
    }

    @Test
    fun `the deleted photos of the undated deck are TRASHED in the undated deck`() = runTest {
        deleteFromUndatedDeck()

        assertEquals(
            mapOf(21L to PhotoDecision.TRASHED, 22L to PhotoDecision.TRASHED),
            swipe.getDecisions(DeckKey.Undated),
        )
        assertTrue(swipe.getPendingDeletes().isEmpty())
    }

    @Test
    fun `the totals of the days and of the undated deck add up`() = runTest {
        swipe.saveSession(SwipeSession(LocalDate.of(2025, 3, 3), true, 500, completedAt = today, deletedCount = 3))
        deleteFromUndatedDeck()

        val achievements = stats.stats.first()

        assertEquals(5, achievements.deletedPhotos)
        assertEquals(10_500L, achievements.freedBytes)
    }

    // --- it is never a day: the streaks ---

    @Test
    fun `deleting from the undated deck starts no streak`() = runTest {
        deleteFromUndatedDeck()

        val achievements = stats.stats.first()

        assertEquals(0, achievements.currentStreak)
        assertEquals(0, achievements.bestStreak)
        stats.streaks.test {
            assertEquals(Streaks.None, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the undated deck does not add to a streak, current or best`() = runTest {
        realDay(today)
        realDay(today.minusDays(1))
        realDay(today.minusDays(5))
        realDay(today.minusDays(6))
        realDay(today.minusDays(7))
        val before = stats.streaks.first()

        deleteFromUndatedDeck()

        assertEquals(Streaks(current = 2, best = 3), before)
        assertEquals(before, stats.streaks.first())
    }

    @Test
    fun `the undated deck does not fill the gap of a streak`() = runTest {
        realDay(today)
        realDay(today.minusDays(2))
        index.value = OnThisDayIndex(2026, emptyMap(), photoYears = mapOf(MonthDay.from(today.minusDays(1)) to setOf(2020)))

        deleteFromUndatedDeck()

        assertEquals(Streaks(current = 1, best = 1), stats.streaks.first())
    }

    // --- never a day: the calendar's sorted days and the daily routing ---

    @Test
    fun `the undated deck is not among the completed sessions, so it is never a sorted day`() = runTest {
        realDay(today.minusDays(3))
        deleteFromUndatedDeck()

        val sessions = swipe.getCompletedSessions().first()

        assertEquals(listOf(today.minusDays(3)), sessions.map { it.date })
    }

    @Test
    fun `the undated deck finished today is not the session completed today`() = runTest {
        deleteFromUndatedDeck()

        assertNull(swipe.getSessionByCompletedAt(today))
    }

    @Test
    fun `a day completed today is still the session completed today next to the undated deck`() = runTest {
        deleteFromUndatedDeck()
        realDay(today)

        assertEquals(today, swipe.getSessionByCompletedAt(today)?.date)
    }

    @Test
    fun `the completed sessions flow does not emit the undated deck`() = runTest {
        swipe.getCompletedSessions().test {
            assertTrue(awaitItem().isEmpty())

            deleteFromUndatedDeck()

            // The session of the undated deck is written, but the filtered query's result is still nothing
            val next = expectMostRecentItem()
            assertTrue(next.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session saved for a day is a day and counts`() = runTest {
        realDay(today)

        assertEquals(Streaks(current = 1, best = 1), stats.streaks.first())
        assertEquals(listOf(today), swipe.getCompletedSessions().first().map { it.date })
    }
}
