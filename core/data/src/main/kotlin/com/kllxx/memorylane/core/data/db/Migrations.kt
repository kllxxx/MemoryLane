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
