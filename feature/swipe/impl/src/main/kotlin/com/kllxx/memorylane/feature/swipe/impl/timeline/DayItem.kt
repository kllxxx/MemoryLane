package com.kllxx.memorylane.feature.swipe.impl.timeline

import com.kllxx.memorylane.core.photos.domain.model.Photo
import java.time.LocalDate

data class DayItem(
    val date: LocalDate,
    val firstPhoto: Photo?,
    val totalCount: Int,
)
