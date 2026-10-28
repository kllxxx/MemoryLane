package com.kllxx.memorylane.feature.swipe.api

import kotlinx.serialization.Serializable

/** «Перед удалением»: the photos of the day marked for deletion. [date] is the ISO-8601 date of the day, e.g. "2026-10-15". */
@Serializable
data class BeforeDeleteRoute(val date: String)
