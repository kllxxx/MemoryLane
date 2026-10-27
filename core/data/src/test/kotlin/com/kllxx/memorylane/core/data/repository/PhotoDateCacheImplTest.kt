package com.kllxx.memorylane.core.data.repository

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.PhotoDateCacheEntity
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.DateSource
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
class PhotoDateCacheImplTest {

    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var cache: PhotoDateCacheImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        cache = PhotoDateCacheImpl(database.photoDateCacheDao())
    }

    @After
    fun tearDown() = database.close()

    private fun entry(id: Long, source: DateSource = DateSource.EXIF) = CachedPhotoDate(
        id = id,
        dateModified = 100,
        takenLocalEpochSecond = if (source == DateSource.UNDATED) null else 1_452_161_700,
        offsetSeconds = if (source == DateSource.EXIF) 10_800 else null,
        source = source,
    )

    @Test
    fun `an entry survives a round trip with every source`() = runTest {
        val entries = listOf(entry(1, DateSource.EXIF), entry(2, DateSource.FILENAME), entry(3, DateSource.UNDATED))

        cache.putAll(entries)

        assertEquals(entries.associateBy { it.id }, cache.get(listOf(1, 2, 3)))
    }

    @Test
    fun `only the asked ids are returned`() = runTest {
        cache.putAll(listOf(entry(1), entry(2)))

        assertEquals(setOf(2L), cache.get(listOf(2, 9)).keys)
    }

    @Test
    fun `thousands of ids are looked up and written in chunks`() = runTest {
        val entries = (1L..1_500L).map { entry(it) }

        cache.putAll(entries)

        assertEquals(1_500, cache.get(entries.map { it.id }).size)
    }

    @Test
    fun `retainOnly drops everything else, in chunks`() = runTest {
        cache.putAll((1L..1_200L).map { entry(it) })

        cache.retainOnly(setOf(5L, 1_100L))

        assertEquals(setOf(5L, 1_100L), cache.get((1L..1_200L).toList()).keys)
    }

    @Test
    fun `retainOnly with no ids empties the cache`() = runTest {
        cache.putAll(listOf(entry(1), entry(2)))

        cache.retainOnly(emptySet())

        assertTrue(cache.get(listOf(1, 2)).isEmpty())
    }

    @Test
    fun `a source this version does not know reads as undated`() {
        val entity = PhotoDateCacheEntity(1, 100, 5, null, source = "SOMETHING_NEW")

        assertEquals(DateSource.UNDATED, entity.toDomain().source)
    }

    @Test
    fun `mapping to the entity keeps every field`() {
        val entity = entry(1, DateSource.FILENAME).toEntity()

        assertEquals(PhotoDateCacheEntity(1, 100, 1_452_161_700, null, "FILENAME"), entity)
    }
}
