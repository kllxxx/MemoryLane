package com.kllxx.memorylane.feature.swipe.impl.calendar

import java.time.LocalDate

data class DayItem(
    val date: LocalDate,
    val loadState: DayLoadState,
)
