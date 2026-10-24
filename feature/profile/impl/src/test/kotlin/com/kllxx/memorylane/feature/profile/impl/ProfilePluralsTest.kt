package com.kllxx.memorylane.feature.profile.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Russian plural forms of the day counts, resolved by the real resources. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class ProfilePluralsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun daysLeft(n: Int) = context.resources.getQuantityString(R.plurals.profile_streak_days_left, n, n)

    private fun ofGoal(n: Int) = context.resources.getQuantityString(R.plurals.profile_streak_of_goal, n, n)

    @Test
    fun `days left`() {
        val expected = mapOf(
            0 to "Ещё 0 дней до цели",
            1 to "Ещё 1 день до цели",
            2 to "Ещё 2 дня до цели",
            5 to "Ещё 5 дней до цели",
            11 to "Ещё 11 дней до цели",
            21 to "Ещё 21 день до цели",
            22 to "Ещё 22 дня до цели",
            23 to "Ещё 23 дня до цели",
            25 to "Ещё 25 дней до цели",
            100 to "Ещё 100 дней до цели",
            364 to "Ещё 364 дня до цели",
        )
        expected.forEach { (n, text) -> assertEquals("n = $n", text, daysLeft(n)) }
    }

    @Test
    fun `of goal days`() {
        val expected = mapOf(
            0 to "из 0 дней",
            1 to "из 1 дня",
            2 to "из 2 дней",
            5 to "из 5 дней",
            7 to "из 7 дней",
            11 to "из 11 дней",
            21 to "из 21 дня",
            22 to "из 22 дней",
            25 to "из 25 дней",
            30 to "из 30 дней",
            100 to "из 100 дней",
            365 to "из 365 дней",
        )
        expected.forEach { (n, text) -> assertEquals("n = $n", text, ofGoal(n)) }
    }
}
