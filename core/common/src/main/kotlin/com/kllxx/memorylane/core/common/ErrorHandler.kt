package com.kllxx.memorylane.core.common

import androidx.annotation.StringRes
import timber.log.Timber

/** What the UI shows for a failure: the text comes from resources, never from the exception. */
data class UiError(@StringRes val message: Int) {
    companion object {
        val Generic = UiError(R.string.error_generic)
    }
}

/** The one place a failure is logged and turned into something the UI can show. Injected, not inherited. */
interface ErrorHandler {
    /**
     * Logs [throwable] as "Could not [what]" and returns what the UI shows for it. Never throws, and is never called
     * with a `CancellationException`: that is not a failure.
     */
    fun handle(throwable: Throwable, what: String): UiError
}

class TimberErrorHandler : ErrorHandler {
    override fun handle(throwable: Throwable, what: String): UiError {
        Timber.e(throwable, "Could not %s", what)
        // Throwable-specific messages go here as they appear
        return UiError.Generic
    }
}
