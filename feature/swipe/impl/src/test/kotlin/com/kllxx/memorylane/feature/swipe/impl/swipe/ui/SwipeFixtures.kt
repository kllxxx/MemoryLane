package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import com.kllxx.memorylane.core.domain.model.DeckKey
import android.net.Uri
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.feature.swipe.impl.swipe.DeckPhoto
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.collections.immutable.toImmutableList

internal val FixtureDate: LocalDate = LocalDate.of(2026, 1, 7)

internal fun uriOf(id: Long): Uri = Uri.parse("content://media/external/images/media/$id")

/** A photo of the deck, taken [id] years after 2015, so the ids are also the order in time. */
internal fun deckPhoto(
    id: Long,
    decision: PhotoDecision? = null,
    location: String? = null,
    aspectRatio: Float? = null,
) = DeckPhoto(
    photo = Photo(id, uriOf(id), LocalDateTime.of(2015, 1, 7, 21, 15).plusYears(id), sizeBytes = 100, location = location),
    decision = decision,
    takenAtText = "7 января ${2015 + id}, 21:15",
    aspectRatio = aspectRatio,
)

internal fun swipeState(
    count: Int,
    currentIndex: Int = 0,
    decisions: Map<Long, PhotoDecision> = emptyMap(),
    trashed: List<DeckPhoto> = emptyList(),
    aspectRatio: Float? = null,
    aspectRatios: Map<Long, Float> = emptyMap(),
    buttonsMode: Boolean = false,
    location: String? = null,
) = SwipeState(
    deckKey = DeckKey.Day(FixtureDate),
    deck = (1..count).map { deckPhoto(it.toLong(), decisions[it.toLong()], location, aspectRatios[it.toLong()] ?: aspectRatio) }.toImmutableList(),
    trashed = trashed.toImmutableList(),
    currentIndex = currentIndex,
    buttonsMode = buttonsMode,
)
