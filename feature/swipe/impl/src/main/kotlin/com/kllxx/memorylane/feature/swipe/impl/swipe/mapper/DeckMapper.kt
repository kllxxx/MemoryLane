package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.feature.swipe.impl.swipe.DeckPhoto
import java.util.Locale

fun Photo.toDeckPhoto(decision: PhotoDecision?, locale: Locale) = DeckPhoto(
    photo = this,
    decision = decision,
    takenAtText = formatTakenAt(takenAt, locale),
    aspectRatio = displayAspectRatio(),
)
