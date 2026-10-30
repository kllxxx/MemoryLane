package com.kllxx.memorylane.core.data.db

import android.content.Context
import androidx.room.Room
import com.kllxx.memorylane.core.data.db.entity.PhotoDateCacheEntity
import com.kllxx.memorylane.core.data.db.entity.PhotoLocationCacheEntity
import com.kllxx.memorylane.core.data.db.entity.ReviewedPhotoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * Builds a version 1 database by hand (the SQL is the one exported to schemas/.../1.json), then lets
 * Room open it with the real migrations and validate the result against the current entities.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
        val v1 = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        v1.execSQL(
            "CREATE TABLE IF NOT EXISTS `swipe_sessions` (`date` INTEGER NOT NULL, " +
                "`completed` INTEGER NOT NULL, `savedBytes` INTEGER NOT NULL, PRIMARY KEY(`date`))",
        )
        v1.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_deletes` (`photoId` INTEGER NOT NULL, " +
                "`sessionDate` INTEGER NOT NULL, PRIMARY KEY(`photoId`))",
        )
        v1.execSQL("INSERT INTO swipe_sessions (date, completed, savedBytes) VALUES (20000, 1, 500)")
        v1.execSQL("INSERT INTO swipe_sessions (date, completed, savedBytes) VALUES (20001, 0, 0)")
        v1.execSQL("INSERT INTO pending_deletes (photoId, sessionDate) VALUES (7, 20001)")
        v1.version = 1
        v1.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `existing sessions survive and get the unknown completion day`() = runTest {
        val sessions = database.swipeSessionDao().observeCompleted().first()

        val completed = sessions.single()
        assertEquals(LocalDate.ofEpochDay(20000), completed.date)
        assertEquals(500L, completed.savedBytes)
        assertEquals(LocalDate.EPOCH, completed.completedAt)
    }

    @Test
    fun `the unfinished session is kept, and the pending delete became a DELETE decision`() = runTest {
        // Not completed, so it is not visible through the DAO queries: read the row directly
        database.openHelper.readableDatabase.query("SELECT completed, completed_at FROM swipe_sessions WHERE date = 20001").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
            assertEquals(0L, it.getLong(1))
        }
        val decision = database.photoDecisionDao().getAll().single { it.photoId == 7L }
        assertEquals("DELETE", decision.decision)
        assertEquals(LocalDate.ofEpochDay(20001), decision.sessionDate)
    }

    @Test
    fun `a migrated decision is stamped with the time of the migration`() = runTest {
        val decision = database.photoDecisionDao().getAll().single { it.photoId == 7L }

        // The real time was never recorded; any plausible clock reading is later than 2026-01-01
        assertTrue(decision.decidedAt > 1_767_225_600_000L)
    }

    @Test
    fun `the pending deletes table is gone`() {
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'pending_deletes'",
        ).use { assertEquals(0, it.count) }
    }

    @Test
    fun `the session date index exists after migrating`() {
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'photo_decisions'",
        ).use {
            val names = generateSequence { if (it.moveToNext()) it.getString(0) else null }.toList()
            assertTrue(names.contains("index_photo_decisions_sessionDate"))
        }
    }

    @Test
    fun `the photo locations table exists, is empty and accepts rows`() = runTest {
        val dao = database.photoLocationCacheDao()
        assertTrue(dao.allIds().isEmpty())

        dao.upsertAll(listOf(PhotoLocationCacheEntity(1, 100, "en", "Paris, France")))

        assertEquals(listOf(1L), dao.allIds())
    }

    @Test
    fun `existing sessions get a zero deleted count`() {
        database.openHelper.readableDatabase.query("SELECT deleted_count FROM swipe_sessions").use {
            while (it.moveToNext()) assertEquals(0, it.getInt(0))
            assertEquals(2, it.count)
        }
    }

    @Test
    fun `the reviewed photos table exists, is empty and accepts rows`() = runTest {
        val dao = database.reviewedPhotoDao()
        assertEquals(0, dao.observeCount().first())

        dao.insert(ReviewedPhotoEntity(1L, LocalDate.of(2026, 10, 15)))

        assertEquals(1, dao.observeCount().first())
    }

    @Test
    fun `the photo date cache table exists, is empty and accepts rows`() = runTest {
        val dao = database.photoDateCacheDao()
        assertTrue(dao.allIds().isEmpty())

        dao.upsertAll(listOf(PhotoDateCacheEntity(1, 100, 1_452_161_700, 10_800, "EXIF")))

        assertEquals(listOf(1L), dao.allIds())
    }

    @Test
    fun `the data of earlier versions is untouched by the cache table`() = runTest {
        assertEquals(500L, database.swipeSessionDao().observeFreedBytes().first())
        assertNotNull(database.photoDecisionDao().getAll().singleOrNull { it.photoId == 7L })
    }

    @Test
    fun `freed bytes of migrated sessions still count`() = runTest {
        assertEquals(500L, database.swipeSessionDao().observeFreedBytes().first())
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }

    @Test
    fun `decisions made before the capture time was stored keep it unknown`() = runTest {
        val decision = database.photoDecisionDao().getById(7L)

        assertNotNull(decision)
        assertEquals("DELETE", decision!!.decision)
        assertEquals(null, decision.takenAt)
    }
}
