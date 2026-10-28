package com.kllxx.memorylane.navigation

import kotlinx.serialization.Serializable

/** Main (Calendar) tab. Lives in :app because :app owns the bottom navigation. */
@Serializable
data object CalendarRoute

/** The app opens on Onboarding until it is done, and on Calendar after that: never on a day. */
enum class StartDestination { Onboarding, Main }
