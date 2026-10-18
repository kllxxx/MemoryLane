package com.kllxx.memorylane.feature.swipe.impl.timeline

/** Results produced by [TimelineViewModel] itself. The UI never sends these. */
internal sealed interface TimelineEvent {
    data class DaysInitialized(val days: List<DayItem>) : TimelineEvent
    data class DaysLoaded(val days: List<DayItem>) : TimelineEvent
    data object LoadFailed : TimelineEvent
}
