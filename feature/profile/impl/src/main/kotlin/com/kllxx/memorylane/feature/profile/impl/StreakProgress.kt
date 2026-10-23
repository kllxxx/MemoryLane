package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal

/** What the streak card shows, derived from the streak and its goal. */
data class StreakProgress(
    val progress: Float,
    val daysLeft: Int,
    val goalReached: Boolean,
) {
    companion object {
        fun of(currentStreak: Int, goal: StreakGoal): StreakProgress {
            val goalDays = goal.days
            return StreakProgress(
                progress = (currentStreak.toFloat() / goalDays).coerceIn(0f, 1f),
                daysLeft = (goalDays - currentStreak).coerceAtLeast(0),
                goalReached = currentStreak >= goalDays,
            )
        }
    }
}
