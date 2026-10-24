package com.kllxx.memorylane.feature.achievements.impl

sealed interface AchievementsIntent {
    data object GoToCalendarClicked : AchievementsIntent
}
