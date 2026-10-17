package com.kllxx.memorylane.feature.swipe.impl.review

sealed interface ReviewIntent {
    data object LoadStats : ReviewIntent
    data object ConfirmDelete : ReviewIntent

    /** Sent when the photos are really gone: after the system delete dialog is confirmed, or after a direct delete. */
    data object SystemDeleteConfirmed : ReviewIntent

    /** Sent when the system delete dialog is dismissed or the delete failed. Nothing was deleted. */
    data object SystemDeleteCancelled : ReviewIntent
}
