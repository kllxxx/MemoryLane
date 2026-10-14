package com.kllxx.memorylane.core.data.impl.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "swipe_sessions")
data class SwipeSessionEntity(
    @PrimaryKey val date: LocalDate,
    val completed: Boolean,
    val savedBytes: Long,
)
