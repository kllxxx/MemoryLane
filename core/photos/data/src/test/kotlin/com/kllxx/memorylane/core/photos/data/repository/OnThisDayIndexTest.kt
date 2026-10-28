package com.kllxx.memorylane.core.photos.data.repository

import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a date has photos in its own window: the same month and day in the 30 years before the year of that date. */
class OnThisDayIndexTest {

    private fun index(vararg years: Pair<MonthDay, Set<Int>>) =
        OnThisDayIndex(currentYear = 2026, entries = emptyMap(), photoYears = mapOf(*years))

    private val mar9 = MonthDay.of(3, 9)

    @Test
    fun `a date with a photo from an earlier year in its window has photos`() {
        assertTrue(index(mar9 to setOf(2020)).hasPhotosFor(LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun `a date without a photo on its month and day has none`() {
        assertFalse(index(mar9 to setOf(2020)).hasPhotosFor(LocalDate.of(2026, 3, 10)))
        assertFalse(index().hasPhotosFor(LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun `a photo of the year of the date itself or later is not in its window`() {
        val index = index(mar9 to setOf(2025))

        assertFalse(index.hasPhotosFor(LocalDate.of(2025, 3, 9)))
        assertFalse(index.hasPhotosFor(LocalDate.of(2024, 3, 9)))
        assertTrue(index.hasPhotosFor(LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun `each date uses the window of its own year, so the same photo is in one window and not in another`() {
        // A photo from 1995: not in the window of 2026 (1996..2025), but in the window of 2025 (1995..2024)
        val index = index(mar9 to setOf(1995))

        assertFalse(index.hasPhotosFor(LocalDate.of(2026, 3, 9)))
        assertTrue(index.hasPhotosFor(LocalDate.of(2025, 3, 9)))
    }

    @Test
    fun `the window is 30 years, 29 years ago is in it and 31 is not`() {
        assertTrue(index(mar9 to setOf(1996)).hasPhotosFor(LocalDate.of(2026, 3, 9)))
        assertFalse(index(mar9 to setOf(1995)).hasPhotosFor(LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun `a run over New Year has a different window on each side of it`() {
        // Dec 31 2025 has the window 1995..2024, Jan 1 2026 has 1996..2025
        val index = index(MonthDay.of(12, 31) to setOf(1995), MonthDay.of(1, 1) to setOf(1995))

        assertTrue(index.hasPhotosFor(LocalDate.of(2025, 12, 31)))
        assertFalse(index.hasPhotosFor(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `Feb 29 is only asked about in a leap year and has its own years`() {
        val index = index(MonthDay.of(2, 29) to setOf(2016))

        assertTrue(index.hasPhotosFor(LocalDate.of(2024, 2, 29)))
    }
}
