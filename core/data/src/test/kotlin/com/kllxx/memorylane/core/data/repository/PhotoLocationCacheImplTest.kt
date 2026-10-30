package com.kllxx.memorylane.core.data.repository

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoLocation
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhotoLocationCacheImplTest {

    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var cache: PhotoLocationCacheImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        cache = PhotoLocationCacheImpl(database.photoLocationCacheDao())
    }

    @After
    fun tearDown() = database.close()

    private fun entry(id: Long, location: String? = "Paris, France") =
        CachedPhotoLocation(photoId = id, dateModified = 100, locale = "fr-FR", location = location)

    @Test
    fun `an entry survives a round trip, with and without a place`() = runTest {
        val entries = listOf(entry(1), entry(2, location = null))

        cache.putAll(entries)

        assertEquals(entries.associateBy { it.photoId }, cache.get(listOf(1, 2)))
    }

    @Test
    fun `get leaves out ids that were never cached`() = runTest {
        cache.putAll(listOf(entry(1)))

        assertEquals(setOf(1L), cache.get(listOf(1, 2, 3)).keys)
    }

    @Test
    fun `get and putAll work past the SQLite variable limit`() = runTest {
        val entries = (1L..1_200L).map { entry(it) }

        cache.putAll(entries)

        assertEquals(1_200, cache.get(entries.map { it.photoId }).size)
    }

    @Test
    fun `retainOnly removes the entries of photos that are gone`() = runTest {
        cache.putAll(listOf(entry(1), entry(2), entry(3)))

        cache.retainOnly(setOf(2L))

        assertEquals(setOf(2L), cache.get(listOf(1, 2, 3)).keys)
    }

    @Test
    fun `retainOnly with every id keeps everything`() = runTest {
        cache.putAll(listOf(entry(1), entry(2)))

        cache.retainOnly(setOf(1L, 2L))

        assertEquals(2, cache.get(listOf(1, 2)).size)
    }

    @Test
    fun `clear removes every entry, so every place is looked up again`() = runTest {
        cache.putAll(listOf(entry(1), entry(2, location = null), entry(3)))

        cache.clear()

        assertEquals(emptyMap<Long, CachedPhotoLocation>(), cache.get(listOf(1L, 2L, 3L)))
    }
}
