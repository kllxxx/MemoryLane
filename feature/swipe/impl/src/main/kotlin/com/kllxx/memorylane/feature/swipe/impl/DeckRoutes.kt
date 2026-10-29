package com.kllxx.memorylane.feature.swipe.impl

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.feature.swipe.api.BeforeDeleteRoute
import com.kllxx.memorylane.feature.swipe.api.DeckKind
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import java.time.LocalDate

/** The deck a route is for. A day route without a date is a programming error, not a deck. */
internal fun deckKeyOf(deck: DeckKind, date: String?): DeckKey = when (deck) {
    DeckKind.DAY -> DeckKey.Day(LocalDate.parse(requireNotNull(date) { "A day route needs a date" }))
    DeckKind.UNDATED -> DeckKey.Undated
}

internal fun SwipeRoute.toDeckKey(): DeckKey = deckKeyOf(deck, date)

internal fun BeforeDeleteRoute.toDeckKey(): DeckKey = deckKeyOf(deck, date)

internal fun ResultRoute.toDeckKey(): DeckKey = deckKeyOf(deck, date)

/** The routes of the screens for a deck, for the app's navigation. */
fun DeckKey.toSwipeRoute(): SwipeRoute = when (this) {
    is DeckKey.Day -> SwipeRoute.day(date)
    DeckKey.Undated -> SwipeRoute.undated()
}

fun DeckKey.toBeforeDeleteRoute(): BeforeDeleteRoute = when (this) {
    is DeckKey.Day -> BeforeDeleteRoute.day(date)
    DeckKey.Undated -> BeforeDeleteRoute.undated()
}

fun DeckKey.toResultRoute(deletedCount: Int, freedBytes: Long, keptCount: Int, totalCount: Int): ResultRoute = when (this) {
    is DeckKey.Day -> ResultRoute.day(date, deletedCount, freedBytes, keptCount, totalCount)
    DeckKey.Undated -> ResultRoute.undated(deletedCount, freedBytes, keptCount, totalCount)
}
