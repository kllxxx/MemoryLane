package com.kllxx.memorylane.core.domain.streak

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 10, 17)

    private val everyDayHasPhotos: (LocalDate) -> Boolean = { true }
    private val noDayHasPhotos: (LocalDate) -> Boolean = { false }

    private fun streaks(
        real: Collection<LocalDate>,
        now: LocalDate = today,
        hasPhotos: (LocalDate) -> Boolean = everyDayHasPhotos,
    ) = StreakCalculator.streaks(real, now, hasPhotos)

    private fun days(from: LocalDate, count: Int) = (0L until count).map { from.plusDays(it) }

    // --- nothing ---

    @Test
    fun `no real day gives zero`() {
        assertEquals(Streaks.None, streaks(emptyList()))
    }

    @Test
    fun `only no-photo days give zero, however many`() {
        assertEquals(Streaks.None, streaks(emptyList(), hasPhotos = noDayHasPhotos))
    }

    // --- real days ---

    @Test
    fun `a real today is one`() {
        assertEquals(Streaks(1, 1), streaks(listOf(today)))
    }

    @Test
    fun `a real yesterday is one while today does not count yet`() {
        assertEquals(Streaks(1, 1), streaks(listOf(today.minusDays(1))))
    }

    @Test
    fun `a real day two days ago is no current streak but still the best`() {
        assertEquals(Streaks(0, 1), streaks(listOf(today.minusDays(2))))
    }

    @Test
    fun `three consecutive real days ending today or yesterday are three`() {
        assertEquals(Streaks(3, 3), streaks(days(today.minusDays(2), 3)))
        assertEquals(Streaks(3, 3), streaks(days(today.minusDays(3), 3)))
    }

    @Test
    fun `a gap resets the current run and the best keeps the longer one`() {
        val real = listOf(today, today.minusDays(1)) + days(today.minusDays(5), 3)

        assertEquals(Streaks(2, 3), streaks(real))
    }

    @Test
    fun `a day completed on another date is not a real day, so a person with only such days has nothing`() {
        // The rule is applied before the calculator (see SessionCompletion): May 18 done today is not passed in
        assertEquals(Streaks.None, streaks(emptyList()))
    }

    // --- no-photo days ---

    @Test
    fun `a real yesterday and a no-photo today are two`() {
        val onlyTodayEmpty: (LocalDate) -> Boolean = { it != today }

        assertEquals(Streaks(2, 2), streaks(listOf(today.minusDays(1)), hasPhotos = onlyTodayEmpty))
    }

    @Test
    fun `no-photo days before the first real day do not count`() {
        // Yesterday is the first real day, and every other day is empty: the run is yesterday and today, no more
        val result = streaks(listOf(today.minusDays(1)), hasPhotos = noDayHasPhotos)

        assertEquals(Streaks(2, 2), result)
    }

    @Test
    fun `no-photo days after a real day continue the run up to today`() {
        assertEquals(Streaks(6, 6), streaks(listOf(today.minusDays(5)), hasPhotos = noDayHasPhotos))
    }

    @Test
    fun `a day with photos that was not completed ends a run, and later empty days do not restart it`() {
        // Day 5 ago has photos and is not done; everything after it is empty
        val hasPhotos: (LocalDate) -> Boolean = { it == today.minusDays(5) }

        assertEquals(Streaks(0, 1), streaks(listOf(today.minusDays(6)), hasPhotos = hasPhotos))
    }

    @Test
    fun `an empty day between two real days joins them`() {
        val real = listOf(today.minusDays(3), today.minusDays(1), today)
        val hasPhotos: (LocalDate) -> Boolean = { it != today.minusDays(2) }

        assertEquals(Streaks(4, 4), streaks(real, hasPhotos = hasPhotos))
    }

    @Test
    fun `an empty today after a run that ended yesterday extends it, a day with photos does not`() {
        val real = listOf(today.minusDays(1))

        assertEquals(2, streaks(real, hasPhotos = { it != today }).current)
        assertEquals(1, streaks(real, hasPhotos = { true }).current)
    }

    // --- years ---

    @Test
    fun `a run over New Year asks about each date, so each date uses the window of its own year`() {
        val asked = mutableListOf<LocalDate>()
        // The real day is Dec 30; Dec 31 2025 and Jan 1 2026 are empty days, Jan 2 2026 has photos
        val hasPhotos: (LocalDate) -> Boolean = { date ->
            asked += date
            date == LocalDate.of(2026, 1, 2)
        }

        val result = streaks(listOf(LocalDate.of(2025, 12, 30)), now = LocalDate.of(2026, 1, 2), hasPhotos = hasPhotos)

        // Dec 30, Dec 31, Jan 1 count; Jan 2 has photos and is not done, so today does not count and yesterday ends the run
        assertEquals(Streaks(3, 3), result)
        assertEquals(listOf(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)), asked)
    }

    @Test
    fun `the best streak is never less than the current one`() {
        listOf(
            emptyList(),
            listOf(today),
            days(today.minusDays(4), 5),
            listOf(today, today.minusDays(2), today.minusDays(3)),
        ).forEach { real ->
            val result = streaks(real, hasPhotos = { it.dayOfMonth % 2 == 0 })
            assertTrue("$real $result", result.best >= result.current)
        }
    }

    @Test
    fun `days after today are ignored`() {
        assertEquals(Streaks(1, 1), streaks(listOf(today, today.plusDays(1), today.plusDays(2))))
    }

    @Test
    fun `the best streak covers the whole history`() {
        assertEquals(Streaks(0, 10), streaks(days(LocalDate.of(2024, 3, 1), 10)))
    }
}
