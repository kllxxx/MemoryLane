package com.kllxx.memorylane.feature.swipe.api

import java.time.LocalDate
import kotlinx.serialization.Serializable

/** The deck to sort: the [DeckKind.DAY] deck of [date] (an ISO-8601 date), or the [DeckKind.UNDATED] one, which has no date. */
@Serializable
data class SwipeRoute(val deck: DeckKind = DeckKind.DAY, val date: String? = null) {
    companion object {
        fun day(date: LocalDate) = SwipeRoute(DeckKind.DAY, date.toString())

        fun undated() = SwipeRoute(DeckKind.UNDATED)
    }
}
