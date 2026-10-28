package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.feature.swipe.impl.R

private const val DISABLED_ALPHA = 0.38f

/**
 * Undo, share, open in the gallery, and the switch of buttons mode, in four equal columns. Undo is disabled while
 * there is nothing to undo; the buttons switch is filled while buttons mode is on.
 */
@Composable
internal fun QuickActions(
    canUndo: Boolean,
    buttonsMode: Boolean,
    onUndo: () -> Unit,
    onShare: () -> Unit,
    onOpenInGallery: () -> Unit,
    onToggleButtons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        QuickAction(
            iconRes = UiR.drawable.ic_undo,
            label = stringResource(R.string.swipe_action_undo),
            description = stringResource(R.string.swipe_action_undo_description),
            tag = SwipeTags.ACTION_UNDO,
            enabled = canUndo,
            onClick = onUndo,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            iconRes = UiR.drawable.ic_share,
            label = stringResource(R.string.swipe_action_share),
            description = stringResource(R.string.swipe_action_share_description),
            tag = SwipeTags.ACTION_SHARE,
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            iconRes = UiR.drawable.ic_open_in_new,
            label = stringResource(R.string.swipe_action_gallery),
            description = stringResource(R.string.swipe_action_gallery_description),
            tag = SwipeTags.ACTION_GALLERY,
            onClick = onOpenInGallery,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            iconRes = UiR.drawable.ic_touch_app,
            label = stringResource(R.string.swipe_action_buttons),
            description = stringResource(R.string.swipe_action_buttons_description),
            tag = SwipeTags.ACTION_BUTTONS,
            selected = buttonsMode,
            onToggle = { onToggleButtons() },
            modifier = Modifier.weight(1f),
        )
    }
}

/** A 40dp circle with a 20dp icon and a label under it; [onToggle] makes it a switch that is filled while [selected]. */
@Composable
private fun QuickAction(
    iconRes: Int,
    label: String,
    description: String,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onToggle: ((Boolean) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val press = if (onToggle != null) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(press)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .testTag(tag)
                .clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) colors.onPrimary else colors.primary,
                )
            }
            Text(
                text = label,
                modifier = Modifier.padding(top = 4.dp),
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
