package com.kllxx.memorylane.navigation

import kotlinx.serialization.Serializable

/** Main (Timeline) tab. Lives in :app because :app owns the bottom navigation. */
@Serializable
data object TimelineRoute

enum class StartDestination { Onboarding, Swipe, Main }
