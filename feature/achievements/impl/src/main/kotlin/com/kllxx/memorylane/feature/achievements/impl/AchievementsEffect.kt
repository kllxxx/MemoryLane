package com.kllxx.memorylane.feature.achievements.impl

sealed interface AchievementsEffect {
    data object NavigateToTimeline : AchievementsEffect
}
