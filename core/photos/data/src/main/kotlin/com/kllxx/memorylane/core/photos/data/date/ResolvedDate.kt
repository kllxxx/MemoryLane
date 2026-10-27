package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.domain.model.CachedPhotoDate
import com.kllxx.memorylane.core.photos.domain.model.DateSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A photo without a MediaStore `DATE_TAKEN`, as far as its date is concerned. [dateModified] is in seconds, as MediaStore stores it. */
internal data class UndatedPhoto(
    val id: Long,
    val dateModified: Long,
    val name: String?,
    val mimeType: String?,
)

/**
 * The date found for an [UndatedPhoto]: the wall-clock [dateTime] and, when the file says so, its [offset] from UTC.
 * Without an offset the device zone decides, the same as for `DATE_TAKEN`.
 */
internal data class ResolvedDate(
    val dateTime: LocalDateTime,
    val offset: ZoneOffset?,
    val source: DateSource,
) {
    fun toInstant(zone: ZoneId): Instant =
        if (offset != null) dateTime.toInstant(offset) else dateTime.atZone(zone).toInstant()

    /** The day this photo belongs to in [zone], which is the day `DATE_TAKEN` would give for the same moment. */
    fun toLocalDate(zone: ZoneId): LocalDate = toInstant(zone).atZone(zone).toLocalDate()

    fun toCached(photo: UndatedPhoto) = CachedPhotoDate(
        id = photo.id,
        dateModified = photo.dateModified,
        takenLocalEpochSecond = dateTime.toEpochSecond(ZoneOffset.UTC),
        offsetSeconds = offset?.totalSeconds,
        source = source,
    )

    companion object {
        fun undatedToCached(photo: UndatedPhoto) = CachedPhotoDate(
            id = photo.id,
            dateModified = photo.dateModified,
            takenLocalEpochSecond = null,
            offsetSeconds = null,
            source = DateSource.UNDATED,
        )

        /** The date held by a cache entry, or null when the entry says the photo is undated. */
        fun fromCached(cached: CachedPhotoDate): ResolvedDate? {
            val seconds = cached.takenLocalEpochSecond ?: return null
            if (cached.source == DateSource.UNDATED) return null
            return ResolvedDate(
                dateTime = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC),
                offset = cached.offsetSeconds?.let(ZoneOffset::ofTotalSeconds),
                source = cached.source,
            )
        }
    }
}
