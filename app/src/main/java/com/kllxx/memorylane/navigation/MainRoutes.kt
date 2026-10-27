package com.kllxx.memorylane.navigation

import kotlinx.serialization.Serializable

/** Main (Calendar) tab. Lives in :app because :app owns the bottom navigation. */
@Serializable
data object CalendarRoute

enum class StartDestination { Onboarding, Swipe, Main }
