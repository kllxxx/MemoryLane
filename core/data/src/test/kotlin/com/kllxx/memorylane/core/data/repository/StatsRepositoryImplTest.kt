package com.kllxx.memorylane.core.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import com.kllxx.memorylane.core.domain.model.AchievementStats
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Real in-memory Room: the counting rules live in the queries, so mocked DAOs would prove nothing. */
@RunWith(RobolectricTestRunner::class)
class StatsRepositoryImplTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val zone = ZoneId.of("UTC")
    private val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)
    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var repository: StatsRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        repository = StatsRepositoryImpl(database.reviewedPhotoDao(), database.swipeSessionDao(), clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun session(completedAt: LocalDate, savedBytes: Long = 0, deletedCount: Int = 0, dateOffset: Long = 0) =
        database.swipeSessionDao().insert(
            SwipeSessionEntity(completedAt.minusYears(1).minusDays(dateOffset), true, savedBytes, completedAt, deletedCount),
        )

    @Test
    fun `nothing stored gives zeros`() = runTest {
        assertEquals(AchievementStats(0, 0, 0, 0, 0L), repository.stats.first())
    }

    @Test
    fun `markReviewed with the same id twice counts one photo`() = runTest {
        repository.markReviewed(7L)
        repository.markReviewed(7L)

        assertEquals(1, repository.stats.first().viewedPhotos)
    }

    @Test
    fun `markReviewed counts different ids separately`() = runTest {
        repository.markReviewed(1L)
        repository.markReviewed(2L)

        assertEquals(2, repository.stats.first().viewedPhotos)
    }

    @Test
    fun `deleted photos and freed bytes add up across sessions`() = runTest {
        session(today, savedBytes = 500, deletedCount = 3)
        session(today.minusDays(1), savedBytes = 250, deletedCount = 2, dateOffset = 1)

        val stats = repository.stats.first()

        assertEquals(5, stats.deletedPhotos)
        assertEquals(750L, stats.freedBytes)
        assertEquals(750L, repository.freedBytes.first())
    }

    @Test
    fun `streaks come from the completion days and skip the epoch marker`() = runTest {
        session(today, dateOffset = 0)
        session(today.minusDays(1), dateOffset = 1)
        session(today.minusDays(5), dateOffset = 5)
        session(today.minusDays(6), dateOffset = 6)
        session(today.minusDays(7), dateOffset = 7)
        session(LocalDate.EPOCH, dateOffset = 20)

        val stats = repository.stats.first()

        assertEquals(2, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
    }

    @Test
    fun `stats emit again when a photo is reviewed`() = runTest {
        repository.stats.test {
            assertEquals(0, awaitItem().viewedPhotos)
            repository.markReviewed(9L)
            assertEquals(1, awaitItem().viewedPhotos)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
