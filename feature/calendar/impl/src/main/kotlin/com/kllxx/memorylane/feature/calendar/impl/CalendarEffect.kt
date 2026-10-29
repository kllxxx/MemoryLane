package com.kllxx.memorylane.feature.calendar.impl

import java.time.LocalDate

sealed interface CalendarEffect {
    data class NavigateToSwipe(val date: LocalDate) : CalendarEffect
    data object RequestPermissionLauncher : CalendarEffect
}
