package com.kllxx.memorylane.core.domain.model

import java.time.LocalDate

/**
 * @property date the On This Day date that was sorted.
 * @property completedAt the day the user finished sorting [date], which is what streaks and the daily
 * routing are based on. [LocalDate.EPOCH] marks sessions saved before this was tracked.
 * @property deletedCount photos of [date] deleted so far; 0 for deletions made before this was tracked.
 */
data class SwipeSession(
    val date: LocalDate,
    val completed: Boolean,
    val savedBytes: Long,
    val completedAt: LocalDate,
    val deletedCount: Int = 0,
)
