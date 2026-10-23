package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakProgressTest {

    @Test
    fun `no streak is an empty bar with the whole goal left`() {
        val progress = StreakProgress.of(0, StreakGoal.D30)

        assertEquals(0f, progress.progress, 0f)
        assertEquals(30, progress.daysLeft)
        assertFalse(progress.goalReached)
    }

    @Test
    fun `progress is the share of the goal`() {
        val progress = StreakProgress.of(7, StreakGoal.D30)

        assertEquals(7f / 30f, progress.progress, 0.0001f)
        assertEquals(23, progress.daysLeft)
        assertFalse(progress.goalReached)
    }

    @Test
    fun `one day short of the goal`() {
        val progress = StreakProgress.of(99, StreakGoal.D100)

        assertEquals(1, progress.daysLeft)
        assertFalse(progress.goalReached)
    }

    @Test
    fun `goal exactly reached`() {
        val progress = StreakProgress.of(30, StreakGoal.D30)

        assertEquals(1f, progress.progress, 0f)
        assertEquals(0, progress.daysLeft)
        assertTrue(progress.goalReached)
    }

    @Test
    fun `goal exceeded keeps the bar full and nothing left`() {
        val progress = StreakProgress.of(400, StreakGoal.D365)

        assertEquals(1f, progress.progress, 0f)
        assertEquals(0, progress.daysLeft)
        assertTrue(progress.goalReached)
    }

    @Test
    fun `every goal has its own length`() {
        assertEquals(listOf(7, 30, 100, 365), StreakGoal.entries.map { it.days })
        StreakGoal.entries.forEach { goal ->
            assertEquals(goal.days, StreakProgress.of(0, goal).daysLeft)
        }
    }
}
