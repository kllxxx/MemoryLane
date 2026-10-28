package com.kllxx.memorylane.core.photos.domain.model

import android.net.Uri
import java.time.LocalDate
import java.time.MonthDay

/** The oldest photo taken on a month/day, used as its thumbnail. */
data class DayCover(
    val id: Long,
    val uri: Uri,
    val takenOn: LocalDate,
)

/** How many photos were taken on a month/day in earlier years, and the [cover] among them. */
data class DayMemories(
    val count: Int,
    val cover: DayCover,
)

/**
 * Every month/day that has photos from earlier years (up to the look-back limit before [currentYear]),
 * built from one pass over the gallery. A month/day without photos has no entry.
 */
data class OnThisDayIndex(
    val currentYear: Int,
    val entries: Map<MonthDay, DayMemories>,
    /**
     * Every year before [currentYear], without a lower limit, in which a photo was taken on a month/day. [entries] only
     * has the look-back window of the current year; this is what answers "does that date have photos in its own window"
     * for a date of another year.
     */
    val photoYears: Map<MonthDay, Set<Int>> = emptyMap(),
) {
    /** Photos in the index, i.e. photos that can show up as a memory in the calendar. */
    val totalPhotos: Int get() = entries.values.sumOf { it.count }

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Whether [date] has photos in its own window: its month/day in the [maxYearsBack] years before the year of [date].
     * Only photos from years before [currentYear] are known, which is every year that can be in the window of a date
     * that has happened.
     */
    fun hasPhotosFor(date: LocalDate, maxYearsBack: Int = DEFAULT_YEARS_BACK): Boolean {
        val years = photoYears[MonthDay.from(date)] ?: return false
        val window = (date.year - maxYearsBack)..(date.year - 1)
        return years.any { it in window }
    }

    companion object {
        /** The look-back of the app: the 30 years before a date. */
        const val DEFAULT_YEARS_BACK = 30
    }
}
