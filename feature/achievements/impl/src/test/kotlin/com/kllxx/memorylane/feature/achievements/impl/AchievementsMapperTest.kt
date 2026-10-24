package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.AchievementStats
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementsMapperTest {

    private val nbsp = " "

    private fun size(bytes: Long) = formatSize(bytes).let { it.value to it.unit }

    @Test
    fun `size below one megabyte is zero megabytes`() {
        assertEquals("0" to SizeUnit.MB, size(0))
        assertEquals("0" to SizeUnit.MB, size(999_999))
    }

    @Test
    fun `size of one megabyte`() {
        assertEquals("1" to SizeUnit.MB, size(1_000_000))
    }

    @Test
    fun `size below a gigabyte is whole megabytes`() {
        assertEquals("640" to SizeUnit.MB, size(640_000_000))
    }

    @Test
    fun `size just below a gigabyte never rounds up to 1000 megabytes`() {
        assertEquals("999" to SizeUnit.MB, size(999_900_000))
        assertEquals("999" to SizeUnit.MB, size(999_999_999))
    }

    @Test
    fun `size of one gigabyte drops the trailing zero`() {
        assertEquals("1" to SizeUnit.GB, size(1_000_000_000))
    }

    @Test
    fun `size above a gigabyte has one decimal with a comma`() {
        assertEquals("2,4" to SizeUnit.GB, size(2_440_000_000))
        assertEquals("2,5" to SizeUnit.GB, size(2_450_000_000))
    }

    @Test
    fun `a whole number of gigabytes has no decimal`() {
        assertEquals("12" to SizeUnit.GB, size(12_000_000_000))
        assertEquals("12" to SizeUnit.GB, size(11_960_000_000))
    }

    @Test
    fun `count of zero and small numbers has no grouping`() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
    }

    @Test
    fun `count groups thousands with a non-breaking space`() {
        assertEquals("1${nbsp}248", formatCount(1_248))
        assertEquals("12${nbsp}304", formatCount(12_304))
        assertEquals("1${nbsp}000${nbsp}000", formatCount(1_000_000))
    }

    @Test
    fun `no viewed photo means the empty state even with a streak or freed space`() {
        val stats = AchievementStats(currentStreak = 3, bestStreak = 5, viewedPhotos = 0, deletedPhotos = 0, freedBytes = 5_000_000)
        assertEquals(AchievementsState.Empty, stats.toState())
    }

    @Test
    fun `a viewed photo shows the stats even when the streak is zero`() {
        val stats = AchievementStats(currentStreak = 0, bestStreak = 0, viewedPhotos = 1, deletedPhotos = 0, freedBytes = 0)

        assertEquals(
            AchievementsState.Stats(0, 0, FormattedSize("0", SizeUnit.MB), "1", "0"),
            stats.toState(),
        )
    }

    @Test
    fun `stats are formatted for display`() {
        val stats = AchievementStats(7, 21, 1_248, 312, 2_440_000_000)

        assertEquals(
            AchievementsState.Stats(7, 21, FormattedSize("2,4", SizeUnit.GB), "1${nbsp}248", "312"),
            stats.toState(),
        )
    }
}
