package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import app.cash.turbine.test
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SwipeSessionDaoTest {

    private val monday = LocalDate.of(2026, 10, 12)
    private val tuesday = LocalDate.of(2026, 10, 13)
    private val wednesday = LocalDate.of(2026, 10, 14)
    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var dao: SwipeSessionDao

    /** By default a session was completed on the day it is about. */
    private fun session(
        date: LocalDate,
        completed: Boolean,
        savedBytes: Long,
        completedAt: LocalDate = date,
    ) = SwipeSessionEntity(date, completed, savedBytes, completedAt)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.swipeSessionDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `insert then observeCompleted returns the row`() = runTest {
        val session = session(monday, completed = true, savedBytes = 100, completedAt = wednesday)
        dao.insert(session)
        assertEquals(listOf(session), dao.observeCompleted().first())
    }

    @Test
    fun `inserting the same date replaces the session`() = runTest {
        dao.insert(session(monday, completed = false, savedBytes = 0))
        dao.insert(session(monday, completed = true, savedBytes = 300))
        assertEquals(listOf(session(monday, completed = true, savedBytes = 300)), dao.observeCompleted().first())
    }

    @Test
    fun `getCompletedOn finds a completed session by the day it was completed`() = runTest {
        // Sorted on Wednesday although it is about Monday
        dao.insert(session(monday, completed = true, savedBytes = 1, completedAt = wednesday))
        assertEquals(monday, dao.getCompletedOn(wednesday)?.date)
    }

    @Test
    fun `getCompletedOn does not match the date the session is about`() = runTest {
        dao.insert(session(monday, completed = true, savedBytes = 1, completedAt = wednesday))
        assertNull(dao.getCompletedOn(monday))
    }

    @Test
    fun `getCompletedOn ignores sessions that are not completed`() = runTest {
        dao.insert(session(monday, completed = false, savedBytes = 0, completedAt = wednesday))
        assertNull(dao.getCompletedOn(wednesday))
    }

    @Test
    fun `observeCompleted keeps only completed sessions, newest first`() = runTest {
        dao.insert(session(monday, completed = true, savedBytes = 1))
        dao.insert(session(tuesday, completed = false, savedBytes = 0))
        dao.insert(session(wednesday, completed = true, savedBytes = 3))

        dao.observeCompleted().test {
            assertEquals(listOf(wednesday, monday), awaitItem().map { it.date })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeCompleted emits again when a session is completed`() = runTest {
        dao.insert(session(monday, completed = false, savedBytes = 0))

        dao.observeCompleted().test {
            assertEquals(emptyList<SwipeSessionEntity>(), awaitItem())
            dao.insert(session(monday, completed = true, savedBytes = 42))
            assertEquals(listOf(session(monday, completed = true, savedBytes = 42)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals are zero without sessions`() = runTest {
        assertEquals(0L, dao.observeFreedBytes().first())
        assertEquals(0, dao.observeDeletedCount().first())
    }

    @Test
    fun `totals add up the freed bytes and deleted photos of every session`() = runTest {
        dao.insert(session(monday, completed = true, savedBytes = 100).copy(deletedCount = 2))
        dao.insert(session(tuesday, completed = true, savedBytes = 50).copy(deletedCount = 1))

        assertEquals(150L, dao.observeFreedBytes().first())
        assertEquals(3, dao.observeDeletedCount().first())
    }

    @Test
    fun `totals emit again when a session is replaced`() = runTest {
        dao.insert(session(monday, completed = true, savedBytes = 100).copy(deletedCount = 2))

        dao.observeDeletedCount().test {
            assertEquals(2, awaitItem())
            dao.insert(session(monday, completed = true, savedBytes = 160).copy(deletedCount = 5))
            assertEquals(5, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
