package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import com.kllxx.memorylane.core.domain.model.DeckKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeIntent
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeState
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.buildStrip

/** The Swipe screen for a [SwipeState]; every action goes out as an intent. */
@Composable
fun SwipeContent(
    state: SwipeState,
    onIntent: (SwipeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strip = remember(state.deck, state.trashed) { buildStrip(state.deck, state.trashed) }
    val current = state.currentPhoto

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // The design has the top bar 4dp below the status bar
        Spacer(Modifier.height(SwipeLayout.TopBarGap))
        SwipeTopBar(
            dateText = if (state.deckKey == DeckKey.Undated) stringResource(R.string.deck_undated) else current?.takenAtText,
            location = current?.photo?.location,
            onClose = { onIntent(SwipeIntent.Close) },
        )
        Spacer(Modifier.height(SwipeLayout.TopBarGap))
        // The photo area: what is between the top bar and the strip. The gap above the strip is part of it.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> {
                    val loading = stringResource(R.string.swipe_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                }
                // Once the last photo is decided the screen is on its way out; the stack would show the last card again
                state.deck.isNotEmpty() && !state.isFinished -> PhotoCardStack(state, onIntent, Modifier.fillMaxSize())
            }
        }
        // The strip floats right under the photo area, without a background
        PhotoStrip(
            strip = strip,
            currentDeckIndex = state.currentIndex,
            onJump = { onIntent(SwipeIntent.JumpTo(it)) },
        )
        Spacer(Modifier.height(SwipeLayout.StripGap))
        QuickActionsCard(
            canUndo = state.canUndo,
            buttonsMode = state.buttonsMode,
            onUndo = { onIntent(SwipeIntent.Undo) },
            onShare = { onIntent(SwipeIntent.Share) },
            onOpenInGallery = { onIntent(SwipeIntent.OpenInGallery) },
            onToggleButtons = { onIntent(SwipeIntent.ToggleButtonsMode) },
            modifier = Modifier.padding(
                start = SwipeLayout.ActionsCardMargin,
                end = SwipeLayout.ActionsCardMargin,
                bottom = SwipeLayout.ActionsCardMargin,
            ),
        )
    }
}
