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
}
