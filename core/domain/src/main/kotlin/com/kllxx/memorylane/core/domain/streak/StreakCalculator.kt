package com.kllxx.memorylane.core.domain.streak

import java.time.LocalDate

object StreakCalculator {

    /** Consecutive days ending yesterday, plus 1 if [today] is also in [dates]. */
    fun currentStreak(dates: Collection<LocalDate>, today: LocalDate): Int {
        val set = dates.toSet()
        var streak = if (today in set) 1 else 0
        var day = today.minusDays(1)
        while (day in set) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Longest run of consecutive days in [dates]. */
    fun bestStreak(dates: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in dates.toSortedSet()) {
            run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            best = maxOf(best, run)
            previous = date
        }
        return best
    }
}
