package com.kllxx.memorylane.feature.swipe.impl.review

/** Results produced by [ReviewViewModel] itself. The UI never sends these. */
internal sealed interface ReviewEvent {
    data class StatsLoaded(
        val keptCount: Int,
        val deletedCount: Int,
        val pendingDeleteIds: List<Long>,
        val totalFreedBytes: Long,
        val sessionFreedBytes: Long,
    ) : ReviewEvent
}
