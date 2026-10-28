package com.kllxx.memorylane.core.photos.domain.model

import android.net.Uri
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [takenAt] is the wall-clock time the photo was taken, in the device time zone: the same moment that gives
 * [dateTaken], so a photo is on the same day here as in the calendar index. [dateModified] is in seconds, as
 * MediaStore stores it, and keys the location cache together with [id].
 *
 * [width] and [height] are the pixels of the file as MediaStore stores them, before [orientation] (0, 90, 180 or 270
 * degrees) is applied, so a photo taken upright with the phone turned has a landscape file and a 90 or 270
 * orientation. 0 means MediaStore does not know.
 *
 * [hasTime] is false when only the day is known (a date read from a file name without a time): the time of day in
 * [takenAt] is then a placeholder, and only the date is shown. A real midnight has [hasTime] true.
 */
data class Photo(
    val id: Long,
    val uri: Uri,
    val takenAt: LocalDateTime,
    val sizeBytes: Long,
    val dateModified: Long = 0,
    val location: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 0,
    val hasTime: Boolean = true,
) {
    val dateTaken: LocalDate get() = takenAt.toLocalDate()
}
