package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit

import com.kllxx.memorylane.core.domain.model.AchievementStats
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementsMapperTest {

    private val nbsp = " "

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
