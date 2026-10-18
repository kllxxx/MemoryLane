package com.kllxx.memorylane.feature.achievements.impl

sealed interface AchievementsEffect {
    /** The streak goal could not be stored and was put back; there is no UI for this yet. */
    data object ShowSettingsError : AchievementsEffect
}
