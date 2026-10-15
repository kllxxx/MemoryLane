package com.kllxx.memorylane.feature.swipe.impl.review

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * [sessionFreedBytes] is the amount that will be freed until the delete succeeds,
 * and the amount that was actually freed afterwards.
 */
data class ReviewState(
    val keptCount: Int = 0,
    val deletedCount: Int = 0,
    val pendingDeleteIds: ImmutableList<Long> = persistentListOf(),
    val totalFreedBytes: Long = 0L,
    val sessionFreedBytes: Long = 0L,
    val isDeleting: Boolean = false,
    val isFinished: Boolean = false,
)
