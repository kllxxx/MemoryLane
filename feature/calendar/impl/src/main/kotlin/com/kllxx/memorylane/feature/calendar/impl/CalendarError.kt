package com.kllxx.memorylane.feature.calendar.impl

/** What went wrong, without the exception text; the UI maps it to a message. */
sealed interface CalendarError {
    data object LoadFailed : CalendarError
}
