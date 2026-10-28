package com.kllxx.memorylane.core.data.db

import android.content.Context
import androidx.room.Room
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Builds a version 6 database by hand (the SQL is the one exported to schemas/.../6.json) with date cache rows in it,
 * then lets Room open it with MIGRATION_6_7 and validate the result against the current entities.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationHasTimeTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase

    private fun seconds(hour: Int, minute: Int = 0, second: Int = 0) =
        LocalDateTime.of(2016, 1, 7, hour, minute, second).toEpochSecond(ZoneOffset.UTC)

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
        val v6 = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_decisions` (`photoId` INTEGER NOT NULL, `sessionDate` INTEGER NOT NULL, " +
                "`decision` TEXT NOT NULL, `decided_at` INTEGER NOT NULL, PRIMARY KEY(`photoId`))",
        )
        v6.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_decisions_sessionDate` ON `photo_decisions` (`sessionDate`)")
        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `swipe_sessions` (`date` INTEGER NOT NULL, `completed` INTEGER NOT NULL, " +
                "`savedBytes` INTEGER NOT NULL, `completed_at` INTEGER NOT NULL DEFAULT 0, " +
                "`deleted_count` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`date`))",
        )
        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `reviewed_photos` (`mediaId` INTEGER NOT NULL, " +
                "`firstReviewedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))",
        )
        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_date_cache` (`id` INTEGER NOT NULL, `date_modified` INTEGER NOT NULL, " +
                "`taken_local_epoch_second` INTEGER, `offset_seconds` INTEGER, `source` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        v6.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_locations` (`photoId` INTEGER NOT NULL, `date_modified` INTEGER NOT NULL, " +
                "`locale` TEXT NOT NULL, `location` TEXT, PRIMARY KEY(`photoId`))",
        )
        fun date(id: Long, seconds: Long?, source: String) = v6.execSQL(
            "INSERT INTO photo_date_cache (id, date_modified, taken_local_epoch_second, offset_seconds, source) " +
                "VALUES ($id, 100, ${seconds ?: "NULL"}, NULL, '$source')",
        )
        // 1: a file name without a time, stored as noon; 2: a file name with a real 10:15; 3: a file name with a real
        // 12:00:00 (the same row as 1, see MIGRATION_6_7); 4: EXIF at noon; 5: EXIF at midnight; 6: undated
        date(1, seconds(12), "FILENAME")
        date(2, seconds(10, 15), "FILENAME")
        date(3, seconds(12), "FILENAME")
        date(4, seconds(12), "EXIF")
        date(5, seconds(0), "EXIF")
        date(6, null, "UNDATED")
        v6.version = 6
        v6.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    private suspend fun hasTime() =
        database.photoDateCacheDao().getByIds(listOf(1, 2, 3, 4, 5, 6)).associate { it.id to it.hasTime }

    @Test
    fun `every row survives the migration with its date`() = runTest {
        val rows = database.photoDateCacheDao().getByIds(listOf(1, 2, 3, 4, 5, 6)).associateBy { it.id }

        assertEquals(6, rows.size)
        assertEquals(seconds(10, 15), rows.getValue(2).takenLocalEpochSecond)
        assertEquals("EXIF", rows.getValue(5).source)
        assertEquals(null, rows.getValue(6).takenLocalEpochSecond)
    }

    @Test
    fun `a file name date stored at noon becomes date-only`() = runTest {
        val flags = hasTime()

        assertEquals(false, flags.getValue(1))
        // The same row as 1 in the database, so it cannot be told apart; the flag is set again when the file changes
        assertEquals(false, flags.getValue(3))
    }

    @Test
    fun `a file name with another time keeps its time`() = runTest {
        assertEquals(true, hasTime().getValue(2))
    }

    @Test
    fun `EXIF dates keep their time, also at noon and at midnight`() = runTest {
        val flags = hasTime()

        assertEquals(true, flags.getValue(4))
        assertEquals(true, flags.getValue(5))
    }

    @Test
    fun `an undated row keeps a time flag of true`() = runTest {
        assertEquals(true, hasTime().getValue(6))
    }

    private companion object {
        const val DB_NAME = "migration-has-time-test.db"
    }
}
