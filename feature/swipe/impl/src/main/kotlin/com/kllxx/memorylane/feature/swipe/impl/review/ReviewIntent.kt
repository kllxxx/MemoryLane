package com.kllxx.memorylane.feature.swipe.impl.review

sealed interface ReviewIntent {
    data object LoadStats : ReviewIntent
    data object ConfirmDelete : ReviewIntent

    /** Sent after the system delete dialog closes. [freedBytes] is what the delete freed. */
    data class DeleteResult(val success: Boolean, val freedBytes: Long) : ReviewIntent
}
