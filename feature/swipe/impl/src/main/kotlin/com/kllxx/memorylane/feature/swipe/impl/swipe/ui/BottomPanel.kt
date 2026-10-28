package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.StripItem

/**
 * The card at the bottom of the screen that holds the strip of photos and the quick actions, with a divider between
 * them. The caller places it: 12dp from the screen edges and 12dp above the gesture area.
 */
@Composable
internal fun BottomPanel(
    strip: List<StripItem>,
    currentDeckIndex: Int,
    onJump: (deckIndex: Int) -> Unit,
    canUndo: Boolean,
    buttonsMode: Boolean,
    onUndo: () -> Unit,
    onShare: () -> Unit,
    onOpenInGallery: () -> Unit,
    onToggleButtons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(SwipeLayout.PanelRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SwipeTags.PANEL)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(width = 1.dp, color = colors.outlineVariant, shape = shape),
    ) {
        Spacer(Modifier.height(8.dp))
        PhotoStrip(strip = strip, currentDeckIndex = currentDeckIndex, onJump = onJump)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = colors.outlineVariant,
        )
        Spacer(Modifier.height(9.dp))
        QuickActions(
            canUndo = canUndo,
            buttonsMode = buttonsMode,
            onUndo = onUndo,
            onShare = onShare,
            onOpenInGallery = onOpenInGallery,
            onToggleButtons = onToggleButtons,
        )
        Spacer(Modifier.height(11.dp))
    }
}
