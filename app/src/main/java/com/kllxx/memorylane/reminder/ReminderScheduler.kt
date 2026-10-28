package com.kllxx.memorylane.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kllxx.memorylane.core.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * The next moment the reminder is due: [time] today if that is still ahead of [now], else [time] tomorrow. The date is
 * worked out in the zone of [now], so a change of the zone or of the clock is followed by asking again.
 */
fun nextReminder(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

/** Whether a person with these [settings] wants the reminder: the app's switch is on and the app is set up. */
fun AppSettings.wantsReminder(): Boolean = notificationsEnabled && onboardingCompleted

/**
 * One alarm a day at the stored time. It is the inexact kind (`setAndAllowWhileIdle`), which needs no special
 * permission and may come a few minutes late. It is set again each time it goes off, and when the settings, the clock,
 * the time zone or the app itself change (see [ReminderReceiver] and [ReminderBootReceiver]).
 */
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Sets the alarm for the next occurrence of [time], replacing the one that is set. */
    fun schedule(time: LocalTime) {
        val at = nextReminder(ZonedDateTime.now(clock), time)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), alarmIntent())
    }

    fun cancel() {
        alarmManager.cancel(alarmIntent())
    }

    /** Sets or cancels the alarm as the [settings] say. */
    fun apply(settings: AppSettings) {
        if (settings.wantsReminder()) schedule(settings.reminderTime) else cancel()
    }

    private fun alarmIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderConstants.ACTION_REMIND)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQUEST_ALARM = 20
    }
}
