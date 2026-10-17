package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.PendingDeleteEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PendingDeleteDaoTest {

    private val day = LocalDate.of(2026, 10, 15)
    private val otherDay = LocalDate.of(2026, 3, 3)
    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var dao: PendingDeleteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.pendingDeleteDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `insert then getAll returns the row`() = runTest {
        dao.insert(PendingDeleteEntity(1, day))
        assertEquals(listOf(PendingDeleteEntity(1, day)), dao.getAll())
    }

    @Test
    fun `getAll is empty when nothing was inserted`() = runTest {
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `inserting the same photo again replaces the row`() = runTest {
        dao.insert(PendingDeleteEntity(1, otherDay))
        dao.insert(PendingDeleteEntity(1, day))
        assertEquals(listOf(PendingDeleteEntity(1, day)), dao.getAll())
    }

    @Test
    fun `deleteByPhotoId removes only that photo`() = runTest {
        dao.insert(PendingDeleteEntity(1, day))
        dao.insert(PendingDeleteEntity(2, day))
        dao.deleteByPhotoId(1)
        assertEquals(listOf(PendingDeleteEntity(2, day)), dao.getAll())
    }

    @Test
    fun `clear removes every row`() = runTest {
        dao.insert(PendingDeleteEntity(1, day))
        dao.insert(PendingDeleteEntity(2, otherDay))
        dao.clear()
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `clearByDate removes only that date`() = runTest {
        dao.insert(PendingDeleteEntity(1, day))
        dao.insert(PendingDeleteEntity(2, otherDay))
        dao.clearByDate(day)
        assertEquals(listOf(PendingDeleteEntity(2, otherDay)), dao.getAll())
    }

    @Test
    fun `clearForCompletedSessions removes only the pending deletes of completed sessions`() = runTest {
        val sessions = database.swipeSessionDao()
        sessions.insert(SwipeSessionEntity(day, completed = true, savedBytes = 1, completedAt = day))
        sessions.insert(SwipeSessionEntity(otherDay, completed = false, savedBytes = 0, completedAt = otherDay))
        val thirdDay = LocalDate.of(2026, 5, 5)
        dao.insert(PendingDeleteEntity(1, day))
        dao.insert(PendingDeleteEntity(2, otherDay))
        dao.insert(PendingDeleteEntity(3, thirdDay))

        dao.clearForCompletedSessions()

        // The incomplete session and the date without any session keep their rows
        assertEquals(
            listOf(PendingDeleteEntity(2, otherDay), PendingDeleteEntity(3, thirdDay)),
            dao.getAll().sortedBy { it.photoId },
        )
    }
}
