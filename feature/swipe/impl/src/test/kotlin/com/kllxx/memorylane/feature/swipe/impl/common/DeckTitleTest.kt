package com.kllxx.memorylane.feature.swipe.impl.common

import com.kllxx.memorylane.core.domain.model.DeckKey
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckTitleTest {

    private val day = DeckKey.Day(LocalDate.of(2015, 10, 28))

    @Test
    fun `a day is its day and month in Russian`() {
        assertEquals("28 октября", deckTitle(day, Locale("ru"), undatedLabel = "Без даты"))
    }

    @Test
    fun `a day is its day and month in English`() {
        assertEquals("28 October", deckTitle(day, Locale.ENGLISH, undatedLabel = "Undated"))
    }

    @Test
    fun `the undated deck is the label it is given`() {
        assertEquals("Без даты", deckTitle(DeckKey.Undated, Locale("ru"), undatedLabel = "Без даты"))
    }
}
