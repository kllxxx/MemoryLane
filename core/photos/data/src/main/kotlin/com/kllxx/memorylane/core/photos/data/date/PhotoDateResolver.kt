package com.kllxx.memorylane.core.photos.data.date

import com.kllxx.memorylane.core.photos.domain.model.DateSource
import java.time.Clock
import java.time.LocalDate

/**
 * Finds the date of a photo that MediaStore has no `DATE_TAKEN` for, in this order:
 * 1. EXIF `DateTimeOriginal`, then `DateTimeDigitized`, then `DateTime`, each with its own offset tag when there is one;
 * 2. the date in the file name.
 * A date that is not plausible (before 1990, in the future, or not a real date) counts as missing and the chain goes on.
 * Null means the photo is undated. A photo with a valid `DATE_TAKEN` never gets here.
 */
internal class PhotoDateResolver(
    private val exif: ExifDateReader,
    private val clock: Clock,
) {

    fun resolve(photo: UndatedPhoto): ResolvedDate? = fromExif(photo) ?: fromFileName(photo)

    private fun fromExif(photo: UndatedPhoto): ResolvedDate? {
        val tags = try {
            exif.read(photo.id)
        } catch (e: Exception) {
            // A file that cannot be opened or is not an image with EXIF: the file name may still tell
            return null
        }
        return tags.candidates().firstNotNullOfOrNull { candidate ->
            candidate.parse()?.let { (dateTime, offset) -> ResolvedDate(dateTime, offset, DateSource.EXIF) }
                ?.takeIf(::isPlausible)
        }
    }

    private fun fromFileName(photo: UndatedPhoto): ResolvedDate? =
        FileNameDateParser.parse(photo.name)
            ?.let { ResolvedDate(it, offset = null, source = DateSource.FILENAME) }
            ?.takeIf(::isPlausible)

    private fun isPlausible(date: ResolvedDate): Boolean {
        val taken = date.toInstant(clock.zone)
        return taken >= EARLIEST.atStartOfDay(clock.zone).toInstant() && taken <= clock.instant()
    }

    private companion object {
        /** Nothing in a phone gallery is older; a smaller year is a camera clock that was never set. */
        val EARLIEST: LocalDate = LocalDate.of(1990, 1, 1)
    }
}
