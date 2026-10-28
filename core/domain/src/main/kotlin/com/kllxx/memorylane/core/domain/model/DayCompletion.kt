package com.kllxx.memorylane.core.domain.model

/** When a day counts as completed, the one rule the banner of today, the reminder and the app's routing share. */
object DayCompletion {

    // A photo that is only marked for deletion is not decided yet: the day is done once the deletion is confirmed
    private val DECIDED = setOf(PhotoDecision.KEEP, PhotoDecision.TRASHED, PhotoDecision.DELETED)

    /**
     * A day is completed when it has photos and every one of them has a decision, none of them only marked for
     * deletion. A day without photos has nothing to complete.
     */
    fun isCompleted(photoIds: Collection<Long>, decisions: Map<Long, PhotoDecision>): Boolean =
        photoIds.isNotEmpty() && photoIds.all { decisions[it] in DECIDED }
}
