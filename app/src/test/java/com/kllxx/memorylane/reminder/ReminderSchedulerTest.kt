package com.kllxx.memorylane.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import com.kllxx.memorylane.MutableClock
import com.kllxx.memorylane.core.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val alarms get() = shadowOf(alarmManager)
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"))
    private val scheduler = ReminderScheduler(context, clock)

    private fun nextAlarm() = alarms.nextScheduledAlarm!!

    private fun instant(text: String): Long = Instant.parse(text).toEpochMilli()

    @Test
    fun `a time still ahead today is set for today as a waking alarm`() {
        scheduler.schedule(LocalTime.of(20, 0))

        val alarm = nextAlarm()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(instant("2026-10-15T20:00:00Z"), alarm.triggerAtMs)
    }

    @Test
    fun `a time that has passed today is set for tomorrow`() {
        scheduler.schedule(LocalTime.of(9, 0))

        assertEquals(instant("2026-10-16T09:00:00Z"), nextAlarm().triggerAtMs)
    }

    @Test
    fun `the time of this very moment is set for tomorrow`() {
        scheduler.schedule(LocalTime.of(12, 0))

        assertEquals(instant("2026-10-16T12:00:00Z"), nextAlarm().triggerAtMs)
    }

    @Test
    fun `the time is the local time of the zone of the clock`() {
        val moscow = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("Europe/Moscow"))

        ReminderScheduler(context, moscow).schedule(LocalTime.of(20, 0))

        // 20:00 in Moscow (UTC+3) is 17:00 UTC
        assertEquals(instant("2026-10-15T17:00:00Z"), nextAlarm().triggerAtMs)
    }

    @Test
    fun `the alarm broadcasts the remind action to the reminder receiver`() {
        scheduler.schedule(LocalTime.of(20, 0))

        val operation = shadowOf(nextAlarm().operation)
        assertTrue(operation.isBroadcastIntent)
        assertEquals(20, operation.requestCode)
        assertEquals(ReminderConstants.ACTION_REMIND, operation.savedIntent.action)
        assertEquals(ReminderReceiver::class.java.name, operation.savedIntent.component?.className)
    }

    @Test
    fun `setting it again replaces the alarm instead of adding one`() {
        scheduler.schedule(LocalTime.of(20, 0))
        scheduler.schedule(LocalTime.of(21, 30))

        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(instant("2026-10-15T21:30:00Z"), nextAlarm().triggerAtMs)
    }

    @Test
    fun `cancel removes the alarm`() {
        scheduler.schedule(LocalTime.of(20, 0))

        scheduler.cancel()

        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun `cancel with nothing set does nothing`() {
        scheduler.cancel()

        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `apply sets the alarm when the reminder is on and the app is set up`() {
        scheduler.apply(
            AppSettings(notificationsEnabled = true, onboardingCompleted = true, reminderTime = LocalTime.of(18, 45)),
        )

        assertEquals(instant("2026-10-15T18:45:00Z"), nextAlarm().triggerAtMs)
    }

    @Test
    fun `apply cancels the alarm when the reminder is switched off`() {
        scheduler.schedule(LocalTime.of(20, 0))

        scheduler.apply(AppSettings(notificationsEnabled = false, onboardingCompleted = true))

        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun `apply cancels the alarm while onboarding is not completed`() {
        scheduler.schedule(LocalTime.of(20, 0))

        scheduler.apply(AppSettings(notificationsEnabled = true, onboardingCompleted = false))

        assertNull(alarms.nextScheduledAlarm)
    }
}
