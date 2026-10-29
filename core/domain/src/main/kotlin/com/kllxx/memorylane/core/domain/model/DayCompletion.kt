package com.kllxx.memorylane.core.domain.model

/** When a day counts as completed, the one rule the banner of today, the reminder and the app's routing share. */
object DayCompletion {

    /**
     * A day is completed when it has photos and every one of them has a decision. A mark for deletion is a decision
     * too: a dialog that was cancelled leaves the day completed, because the streak counts swipes and not confirmed
     * deletions. A day without photos has nothing to complete.
     */
    fun isCompleted(photoIds: Collection<Long>, decisions: Map<Long, PhotoDecision>): Boolean =
        photoIds.isNotEmpty() && photoIds.all { it in decisions }
}
