package com.kllxx.memorylane.core.domain.streak

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun `empty input gives zero`() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), today))
        assertEquals(0, StreakCalculator.bestStreak(emptyList()))
    }

    @Test
    fun `only yesterday gives one`() {
        assertEquals(1, StreakCalculator.currentStreak(listOf(today.minusDays(1)), today))
    }

    @Test
    fun `only two days ago gives zero`() {
        assertEquals(0, StreakCalculator.currentStreak(listOf(today.minusDays(2)), today))
    }

    @Test
    fun `three consecutive days ending today or yesterday give three`() {
        val endingToday = (0L..2L).map { today.minusDays(it) }
        val endingYesterday = (1L..3L).map { today.minusDays(it) }
        assertEquals(3, StreakCalculator.currentStreak(endingToday, today))
        assertEquals(3, StreakCalculator.currentStreak(endingYesterday, today))
    }

    @Test
    fun `a gap resets the count`() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(3), today.minusDays(4), today.minusDays(5))
        assertEquals(2, StreakCalculator.currentStreak(dates, today))
        assertEquals(3, StreakCalculator.bestStreak(dates))
    }

    @Test
    fun `only today gives one`() {
        assertEquals(1, StreakCalculator.currentStreak(listOf(today), today))
    }

    @Test
    fun `today not done and yesterday done counts days up to yesterday`() {
        val dates = (1L..10L).map { today.minusDays(it) } + today.minusDays(12)
        assertEquals(10, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `today done includes today in the streak`() {
        val dates = (0L..10L).map { today.minusDays(it) }
        assertEquals(11, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `yesterday missed gives zero when today is not done`() {
        val dates = listOf(today.minusDays(2), today.minusDays(3))
        assertEquals(0, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `today done but yesterday missed gives one`() {
        val dates = listOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(1, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `duplicates are ignored`() {
        val dates = listOf(today, today, today.minusDays(1))
        assertEquals(2, StreakCalculator.currentStreak(dates, today))
        assertEquals(2, StreakCalculator.bestStreak(dates))
    }

    @Test
    fun `best streak finds the longest run regardless of order`() {
        val dates = listOf(
            today,
            today.minusDays(30), today.minusDays(28), today.minusDays(29), today.minusDays(27),
            today.minusDays(10), today.minusDays(11),
        )
        assertEquals(4, StreakCalculator.bestStreak(dates))
        assertEquals(1, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `best streak of nothing is zero`() {
        assertEquals(0, StreakCalculator.bestStreak(emptyList()))
    }

    @Test
    fun `best streak of a single day is one`() {
        assertEquals(1, StreakCalculator.bestStreak(listOf(today)))
    }

    @Test
    fun `best streak ignores gaps and duplicates`() {
        val dates = listOf(today, today, today.minusDays(2), today.minusDays(4), today.minusDays(5))
        assertEquals(2, StreakCalculator.bestStreak(dates))
    }

    @Test
    fun `best streak is the current run when it is the longest`() {
        val dates = (0L..4L).map { today.minusDays(it) } + listOf(today.minusDays(10), today.minusDays(11))
        assertEquals(5, StreakCalculator.bestStreak(dates))
        assertEquals(5, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `best streak is an older run when the current one is shorter`() {
        val dates = listOf(today) + (10L..15L).map { today.minusDays(it) }
        assertEquals(6, StreakCalculator.bestStreak(dates))
        assertEquals(1, StreakCalculator.currentStreak(dates, today))
    }
}
