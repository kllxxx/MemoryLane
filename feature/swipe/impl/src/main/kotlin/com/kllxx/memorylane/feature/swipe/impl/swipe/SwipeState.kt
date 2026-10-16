package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.photos.domain.model.Photo
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** [date] comes from the route argument, so it has no default. */
data class SwipeState(
    val date: LocalDate,
    val photos: ImmutableList<Photo> = persistentListOf(),
    val currentIndex: Int = 0,
    val pendingDeletes: ImmutableList<Long> = persistentListOf(),
    val isLoading: Boolean = false,
    val isFinished: Boolean = false,
)
