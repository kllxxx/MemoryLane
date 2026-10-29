package com.kllxx.memorylane.feature.swipe.api

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * «Перед удалением»: the photos marked for deletion, reached from the deck of [deck]. [date] is the ISO-8601 date of the
 * day for [DeckKind.DAY] and null for [DeckKind.UNDATED].
 */
@Serializable
data class BeforeDeleteRoute(val deck: DeckKind = DeckKind.DAY, val date: String? = null) {
    companion object {
        fun day(date: LocalDate) = BeforeDeleteRoute(DeckKind.DAY, date.toString())

        fun undated() = BeforeDeleteRoute(DeckKind.UNDATED)
    }
}
