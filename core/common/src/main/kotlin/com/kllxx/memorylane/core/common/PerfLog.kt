package com.kllxx.memorylane.core.common

import timber.log.Timber

/**
 * Performance measurements, active only in the debug and perf builds ([enable] is called from the app).
 * In release the R8 rules (app/release-rules.pro) fold [isEnabled] to false and [nanoTime] to 0, so
 * every [d] call, its message and the time measurements around it are removed.
 *
 * Read the output with `adb logcat -s CalendarPerf`.
 */
object PerfLog {
    const val TAG = "CalendarPerf"
    private const val NANOS_PER_MILLI = 1_000_000L

    @Volatile
    private var enabled = false

    // Replaceable so that tests can read the lines
    @Volatile
    var sink: (String) -> Unit = { Timber.tag(TAG).d(it) }

    fun enable() {
        enabled = true
    }

    /** For tests: switches the logging on or off. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    @JvmStatic
    fun isEnabled(): Boolean = enabled

    /** [System.nanoTime] when logging is on, otherwise 0 (and nothing is measured). */
    @JvmStatic
    fun nanoTime(): Long = if (enabled) System.nanoTime() else 0L

    /** Milliseconds since [startNanos], a value of [nanoTime]. */
    @JvmStatic
    fun sinceMs(startNanos: Long): Long = (nanoTime() - startNanos) / NANOS_PER_MILLI

    /** Logs the message with the thread name. The message is only built when logging is on. */
    inline fun d(message: () -> String) {
        if (isEnabled()) emit(message())
    }

    @PublishedApi
    internal fun emit(message: String) {
        sink("[${Thread.currentThread().name}] $message")
    }
}
