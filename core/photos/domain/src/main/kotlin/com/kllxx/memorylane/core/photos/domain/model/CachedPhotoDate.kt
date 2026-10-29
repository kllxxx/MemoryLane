package com.kllxx.memorylane.core.photos.domain.model

/** Where the date of a photo without a MediaStore `DATE_TAKEN` came from. */
enum class DateSource {
    /** The EXIF date tags of the file. */
    EXIF,

    /** The GPS date and time of the file's EXIF data, which are UTC. */
    GPS,

    /** An XMP date field of the file: `xmp:CreateDate`, `photoshop:DateCreated` or `exif:DateTimeOriginal`. */
    XMP,

    /** A date written in the file name, like `IMG_20160107_101500.jpg`. */
    FILENAME,

    /** None of the EXIF data, the XMP data and the file name has a usable date. */
    UNDATED,
}

/**
 * The result of resolving the date of one photo, remembered so that its file is read only once.
 * Valid while the photo's `DATE_MODIFIED` ([dateModified]) is unchanged.
 *
 * The date is the wall-clock time of the photo, [takenLocalEpochSecond] counted as if it were UTC, plus the
 * [offsetSeconds] from UTC when the file says so. Without an offset the device time zone applies when the date is used,
 * the same as for `DATE_TAKEN`. Both are null for [DateSource.UNDATED].
 *
 * [hasTime] is false when the source had only a day (a file name like `20160107.jpg`), so the time of day in
 * [takenLocalEpochSecond] is a placeholder that must not be shown.
 */
data class CachedPhotoDate(
    val id: Long,
    val dateModified: Long,
    val takenLocalEpochSecond: Long?,
    val offsetSeconds: Int?,
    val source: DateSource,
    val hasTime: Boolean = true,
)
