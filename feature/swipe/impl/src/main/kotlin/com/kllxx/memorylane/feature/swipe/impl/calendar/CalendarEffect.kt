package com.kllxx.memorylane.feature.swipe.impl.calendar

import java.time.LocalDate

sealed interface CalendarEffect {
    data class NavigateToSwipe(val date: LocalDate) : CalendarEffect
    data object RequestPermissionLauncher : CalendarEffect
}
