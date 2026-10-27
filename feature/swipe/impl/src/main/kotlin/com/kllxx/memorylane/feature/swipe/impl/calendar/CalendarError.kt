package com.kllxx.memorylane.feature.swipe.impl.calendar

/** What went wrong, without the exception text; the UI maps it to a message. */
sealed interface CalendarError {
    data object LoadFailed : CalendarError
}
