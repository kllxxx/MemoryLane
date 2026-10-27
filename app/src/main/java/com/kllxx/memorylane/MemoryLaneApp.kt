package com.kllxx.memorylane

import android.app.Application
import com.kllxx.memorylane.core.common.PerfLog
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MemoryLaneApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Release stays silent; the perf build is release-like but logs for measurements
        if (BuildConfig.DEBUG || BuildConfig.PERF_LOGGING) {
            Timber.plant(Timber.DebugTree())
            PerfLog.enable()
        }
    }
}
