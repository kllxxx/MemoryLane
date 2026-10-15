package com.kllxx.memorylane.feature.swipe.api

import kotlinx.serialization.Serializable

/** [date] is the ISO-8601 date of the swipe session to review, e.g. "2026-10-15". */
@Serializable
data class ReviewRoute(val date: String)
