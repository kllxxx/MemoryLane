package com.kllxx.memorylane.feature.swipe.impl.result

import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.core.domain.model.DeckKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultReducerTest {

    private val state = ResultState(
        deck = DeckKey.Day(LocalDate.of(2026, 10, 29)),
        deletedCount = 1,
        keptCount = 2,
        totalCount = 3,
        freed = FormattedSize("0", SizeUnit.MB),
    )

    @Test
    fun `a streak shows in the chip`() {
        assertEquals(5, ResultReducer.reduce(state, ResultEvent.StreakLoaded(5)).streakDays)
    }

    @Test
    fun `a streak of zero is no chip`() {
        assertNull(ResultReducer.reduce(state.copy(streakDays = 3), ResultEvent.StreakLoaded(0)).streakDays)
    }

    @Test
    fun `done leaves the state alone`() {
        assertEquals(state, ResultReducer.reduce(state, ResultIntent.Done))
    }
}
