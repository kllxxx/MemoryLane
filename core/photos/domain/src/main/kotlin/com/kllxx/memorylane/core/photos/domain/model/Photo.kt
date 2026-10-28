package com.kllxx.memorylane.core.photos.domain.model

import android.net.Uri
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [takenAt] is the wall-clock time the photo was taken, in the device time zone: the same moment that gives
 * [dateTaken], so a photo is on the same day here as in the calendar index. [dateModified] is in seconds, as
 * MediaStore stores it, and keys the location cache together with [id].
 */
data class Photo(
    val id: Long,
    val uri: Uri,
    val takenAt: LocalDateTime,
    val sizeBytes: Long,
    val dateModified: Long = 0,
    val location: String? = null,
) {
    val dateTaken: LocalDate get() = takenAt.toLocalDate()
}
