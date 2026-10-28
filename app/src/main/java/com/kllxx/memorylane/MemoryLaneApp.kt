package com.kllxx.memorylane

import android.app.Application
import android.util.Log
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.reminder.ReminderScheduler
import com.kllxx.memorylane.reminder.wantsReminder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class MemoryLaneApp : Application() {

    @Inject lateinit var appSettingsRepository: AppSettingsRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Release stays silent; the perf build is release-like but logs for measurements
        if (BuildConfig.DEBUG || BuildConfig.PERF_LOGGING) {
            Timber.plant(Timber.DebugTree())
            PerfLog.enable()
        }
        followReminderSettings()
    }

    /** The daily reminder is set, moved or cancelled as the switch, the time and the end of onboarding say. */
    private fun followReminderSettings() {
        appScope.launch {
            try {
                appSettingsRepository.getSettings()
                    .distinctUntilChangedBy { it.wantsReminder() to it.reminderTime }
                    .collect { reminderScheduler.apply(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not follow the reminder settings", e)
            }
        }
    }

    private companion object {
        const val TAG = "MemoryLaneApp"
    }
}
