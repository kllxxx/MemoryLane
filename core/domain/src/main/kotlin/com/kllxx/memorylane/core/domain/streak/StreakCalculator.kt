package com.kllxx.memorylane.core.domain.streak

import java.time.LocalDate

/** The streaks of a person: the run that is on now and the longest one there ever was. */
data class Streaks(val current: Int, val best: Int) {
    companion object {
        val None = Streaks(current = 0, best = 0)
    }
}

/**
 * The streak rules, with every fact passed in.
 *
 * A day counts when it is a **real day**, a day the person completed on that very date, or a **no-photo day**, a day
 * that has no photos in its window. A no-photo day only continues a run: a run starts with a real day, so a person who
 * never completed their own day has no streak, however many empty days there are.
 */
object StreakCalculator {

    /**
     * [realDays] are the days completed on their own date. [hasPhotos] says whether a date has photos in its own window
     * (the same month/day in the 30 years before the year of that date), which is how a run over New Year is right.
     *
     * The current streak is the run of counting days that ends today, or yesterday while today does not count yet; with
     * neither it is 0. The best streak is the longest run in the whole history. Dates after [today] are ignored.
     */
    fun streaks(realDays: Collection<LocalDate>, today: LocalDate, hasPhotos: (LocalDate) -> Boolean): Streaks {
        val real = realDays.filterTo(HashSet()) { !it.isAfter(today) }
        var day = real.minOrNull() ?: return Streaks.None
        var run = 0
        var runYesterday = 0
        var best = 0
        while (!day.isAfter(today)) {
            runYesterday = run
            run = when {
                day in real -> run + 1
                // An empty day keeps a run going, it never starts one
                run > 0 && !hasPhotos(day) -> run + 1
                else -> 0
            }
            if (run > best) best = run
            day = day.plusDays(1)
        }
        return Streaks(current = if (run > 0) run else runYesterday, best = best)
    }
}
