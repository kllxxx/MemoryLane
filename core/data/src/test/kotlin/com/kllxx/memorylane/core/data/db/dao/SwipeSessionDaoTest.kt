package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import app.cash.turbine.test
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import java.time.LocalDate
import java.util.concurrent.Executor
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
    fun `getLast is null when there are no sessions`() = runTest {
        assertNull(dao.getLast())
    }

    @Test
    fun `insert then getLast returns the row`() = runTest {
        val session = SwipeSessionEntity(monday, completed = true, savedBytes = 100)
        dao.insert(session)
        assertEquals(session, dao.getLast())
    }

    @Test
    fun `getLast returns the latest date whether or not it is completed`() = runTest {
        dao.insert(SwipeSessionEntity(tuesday, completed = true, savedBytes = 10))
        dao.insert(SwipeSessionEntity(wednesday, completed = false, savedBytes = 0))
        dao.insert(SwipeSessionEntity(monday, completed = true, savedBytes = 5))
        assertEquals(wednesday, dao.getLast()?.date)
    }

    @Test
    fun `inserting the same date replaces the session`() = runTest {
        dao.insert(SwipeSessionEntity(monday, completed = false, savedBytes = 0))
        dao.insert(SwipeSessionEntity(monday, completed = true, savedBytes = 300))
        assertEquals(SwipeSessionEntity(monday, completed = true, savedBytes = 300), dao.getLast())
    }

    @Test
    fun `observeCompleted keeps only completed sessions, newest first`() = runTest {
        dao.insert(SwipeSessionEntity(monday, completed = true, savedBytes = 1))
        dao.insert(SwipeSessionEntity(tuesday, completed = false, savedBytes = 0))
        dao.insert(SwipeSessionEntity(wednesday, completed = true, savedBytes = 3))

        dao.observeCompleted().test {
            assertEquals(listOf(wednesday, monday), awaitItem().map { it.date })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeCompleted emits again when a session is completed`() = runTest {
        dao.insert(SwipeSessionEntity(monday, completed = false, savedBytes = 0))

        dao.observeCompleted().test {
            assertEquals(emptyList<SwipeSessionEntity>(), awaitItem())
            dao.insert(SwipeSessionEntity(monday, completed = true, savedBytes = 42))
            assertEquals(listOf(SwipeSessionEntity(monday, completed = true, savedBytes = 42)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
