package com.kllxx.memorylane.feature.calendar.impl

import com.kllxx.memorylane.feature.calendar.impl.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class StreakPluralsTest {

    private fun days(count: Int) = RuntimeEnvironment.getApplication().resources
        .getQuantityString(R.plurals.calendar_streak_days, count, count)

    @Test
    @Config(qualifiers = "ru")
    fun `Russian uses day, days and days with the right endings`() {
        assertEquals("1 день", days(1))
        assertEquals("21 день", days(21))
        assertEquals("2 дня", days(2))
        assertEquals("4 дня", days(4))
        assertEquals("22 дня", days(22))
        assertEquals("5 дней", days(5))
        assertEquals("7 дней", days(7))
        assertEquals("11 дней", days(11))
        assertEquals("14 дней", days(14))
        assertEquals("20 дней", days(20))
        assertEquals("0 дней", days(0))
    }

    @Test
    @Config(qualifiers = "en")
    fun `other locales fall back to the default plural`() {
        assertEquals("1 day", days(1))
        assertEquals("7 days", days(7))
    }
}
