package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import com.kllxx.memorylane.core.domain.model.DeletionOutcome
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
class DeletionDaoTest {

    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var dao: DeletionDao
    private val date = LocalDate.of(2015, 1, 15)
    private val today = LocalDate.of(2026, 10, 29)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.deletionDao()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun decide(id: Long, decision: String) =
        database.photoDecisionDao().upsert(PhotoDecisionEntity(id, date, decision, decidedAt = 1))

    private suspend fun decisions() = database.photoDecisionDao().getAll().associate { it.photoId to it.decision }

    private val sizes = mapOf(1L to 100L, 2L to 200L, 3L to 400L)

    @Test
    fun `marked photos become the new decision and are counted with their bytes`() = runTest {
        decide(1, "DELETE"); decide(2, "DELETE"); decide(3, "KEEP")

        val outcome = dao.confirmDeletion(date, sizes, "TRASHED", decidedAt = 5, today = today)

        assertEquals(DeletionOutcome(deletedCount = 2, freedBytes = 300), outcome)
        assertEquals(mapOf(1L to "TRASHED", 2L to "TRASHED", 3L to "KEEP"), decisions())
    }

    @Test
    fun `the session is completed and gets the count and the bytes`() = runTest {
        decide(1, "DELETE"); decide(2, "DELETE")

        dao.confirmDeletion(date, sizes, "TRASHED", 5, today)

        assertEquals(SwipeSessionEntity(date, completed = true, savedBytes = 300, completedAt = today, deletedCount = 2), dao.getSession(date))
    }

    @Test
    fun `confirming the same photos twice counts them once`() = runTest {
        decide(1, "DELETE"); decide(2, "DELETE")

        val first = dao.confirmDeletion(date, sizes, "TRASHED", 5, today)
        val second = dao.confirmDeletion(date, sizes, "TRASHED", 6, today)

        assertEquals(DeletionOutcome(2, 300), first)
        assertEquals(DeletionOutcome(0, 0), second)
        assertEquals(300L, dao.getSession(date)!!.savedBytes)
        assertEquals(2, dao.getSession(date)!!.deletedCount)
    }

    @Test
    fun `a photo that was unmarked meanwhile is not deleted and not counted`() = runTest {
        decide(1, "DELETE"); decide(2, "KEEP")

        val outcome = dao.confirmDeletion(date, sizes, "DELETED", 5, today)

        assertEquals(DeletionOutcome(1, 100), outcome)
        assertEquals("KEEP", decisions().getValue(2))
    }

    @Test
    fun `nothing marked changes nothing, not even the session`() = runTest {
        decide(1, "KEEP")

        val outcome = dao.confirmDeletion(date, sizes, "TRASHED", 5, today)

        assertEquals(DeletionOutcome(0, 0), outcome)
        assertNull(dao.getSession(date))
        assertEquals(mapOf(1L to "KEEP"), decisions())
    }

    @Test
    fun `a day sorted again adds to what it freed`() = runTest {
        val first = LocalDate.of(2026, 10, 1)
        dao.putSession(SwipeSessionEntity(date, completed = true, savedBytes = 1_000, completedAt = first, deletedCount = 5))
        decide(1, "DELETE")

        dao.confirmDeletion(date, sizes, "TRASHED", 5, today)

        val session = dao.getSession(date)!!
        assertEquals(1_100L, session.savedBytes)
        assertEquals(6, session.deletedCount)
    }

    @Test
    fun `a day completed on its own date keeps that date when it is sorted again later`() = runTest {
        dao.putSession(SwipeSessionEntity(date, completed = true, savedBytes = 1_000, completedAt = date, deletedCount = 5))
        decide(1, "DELETE")

        dao.confirmDeletion(date, sizes, "TRASHED", 5, today)

        assertEquals(date, dao.getSession(date)!!.completedAt)
    }

    @Test
    fun `a day completed on another date is completed on the date of the confirmation`() = runTest {
        dao.putSession(SwipeSessionEntity(date, completed = true, savedBytes = 1_000, completedAt = LocalDate.of(2026, 10, 1), deletedCount = 5))
        decide(1, "DELETE")

        dao.confirmDeletion(date, sizes, "TRASHED", 5, today)

        assertEquals(today, dao.getSession(date)!!.completedAt)
    }

    @Test
    fun `a day swiped on its own date and confirmed the next day is completed the next day`() = runTest {
        // Saved as not completed when the last photo was swiped, with the date of that day
        dao.putSession(SwipeSessionEntity(date, completed = false, savedBytes = 0, completedAt = date, deletedCount = 0))
        decide(1, "DELETE")

        dao.confirmDeletion(date, sizes, "TRASHED", 5, today)

        assertEquals(today, dao.getSession(date)!!.completedAt)
    }

    @Test
    fun `a day confirmed on its own date is completed on its own date`() = runTest {
        decide(1, "DELETE")

        dao.confirmDeletion(date, sizes, "TRASHED", 5, today = date)

        assertEquals(date, dao.getSession(date)!!.completedAt)
    }

    @Test
    fun `an unfinished session is completed by the confirmation`() = runTest {
        dao.putSession(SwipeSessionEntity(date, completed = false, savedBytes = 0, completedAt = today, deletedCount = 0))
        decide(2, "DELETE")

        dao.confirmDeletion(date, sizes, "DELETED", 5, today)

        assertEquals(SwipeSessionEntity(date, true, 200, today, 1), dao.getSession(date))
    }

    @Test
    fun `decisions of other days are only touched when their photo is in the request`() = runTest {
        database.photoDecisionDao().upsert(PhotoDecisionEntity(9, LocalDate.of(2014, 1, 15), "DELETE", 1))
        decide(1, "DELETE")

        dao.confirmDeletion(date, sizes, "TRASHED", 5, today)

        assertEquals("DELETE", decisions().getValue(9))
    }
}
