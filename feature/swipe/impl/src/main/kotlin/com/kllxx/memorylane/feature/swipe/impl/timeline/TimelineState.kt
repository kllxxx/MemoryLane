package com.kllxx.memorylane.feature.swipe.impl.timeline

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class TimelineState(
    val days: ImmutableList<DayItem> = persistentListOf(),
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val error: TimelineError? = null,
)
