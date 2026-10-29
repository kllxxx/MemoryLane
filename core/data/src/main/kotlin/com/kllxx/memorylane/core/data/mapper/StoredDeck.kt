package com.kllxx.memorylane.core.data.mapper

import com.kllxx.memorylane.core.domain.model.DeckKey
import java.time.LocalDate

/**
 * The only place that knows how the undated deck is stored: as the reserved date 0001-01-01 in the date columns of
 * `photo_decisions` and `swipe_sessions`, which no real photo or day can have. Everything above the data layer sees a
 * [DeckKey]. The DAO queries that must leave the undated deck out of the days refer to [UNDATED_EPOCH_DAY].
 */
internal object StoredDeck {
    /** `LocalDate.of(1, 1, 1).toEpochDay()`, a constant so that the queries can use it. */
    const val UNDATED_EPOCH_DAY = -719162L

    val UNDATED_DATE: LocalDate = LocalDate.ofEpochDay(UNDATED_EPOCH_DAY)
}

/** The date column value of the deck. */
internal fun DeckKey.toStoredDate(): LocalDate = when (this) {
    is DeckKey.Day -> date
    DeckKey.Undated -> StoredDeck.UNDATED_DATE
}

/** The deck a date column value stands for. */
internal fun LocalDate.toDeckKey(): DeckKey = if (this == StoredDeck.UNDATED_DATE) DeckKey.Undated else DeckKey.Day(this)
