package com.kllxx.memorylane.core.data.repository

import androidx.room.Room
import com.kllxx.memorylane.core.data.db.AppDatabase
import com.kllxx.memorylane.core.data.db.entity.PhotoDateCacheEntity
import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhotoDateCacheImplTest {

    private val directExecutor = Executor { it.run() }
    private lateinit var database: AppDatabase
    private lateinit var cache: PhotoDateCacheImpl
    private lateinit var settings: DataStore<Preferences>
    private lateinit var settingsScope: CoroutineScope

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = PreferenceDataStoreFactory.create(scope = settingsScope) { tmp.root.resolve("settings.preferences_pb") }
        cache = PhotoDateCacheImpl(database.photoDateCacheDao(), settings)
    }

    @After
    fun tearDown() {
        database.close()
        settingsScope.cancel()
    }

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
    fun `getAllDated returns every entry that has a date, as domain entries`() = runTest {
        cache.putAll(listOf(entry(1, DateSource.EXIF), entry(2, DateSource.UNDATED), entry(3, DateSource.FILENAME)))

        assertEquals(
            listOf(entry(1, DateSource.EXIF), entry(3, DateSource.FILENAME)),
            cache.getAllDated().sortedBy { it.id },
        )
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

    @Test
    fun `the has-time flag survives a round trip, false and true`() = runTest {
        val dateOnly = entry(1, DateSource.FILENAME).copy(hasTime = false)
        val withTime = entry(2, DateSource.FILENAME).copy(hasTime = true)

        cache.putAll(listOf(dateOnly, withTime))

        val read = cache.get(listOf(1, 2))
        assertEquals(false, read.getValue(1).hasTime)
        assertEquals(true, read.getValue(2).hasTime)
        assertEquals(listOf(dateOnly, withTime), cache.getAllDated().sortedBy { it.id })
    }

    @Test
    fun `every source survives a round trip, also the GPS and XMP ones`() = runTest {
        val entries = listOf(entry(1, DateSource.GPS), entry(2, DateSource.XMP))

        cache.putAll(entries)

        assertEquals(entries.associateBy { it.id }, cache.get(listOf(1, 2)))
        assertEquals(entries, cache.getAllDated().sortedBy { it.id })
    }

    @Test
    fun `forgetGuessedBefore drops the undated and file-name entries and keeps the others`() = runTest {
        cache.putAll(listOf(entry(1, DateSource.UNDATED), entry(2, DateSource.EXIF), entry(3, DateSource.FILENAME), entry(4, DateSource.UNDATED)))

        cache.forgetGuessedBefore(3)

        assertEquals(setOf(2L), cache.get(listOf(1, 2, 3, 4)).keys)
    }

    @Test
    fun `forgetGuessedBefore runs once for a version`() = runTest {
        cache.forgetGuessedBefore(3)
        cache.putAll(listOf(entry(1, DateSource.UNDATED)))

        cache.forgetGuessedBefore(3)

        assertEquals(setOf(1L), cache.get(listOf(1)).keys)
    }

    @Test
    fun `forgetGuessedBefore runs again for a newer version`() = runTest {
        cache.forgetGuessedBefore(3)
        cache.putAll(listOf(entry(1, DateSource.UNDATED)))

        cache.forgetGuessedBefore(4)

        assertTrue(cache.get(listOf(1)).isEmpty())
    }

    @Test
    fun `forgetGuessedBefore remembers the version in the settings`() = runTest {
        cache.forgetGuessedBefore(3)

        assertEquals(3, settings.data.first()[intPreferencesKey("date_chain_version")])
    }

    @Test
    fun `an older version never runs it again`() = runTest {
        settings.edit { it[intPreferencesKey("date_chain_version")] = 5 }
        cache.putAll(listOf(entry(1, DateSource.UNDATED)))

        cache.forgetGuessedBefore(3)

        assertEquals(setOf(1L), cache.get(listOf(1)).keys)
        assertEquals(5, settings.data.first()[intPreferencesKey("date_chain_version")])
    }

    @Test
    fun `an entry written without the flag has a time`() = runTest {
        cache.putAll(listOf(entry(1)))

        assertEquals(true, cache.get(listOf(1)).getValue(1).hasTime)
    }
}
