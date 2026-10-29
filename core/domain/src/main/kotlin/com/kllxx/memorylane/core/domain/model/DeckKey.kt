package com.kllxx.memorylane.core.domain.model

import java.time.LocalDate

/**
 * Which deck of photos is being sorted: the photos of one On This Day [Day], or the photos [Undated] that have no date
 * anywhere. How the undated deck is stored is the business of the data layer alone.
 */
sealed interface DeckKey {
    data class Day(val date: LocalDate) : DeckKey

    /** The photos that have no date after the full date chain. They are never a day of the calendar and never count for a streak. */
    data object Undated : DeckKey
}
