package com.kllxx.memorylane.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * The decision for one photo, at most one per photo. [sessionDate] is the On This Day date it was decided on,
 * [decision] the name of a `PhotoDecision` and [decidedAt] the epoch millis of the last change.
 */
@Entity(tableName = "photo_decisions", indices = [Index(value = ["sessionDate"])])
data class PhotoDecisionEntity(
    @PrimaryKey val photoId: Long,
    val sessionDate: LocalDate,
    val decision: String,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
)
