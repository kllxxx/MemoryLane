package com.kllxx.memorylane.feature.swipe.impl.timeline

/** What went wrong, without the exception text; the UI maps it to a message. */
sealed interface TimelineError {
    data object LoadFailed : TimelineError
}
