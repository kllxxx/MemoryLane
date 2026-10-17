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
    // The default must match MIGRATION_1_2 or Room rejects the migrated schema
    @ColumnInfo(name = "completed_at", defaultValue = "0") val completedAt: LocalDate,
)
