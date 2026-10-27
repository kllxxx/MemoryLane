package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.PhotoDateCacheEntity
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
class PhotoDateCacheDaoTest {

    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var dao: PhotoDateCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.photoDateCacheDao()
    }

    @After
    fun tearDown() = database.close()

    private fun entity(id: Long, modified: Long = 100, seconds: Long? = 1_452_161_700, offset: Int? = 10_800, source: String = "EXIF") =
        PhotoDateCacheEntity(id, modified, seconds, offset, source)

    @Test
    fun `nothing is cached at first`() = runTest {
        assertTrue(dao.getByIds(listOf(1, 2)).isEmpty())
        assertTrue(dao.allIds().isEmpty())
    }

    @Test
    fun `inserted rows are found by id, including their null fields`() = runTest {
        dao.upsertAll(listOf(entity(1), entity(2, seconds = null, offset = null, source = "UNDATED"), entity(3)))

        val found = dao.getByIds(listOf(1, 2, 4)).associateBy { it.id }

        assertEquals(setOf(1L, 2L), found.keys)
        assertEquals(entity(1), found.getValue(1))
        assertNull(found.getValue(2).takenLocalEpochSecond)
        assertNull(found.getValue(2).offsetSeconds)
    }

    @Test
    fun `writing an id again replaces the row`() = runTest {
        dao.upsertAll(listOf(entity(1, modified = 100)))
        dao.upsertAll(listOf(entity(1, modified = 200, seconds = 5)))

        val row = dao.getByIds(listOf(1)).single()

        assertEquals(200L, row.dateModified)
        assertEquals(5L, row.takenLocalEpochSecond)
        assertEquals(1, dao.allIds().size)
    }

    @Test
    fun `rows are deleted by id`() = runTest {
        dao.upsertAll(listOf(entity(1), entity(2), entity(3)))

        dao.deleteByIds(listOf(1, 3))

        assertEquals(listOf(2L), dao.allIds())
    }
}
