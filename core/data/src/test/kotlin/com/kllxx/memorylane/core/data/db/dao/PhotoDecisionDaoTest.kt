package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import java.time.LocalDate
import java.util.concurrent.Executor
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

@RunWith(RobolectricTestRunner::class)
class PhotoDecisionDaoTest {

    private val day = LocalDate.of(2026, 10, 15)
    private val otherDay = LocalDate.of(2026, 3, 3)
    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var dao: PhotoDecisionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.photoDecisionDao()
    }

    @After
    fun tearDown() = database.close()

    private fun decision(id: Long, date: LocalDate = day, value: String = "KEEP", at: Long = 1_000) =
        PhotoDecisionEntity(photoId = id, sessionDate = date, decision = value, decidedAt = at)

    @Test
    fun `upsert then getAll returns the row`() = runTest {
        dao.upsert(decision(1))

        assertEquals(listOf(decision(1)), dao.getAll())
    }

    @Test
    fun `a photo has one decision, and a later one replaces it`() = runTest {
        dao.upsert(decision(1, value = "KEEP", at = 1_000))
        dao.upsert(decision(1, value = "DELETE", at = 2_000))

        assertEquals(listOf(decision(1, value = "DELETE", at = 2_000)), dao.getAll())
    }

    @Test
    fun `getByDate returns only the decisions of that day`() = runTest {
        dao.upsert(decision(1, day))
        dao.upsert(decision(2, otherDay))
        dao.upsert(decision(3, day))

        assertEquals(listOf(1L, 3L), dao.getByDate(day).map { it.photoId }.sorted())
        assertEquals(listOf(2L), dao.getByDate(otherDay).map { it.photoId })
    }

    @Test
    fun `getById finds the row, or null`() = runTest {
        dao.upsert(decision(1))

        assertEquals(decision(1), dao.getById(1))
        assertNull(dao.getById(2))
    }

    @Test
    fun `deleteById removes only that photo`() = runTest {
        dao.upsert(decision(1))
        dao.upsert(decision(2))

        dao.deleteById(1)

        assertEquals(listOf(2L), dao.getAll().map { it.photoId })
    }

    @Test
    fun `deleteById on an unknown photo changes nothing`() = runTest {
        dao.upsert(decision(1))

        dao.deleteById(99)

        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun `updateDecisions changes only the listed photos, with the new time`() = runTest {
        dao.upsert(decision(1, value = "DELETE", at = 1_000))
        dao.upsert(decision(2, value = "DELETE", at = 1_000))
        dao.upsert(decision(3, value = "KEEP", at = 1_000))

        dao.updateDecisions(listOf(1, 2), "TRASHED", 5_000)

        val byId = dao.getAll().associateBy { it.photoId }
        assertEquals(decision(1, value = "TRASHED", at = 5_000), byId.getValue(1))
        assertEquals(decision(2, value = "TRASHED", at = 5_000), byId.getValue(2))
        assertEquals(decision(3, value = "KEEP", at = 1_000), byId.getValue(3))
    }

    @Test
    fun `getPendingDeletes returns the marked photos of every day and nothing else`() = runTest {
        dao.upsert(decision(1, value = "DELETE"))
        dao.upsert(decision(2, date = otherDay, value = "DELETE"))
        dao.upsert(decision(3, value = "KEEP"))
        dao.upsert(decision(4, value = "TRASHED"))

        assertEquals(listOf(decision(1, value = "DELETE"), decision(2, date = otherDay, value = "DELETE")), dao.getPendingDeletes())
    }

    @Test
    fun `updateDecisions skips a photo that has no decision`() = runTest {
        dao.updateDecisions(listOf(1), "TRASHED", 5_000)

        assertTrue(dao.getAll().isEmpty())
    }
}
