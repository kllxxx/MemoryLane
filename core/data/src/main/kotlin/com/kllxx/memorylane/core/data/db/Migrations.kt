package com.kllxx.memorylane.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the day a session was completed. Existing rows get epoch day 0 (1970-01-01), which
 * means "unknown" because the real day was never recorded.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE swipe_sessions ADD COLUMN completed_at INTEGER NOT NULL DEFAULT 0")
    }
}

/** Indexes pending deletes by session date, which is how they are read and cleared. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_deletes_sessionDate` ON `pending_deletes` (`sessionDate`)")
    }
}

/** Adds the unique reviewed photos and the number of photos each session deleted; past deletions were not counted, so they stay 0. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reviewed_photos` (`mediaId` INTEGER NOT NULL, " +
                "`firstReviewedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))",
        )
        db.execSQL("ALTER TABLE swipe_sessions ADD COLUMN deleted_count INTEGER NOT NULL DEFAULT 0")
    }
}

/** Adds the cache of dates found in the files of photos that MediaStore has no date for; it is empty until the first scan. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_date_cache` (`id` INTEGER NOT NULL, `date_modified` INTEGER NOT NULL, " +
                "`taken_local_epoch_second` INTEGER, `offset_seconds` INTEGER, `source` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
