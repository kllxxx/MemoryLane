package com.kllxx.memorylane.feature.swipe.impl.timeline

import java.time.LocalDate

data class DayItem(
    val date: LocalDate,
    val loadState: DayLoadState,
)
