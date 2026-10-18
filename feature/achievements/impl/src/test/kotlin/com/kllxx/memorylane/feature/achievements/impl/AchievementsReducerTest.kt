package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AchievementsReducerTest {

    @Test
    fun `Load sets loading`() {
        val result = AchievementsReducer.reduce(AchievementsState(), AchievementsIntent.Load)
        assertEquals(AchievementsState(isLoading = true), result)
    }

    @Test
    fun `GoalLoaded updates goal and recomputes progress`() {
        val state = AchievementsState(currentStreak = 3, streakGoalProgress = 3f / 7)
        val result = AchievementsReducer.reduce(state, AchievementsEvent.GoalLoaded(StreakGoal.MONTH))
        assertEquals(StreakGoal.MONTH, result.streakGoal)
        assertEquals(3f / 30, result.streakGoalProgress, 0.0001f)
        assertEquals(3, result.currentStreak)
    }

    @Test
    fun `progress is coerced to one`() {
        val state = AchievementsState(currentStreak = 20)
        val result = AchievementsReducer.reduce(state, AchievementsEvent.GoalLoaded(StreakGoal.WEEK))
        assertEquals(1f, result.streakGoalProgress, 0f)
    }

    @Test
    fun `StatsLoaded sets stats, progress and clears loading`() {
        val state = AchievementsState(isLoading = true)
        val result = AchievementsReducer.reduce(
            state,
            AchievementsEvent.StatsLoaded(currentStreak = 5, bestStreak = 9, totalFreedBytes = 1_000L, totalSortedDays = 12),
        )
        assertEquals(5, result.currentStreak)
        assertEquals(9, result.bestStreak)
        assertEquals(1_000L, result.totalFreedBytes)
        assertEquals(12, result.totalSortedDays)
        assertEquals(5f / 7, result.streakGoalProgress, 0.0001f)
        assertFalse(result.isLoading)
    }

    @Test
    fun `GoalLoaded keeps stats and recomputes progress`() {
        val state = AchievementsState(currentStreak = 7, bestStreak = 7)
        val result = AchievementsReducer.reduce(state, AchievementsEvent.GoalLoaded(StreakGoal.HALF_YEAR))
        assertEquals(StreakGoal.HALF_YEAR, result.streakGoal)
        assertEquals(7f / 180, result.streakGoalProgress, 0.0001f)
        assertEquals(7, result.bestStreak)
    }
}
