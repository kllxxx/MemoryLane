package com.kllxx.memorylane.feature.onboarding.api

import kotlinx.serialization.Serializable

/** @param fromProfile the flow was replayed from Profile, so finishing it goes back there instead of to Swipe. */
@Serializable
data class OnboardingRoute(val fromProfile: Boolean = false)
