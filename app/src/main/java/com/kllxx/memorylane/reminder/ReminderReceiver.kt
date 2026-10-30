package com.kllxx.memorylane.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The time of the daily reminder has come. The next alarm is set first, so a failure below never ends the reminders,
 * and then the notification is shown only when there is something to review today.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var appSettingsRepository: AppSettingsRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var todayReview: TodayReview

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderConstants.ACTION_REMIND) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = appSettingsRepository.getSettings().first()
                scheduler.apply(settings)
                if (settings.wantsReminder()) {
                    val photos = todayReview.photosToReview()
                    if (photos > 0) notifier.post(photos)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not remind")
            } finally {
                pending.finish()
            }
        }
    }
}
