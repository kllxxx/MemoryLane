package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.photos.domain.model.Photo
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class SwipeState(
    val photos: ImmutableList<Photo> = persistentListOf(),
    val currentIndex: Int = 0,
    val pendingDeletes: ImmutableList<Long> = persistentListOf(),
    val isLoading: Boolean = false,
    val isFinished: Boolean = false,
    val date: LocalDate = LocalDate.now(),
)
