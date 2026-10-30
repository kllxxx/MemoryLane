package com.kllxx.memorylane.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import app.cash.turbine.test
import com.kllxx.memorylane.core.data.datastore.AppSettingsDataStore
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import com.kllxx.memorylane.core.domain.model.AchievementStats
import com.kllxx.memorylane.core.domain.permission.PermissionsState
import com.kllxx.memorylane.core.domain.repository.PermissionsRepository
import com.kllxx.memorylane.core.domain.streak.Streaks
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
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
    @get:Rule
    val tmp = TemporaryFolder()

    private val photoRepository = mockk<PhotoRepository>()
    private val permissions = MutableStateFlow(PermissionsState())
    private val permissionsRepository = mockk<PermissionsRepository> { every { state } returns permissions }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: AppSettingsDataStore

    // The photo index, which tells the empty days; null is "not read yet"
    private val index = MutableStateFlow<OnThisDayIndex?>(null)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        every { photoRepository.onThisDayIndex } returns index
        store = AppSettingsDataStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("settings.preferences_pb") })
        repository = StatsRepositoryImpl(database.reviewedPhotoDao(), database.swipeSessionDao(), photoRepository, permissionsRepository, store, clock)
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
    }

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

    private suspend fun realDay(date: LocalDate) =
        database.swipeSessionDao().insert(SwipeSessionEntity(date, true, 0, completedAt = date))

    @Test
    fun `a streak is made of days completed on their own date`() = runTest {
        realDay(today)
        realDay(today.minusDays(1))
        realDay(today.minusDays(5))
        realDay(today.minusDays(6))
        realDay(today.minusDays(7))

        val stats = repository.stats.first()

        assertEquals(2, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
    }

    @Test
    fun `a day completed on another date does not count, nor does the epoch marker`() = runTest {
        // Completed today but for a day of another date: 15 January done today
        database.swipeSessionDao().insert(SwipeSessionEntity(LocalDate.of(2026, 1, 15), true, 0, completedAt = today))
        database.swipeSessionDao().insert(SwipeSessionEntity(today.minusDays(1), true, 0, completedAt = LocalDate.EPOCH))

        assertEquals(Streaks.None, repository.streaks.first())
    }

    @Test
    fun `an unfinished day does not count`() = runTest {
        database.swipeSessionDao().insert(SwipeSessionEntity(today, false, 0, completedAt = today))

        assertEquals(Streaks.None, repository.streaks.first())
    }

    @Test
    fun `without the photo index no day is known to be empty`() = runTest {
        realDay(today.minusDays(3))

        assertEquals(Streaks(current = 0, best = 1), repository.streaks.first())
    }

    @Test
    fun `days without photos continue a run once the index is there`() = runTest {
        realDay(today.minusDays(3))
        index.value = OnThisDayIndex(currentYear = 2026, entries = emptyMap(), photoYears = emptyMap())

        // Three empty days after the real one
        assertEquals(Streaks(current = 4, best = 4), repository.streaks.first())
    }

    @Test
    fun `a day that has photos in its window is not an empty day`() = runTest {
        realDay(today.minusDays(3))
        val photoYears = mapOf(java.time.MonthDay.from(today.minusDays(1)) to setOf(2020))
        index.value = OnThisDayIndex(currentYear = 2026, entries = emptyMap(), photoYears = photoYears)

        // Day 2 ago is empty and continues, yesterday has photos and ends the run, today is empty but cannot start one
        assertEquals(Streaks(current = 0, best = 2), repository.streaks.first())
    }

    @Test
    fun `the streaks follow the photo index when it changes`() = runTest {
        realDay(today.minusDays(1))

        repository.streaks.test {
            assertEquals(Streaks(1, 1), awaitItem())
            index.value = OnThisDayIndex(currentYear = 2026, entries = emptyMap(), photoYears = emptyMap())
            assertEquals(Streaks(2, 2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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

    // --- without access to the photos ---

    private fun emptyIndex() = OnThisDayIndex(currentYear = 2026, entries = emptyMap(), photoYears = emptyMap())

    @Test
    fun `without access the stored streaks stay, nothing is worked out`() = runTest {
        store.saveStreaks(Streaks(current = 5, best = 9))
        permissions.value = PermissionsState(photos = false)
        // A real day that would make a different streak, and an index that would extend it
        realDay(today.minusDays(3))
        index.value = emptyIndex()

        assertEquals(Streaks(current = 5, best = 9), repository.streaks.first())
    }

    @Test
    fun `without access and nothing stored there is no streak`() = runTest {
        permissions.value = PermissionsState(photos = false)

        assertEquals(Streaks.None, repository.streaks.first())
    }

    @Test
    fun `the stats show the stored streaks while access is missing`() = runTest {
        store.saveStreaks(Streaks(current = 5, best = 9))
        permissions.value = PermissionsState(photos = false)

        val stats = repository.stats.first()

        assertEquals(5, stats.currentStreak)
        assertEquals(9, stats.bestStreak)
    }

    @Test
    fun `with access and the index the streaks are worked out and stored`() = runTest {
        realDay(today.minusDays(3))
        index.value = emptyIndex()

        assertEquals(Streaks(current = 4, best = 4), repository.streaks.first())
        assertEquals(Streaks(current = 4, best = 4), store.lastStreaks())
    }

    @Test
    fun `before the index is read the stored streaks stand in and a guess is not stored`() = runTest {
        store.saveStreaks(Streaks(current = 4, best = 4))
        realDay(today.minusDays(3))

        // Not read yet: worked out on its own the streak would be 0 and 1
        assertEquals(Streaks(current = 4, best = 4), repository.streaks.first())
        assertEquals(Streaks(current = 4, best = 4), store.lastStreaks())
    }

    @Test
    fun `losing access keeps the streak and getting it back works it out again`() = runTest {
        realDay(today.minusDays(3))
        index.value = emptyIndex()

        repository.streaks.test {
            assertEquals(Streaks(4, 4), awaitItem())

            permissions.value = PermissionsState(photos = false)
            // The same values: nothing new is emitted, and the unreadable gallery changed nothing
            expectNoEvents()

            // A day completed meanwhile (the day before the run) is not counted before the gallery can say which are empty
            realDay(today.minusDays(4))
            expectNoEvents()

            permissions.value = PermissionsState(photos = true)
            assertEquals(Streaks(5, 5), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(Streaks(5, 5), store.lastStreaks())
    }

    @Test
    fun `a streak that was zero is stored as zero, not forgotten`() = runTest {
        index.value = emptyIndex()
        assertEquals(Streaks.None, repository.streaks.first())

        permissions.value = PermissionsState(photos = false)

        assertEquals(Streaks.None, repository.streaks.first())
        assertEquals(Streaks.None, store.lastStreaks())
    }
}
