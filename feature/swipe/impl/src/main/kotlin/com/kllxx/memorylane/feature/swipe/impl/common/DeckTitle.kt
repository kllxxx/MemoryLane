package com.kllxx.memorylane.feature.swipe.impl.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.kllxx.memorylane.core.domain.format.formatDayMonth
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.feature.swipe.impl.R
import java.util.Locale

/** What a deck is called on «Перед удалением» and «Итоги»: «28 октября» for a day, [undatedLabel] for the undated photos. */
fun deckTitle(deck: DeckKey, locale: Locale, undatedLabel: String): String = when (deck) {
    is DeckKey.Day -> formatDayMonth(deck.date, locale)
    DeckKey.Undated -> undatedLabel
}

@Composable
fun rememberDeckTitle(deck: DeckKey): String {
    val locale = LocalConfiguration.current.locales[0]
    val undatedLabel = stringResource(R.string.deck_undated)
    return remember(deck, locale, undatedLabel) { deckTitle(deck, locale, undatedLabel) }
}
