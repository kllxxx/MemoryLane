package com.kllxx.memorylane.reminder

import com.kllxx.memorylane.core.domain.model.AppSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextReminderTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val time = LocalTime.of(20, 0)

    private fun at(date: String, time: String) =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    @Test
    fun `before the time the reminder is today`() {
        assertEquals(at("2026-10-15", "20:00"), nextReminder(at("2026-10-15", "09:30"), time))
    }

    @Test
    fun `after the time the reminder is tomorrow`() {
        assertEquals(at("2026-10-16", "20:00"), nextReminder(at("2026-10-15", "21:00"), time))
    }

    @Test
    fun `exactly at the time the next one is tomorrow, it does not fire twice`() {
        assertEquals(at("2026-10-16", "20:00"), nextReminder(at("2026-10-15", "20:00"), time))
    }

    @Test
    fun `at the end of the month tomorrow is the first of the next one`() {
        assertEquals(at("2026-11-01", "20:00"), nextReminder(at("2026-10-31", "23:59"), time))
    }

    @Test
    fun `the date is worked out in the zone of now`() {
        val tokyo = ZonedDateTime.of(LocalDate.of(2026, 10, 15), LocalTime.of(7, 0), ZoneId.of("Asia/Tokyo"))
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 10, 15), time, ZoneId.of("Asia/Tokyo")),
            nextReminder(tokyo, time),
        )
    }

    @Test
    fun `the reminder is wanted only with the switch on and the app set up`() {
        assertTrue(AppSettings(notificationsEnabled = true, onboardingCompleted = true).wantsReminder())
        assertFalse(AppSettings(notificationsEnabled = false, onboardingCompleted = true).wantsReminder())
        assertFalse(AppSettings(notificationsEnabled = true, onboardingCompleted = false).wantsReminder())
    }
}
