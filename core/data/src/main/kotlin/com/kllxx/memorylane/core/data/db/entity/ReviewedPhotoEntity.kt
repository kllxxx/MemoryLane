package com.kllxx.memorylane.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A photo that got a swipe decision at least once. Rows are never removed, so undo or a later re-swipe changes nothing. */
@Entity(tableName = "reviewed_photos")
data class ReviewedPhotoEntity(
    @PrimaryKey val mediaId: Long,
    val firstReviewedAt: LocalDate,
)
