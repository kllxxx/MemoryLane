package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.domain.model.DateSource
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Finds the date of a photo that MediaStore has no `DATE_TAKEN` for, in this order:
 * 1. EXIF `DateTimeOriginal`, then `DateTimeDigitized`, then `DateTime`, each with its own offset tag when there is one;
 * 2. the EXIF GPS date and time, which are UTC;
 * 3. the XMP fields `xmp:CreateDate`, `photoshop:DateCreated` and `exif:DateTimeOriginal`;
 * 4. the date in the file name.
 * A date that is not plausible (before 1990, in the future, or not a real date) counts as missing and the chain goes on.
 * Null means the photo is undated. A photo with a valid `DATE_TAKEN` never gets here, and neither `DATE_MODIFIED` nor
 * `DATE_ADDED` is ever a source: they are when a file was copied, not when the photo was made.
 */
internal class PhotoDateResolver(
    private val exif: ExifDateReader,
    private val clock: Clock,
) {

    fun resolve(photo: UndatedPhoto): ResolvedDate? {
        val tags = try {
            exif.read(photo.id)
        } catch (e: Exception) {
            // A file that cannot be opened or is not an image with EXIF: the file name may still tell
            null
        }
        return tags?.let { fromExif(it) ?: fromGps(it) ?: fromXmp(it) } ?: fromFileName(photo)
    }

    private fun fromExif(tags: ExifDateTags): ResolvedDate? =
        tags.candidates().firstNotNullOfOrNull { candidate ->
            candidate.parse()?.let { (dateTime, offset) -> ResolvedDate(dateTime, offset, DateSource.EXIF) }
                ?.takeIf(::isPlausible)
        }

    /** The GPS clock is UTC; keeping that as the offset makes the day the one of the device zone. */
    private fun fromGps(tags: ExifDateTags): ResolvedDate? =
        tags.gpsDateTime()
            ?.let { ResolvedDate(it, ZoneOffset.UTC, DateSource.GPS) }
            ?.takeIf(::isPlausible)

    private fun fromXmp(tags: ExifDateTags): ResolvedDate? =
        parseXmpDates(tags.xmp).firstNotNullOfOrNull { xmp ->
            ResolvedDate(xmp.dateTime, xmp.offset, DateSource.XMP, xmp.hasTime).takeIf(::isPlausible)
        }

    private fun fromFileName(photo: UndatedPhoto): ResolvedDate? =
        FileNameDateParser.candidates(photo.name).firstNotNullOfOrNull { name ->
            ResolvedDate(name.dateTime, name.offset, DateSource.FILENAME, name.hasTime).takeIf(::isPlausible)
        }

    private fun isPlausible(date: ResolvedDate): Boolean {
        val taken = date.toInstant(clock.zone)
        return taken >= EARLIEST.atStartOfDay(clock.zone).toInstant() && taken <= clock.instant()
    }

    companion object {
        /**
         * The version of this chain. Photos found to have no date are remembered as such; when the chain learns a new
         * source this goes up and those are looked at again, once. Photos with a cached date keep it.
         */
        const val DATE_CHAIN_VERSION = 2

        /** Nothing in a phone gallery is older; a smaller year is a camera clock that was never set. */
        private val EARLIEST: LocalDate = LocalDate.of(1990, 1, 1)
    }
}
