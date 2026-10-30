package com.kllxx.memorylane.feature.swipe.impl.result

sealed interface ResultEvent {
    /** The current streak, as the [com.kllxx.memorylane.core.domain.repository.StatsRepository] reads it. */
    data class StreakLoaded(val currentStreak: Int) : ResultEvent
}
