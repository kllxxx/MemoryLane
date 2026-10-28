package com.kllxx.memorylane.core.photos.data.repository

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId

/** One gallery image as the index needs it: when it was taken, from `DATE_TAKEN` or from the date resolved out of its file. */
internal class IndexedImage(val id: Long, val takenAtMillis: Long)

/**
 * Collects the photos of a whole gallery into the "on this day" index, one image at a time, so the gallery
 * never has to be held in memory. The rules are those of the former per-day query:
 * - an image counts for the month/day of its local date, in the years `[currentYear - maxYearsBack, currentYear - 1]` only;
 * - the cover of a month/day is its oldest image (lowest id when two were taken at the same moment);
 * - a Feb 29 image is a Feb 29 entry (only leap years have one), never moved to Feb 28 or Mar 1.
 */
internal class OnThisDayIndexBuilder(
    private val currentYear: Int,
    private val maxYearsBack: Int,
    private val zone: ZoneId,
) {
    private val counts = HashMap<MonthDay, Int>()
    private val covers = HashMap<MonthDay, Cover>()
    private val years = HashMap<MonthDay, MutableSet<Int>>()

    class Cover(val id: Long, val takenAtMillis: Long, val takenOn: LocalDate)

    /** Image rows that were inside the look-back window. */
    var counted = 0
        private set

    fun add(image: IndexedImage) {
        val date = Instant.ofEpochMilli(image.takenAtMillis).atZone(zone).toLocalDate()
        // The years are kept beyond the window: a date of an earlier year has a window of its own
        if (date.year < currentYear) years.getOrPut(MonthDay.from(date)) { HashSet() } += date.year
        if (date.year !in (currentYear - maxYearsBack)..(currentYear - 1)) return

        val key = MonthDay.from(date)
        counts.merge(key, 1, Int::plus)
        counted++
        val cover = covers[key]
        if (cover == null || image.takenAtMillis < cover.takenAtMillis ||
            (image.takenAtMillis == cover.takenAtMillis && image.id < cover.id)
        ) {
            covers[key] = Cover(image.id, image.takenAtMillis, date)
        }
    }

    /** Every year before the current one in which a photo was taken on each month/day, whatever the look-back. */
    fun photoYears(): Map<MonthDay, Set<Int>> = years

    /** The collected month/days as (count, cover); the caller turns the cover into a Uri. */
    fun entries(): Map<MonthDay, Pair<Int, Cover>> = counts.mapValues { (key, count) -> count to covers.getValue(key) }
}
