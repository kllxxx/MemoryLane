package com.kllxx.memorylane

import android.util.Log
import timber.log.Timber

/** Release logs warnings and errors only, to Logcat: there is no crash reporter. */
class ReleaseTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // The stack trace of [t] is already part of [message]
        Log.println(priority, tag ?: DEFAULT_TAG, message)
    }

    private companion object {
        const val DEFAULT_TAG = "MemoryLane"
    }
}
