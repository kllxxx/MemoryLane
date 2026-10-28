package com.kllxx.memorylane.core.data.db.dao

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.PhotoLocationCacheEntity
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
class PhotoLocationCacheDaoTest {

    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var dao: PhotoLocationCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        dao = database.photoLocationCacheDao()
    }

    @After
    fun tearDown() = database.close()

    private fun entry(id: Long, location: String? = "Paris, France", modified: Long = 100, locale: String = "en") =
        PhotoLocationCacheEntity(photoId = id, dateModified = modified, locale = locale, location = location)

    @Test
    fun `upsertAll then getByIds returns the rows`() = runTest {
        dao.upsertAll(listOf(entry(1), entry(2, "Berlin, Germany")))

        assertEquals(listOf(entry(1), entry(2, "Berlin, Germany")), dao.getByIds(listOf(1, 2)).sortedBy { it.photoId })
    }

    @Test
    fun `a photo without a place is a row with a null location`() = runTest {
        dao.upsertAll(listOf(entry(1, location = null)))

        val row = dao.getByIds(listOf(1)).single()
        assertNull(row.location)
    }

    @Test
    fun `upserting the same photo replaces the row`() = runTest {
        dao.upsertAll(listOf(entry(1, "Paris, France", modified = 100)))
        dao.upsertAll(listOf(entry(1, "Lyon, France", modified = 200)))

        assertEquals(listOf(entry(1, "Lyon, France", modified = 200)), dao.getByIds(listOf(1)))
    }

    @Test
    fun `getByIds leaves out photos that were never cached`() = runTest {
        dao.upsertAll(listOf(entry(1)))

        assertEquals(listOf(1L), dao.getByIds(listOf(1, 2)).map { it.photoId })
    }

    @Test
    fun `allIds and deleteByIds`() = runTest {
        dao.upsertAll(listOf(entry(1), entry(2), entry(3)))

        dao.deleteByIds(listOf(1, 3))

        assertEquals(listOf(2L), dao.allIds())
    }
}
