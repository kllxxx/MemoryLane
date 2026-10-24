package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.AchievementStats
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementsReducerTest {

    private val stats = AchievementStats(2, 4, 10, 3, 1_000_000)

    @Test
    fun `loaded stats replace the loading state`() {
        val state = AchievementsReducer.reduce(AchievementsState.Loading, AchievementsEvent.StatsLoaded(stats))

        assertEquals(stats.toState(), state)
    }

    @Test
    fun `loaded stats without viewed photos give the empty state`() {
        val state = AchievementsReducer.reduce(
            AchievementsState.Loading,
            AchievementsEvent.StatsLoaded(stats.copy(viewedPhotos = 0)),
        )

        assertEquals(AchievementsState.Empty, state)
    }

    @Test
    fun `new stats replace the empty state`() {
        val state = AchievementsReducer.reduce(AchievementsState.Empty, AchievementsEvent.StatsLoaded(stats))

        assertEquals(stats.toState(), state)
    }

    @Test
    fun `the calendar click does not change the state`() {
        val before = stats.toState()

        assertEquals(before, AchievementsReducer.reduce(before, AchievementsIntent.GoToCalendarClicked))
        assertEquals(
            AchievementsState.Loading,
            AchievementsReducer.reduce(AchievementsState.Loading, AchievementsIntent.GoToCalendarClicked),
        )
    }
}
