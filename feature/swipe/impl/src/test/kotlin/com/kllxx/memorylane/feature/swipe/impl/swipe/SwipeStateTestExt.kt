package com.kllxx.memorylane.feature.swipe.impl.swipe

import com.kllxx.memorylane.core.domain.model.DayCompletion
import com.kllxx.memorylane.core.domain.model.PhotoDecision

/** Whether every photo of the deck has a decision, by the one rule of the app. */
val SwipeState.isDayCompleted: Boolean
    get() = DayCompletion.isCompleted(
        deck.map { it.id },
        deck.mapNotNull { photo -> photo.decision?.let { photo.id to it } }.toMap<Long, PhotoDecision>(),
    )
