package com.kllxx.memorylane.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "pending_deletes")
data class PendingDeleteEntity(
    @PrimaryKey val photoId: Long,
    val sessionDate: LocalDate,
)
