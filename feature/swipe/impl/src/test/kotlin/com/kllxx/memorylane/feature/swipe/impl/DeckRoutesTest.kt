package com.kllxx.memorylane.feature.swipe.impl

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.feature.swipe.api.BeforeDeleteRoute
import com.kllxx.memorylane.feature.swipe.api.DeckKind
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeckRoutesTest {

    private val day = LocalDate.of(2026, 10, 15)

    @Test
    fun `a day is a route with the kind DAY and its date`() {
        val route = SwipeRoute.day(day)

        assertEquals(DeckKind.DAY, route.deck)
        assertEquals("2026-10-15", route.date)
        assertEquals(DeckKey.Day(day), route.toDeckKey())
    }

    @Test
    fun `the undated deck is a route with the kind UNDATED and no date`() {
        val route = SwipeRoute.undated()

        assertEquals(DeckKind.UNDATED, route.deck)
        assertNull(route.date)
        assertEquals(DeckKey.Undated, route.toDeckKey())
    }

    @Test
    fun `a deck goes into the three routes and comes back out of them`() {
        listOf(DeckKey.Day(day), DeckKey.Undated).forEach { deck ->
            assertEquals(deck, deck.toSwipeRoute().toDeckKey())
            assertEquals(deck, deck.toBeforeDeleteRoute().toDeckKey())
            assertEquals(deck, deck.toResultRoute(1, 2L, 3, 4).toDeckKey())
        }
    }

    @Test
    fun `the result route keeps the numbers`() {
        assertEquals(ResultRoute.day(day, 1, 2L, 3, 4), DeckKey.Day(day).toResultRoute(1, 2L, 3, 4))
        assertEquals(ResultRoute.undated(1, 2L, 3, 4), DeckKey.Undated.toResultRoute(1, 2L, 3, 4))
    }

    @Test
    fun `the routes of the two kinds are different values`() {
        assertEquals(false, SwipeRoute.undated() == SwipeRoute.day(day))
        assertEquals(false, BeforeDeleteRoute.undated() == BeforeDeleteRoute.day(day))
    }

    @Test
    fun `a day route without a date is a programming error`() {
        assertThrows(IllegalArgumentException::class.java) { SwipeRoute(DeckKind.DAY, null).toDeckKey() }
    }

    @Test
    fun `a route made without a kind is a day, as before`() {
        assertEquals(DeckKey.Day(day), SwipeRoute(date = "2026-10-15").toDeckKey())
    }
}
