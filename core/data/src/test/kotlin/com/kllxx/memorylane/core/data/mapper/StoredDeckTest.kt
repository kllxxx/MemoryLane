package com.kllxx.memorylane.core.data.mapper

import com.kllxx.memorylane.core.domain.model.DeckKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredDeckTest {

    private val day = LocalDate.of(2026, 10, 15)

    @Test
    fun `a day is stored as its own date`() {
        assertEquals(day, DeckKey.Day(day).toStoredDate())
    }

    @Test
    fun `the undated deck is stored as the reserved date`() {
        assertEquals(LocalDate.of(1, 1, 1), DeckKey.Undated.toStoredDate())
    }

    @Test
    fun `a stored date comes back as the deck it stands for`() {
        assertEquals(DeckKey.Day(day), day.toDeckKey())
        assertEquals(DeckKey.Undated, LocalDate.of(1, 1, 1).toDeckKey())
    }

    @Test
    fun `every deck survives the round trip`() {
        listOf(DeckKey.Day(day), DeckKey.Day(LocalDate.EPOCH), DeckKey.Day(LocalDate.of(1990, 1, 1)), DeckKey.Undated).forEach {
            assertEquals(it, it.toStoredDate().toDeckKey())
        }
    }

    @Test
    fun `no real date is mistaken for the undated deck`() {
        assertEquals(DeckKey.Day(LocalDate.of(1, 1, 2)), LocalDate.of(1, 1, 2).toDeckKey())
        assertEquals(DeckKey.Day(LocalDate.EPOCH), LocalDate.EPOCH.toDeckKey())
    }
}
