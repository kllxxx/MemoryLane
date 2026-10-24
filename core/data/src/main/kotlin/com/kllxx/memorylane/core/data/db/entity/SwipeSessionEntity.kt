package com.kllxx.memorylane.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "swipe_sessions")
data class SwipeSessionEntity(
    @PrimaryKey val date: LocalDate,
    val completed: Boolean,
    val savedBytes: Long,
    // The defaults must match MIGRATION_1_2 and MIGRATION_3_4 or Room rejects the migrated schema
    @ColumnInfo(name = "completed_at", defaultValue = "0") val completedAt: LocalDate,
    @ColumnInfo(name = "deleted_count", defaultValue = "0") val deletedCount: Int = 0,
)
