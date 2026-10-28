package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.theme.semanticColors
import com.kllxx.memorylane.feature.swipe.impl.R

// Where the circles are across the card, as the design has them: a little inside each third
private const val DELETE_POSITION = 0.2875f
private const val KEEP_POSITION = 0.7125f

/**
 * The ✕ and ✓ circles of buttons mode. They are as wide as the card, so that they sit at the same places on a wide
 * card as on a narrow one; place them with their vertical centre on the bottom edge of the card.
 */
@Composable
internal fun DecisionButtons(
    cardWidth: Dp,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.semanticColors
    val radius = SwipeLayout.DecisionButton / 2
    Box(modifier = modifier.width(cardWidth).height(SwipeLayout.DecisionButton)) {
        DecisionButton(
            iconRes = UiR.drawable.ic_close,
            color = colors.delete,
            description = stringResource(R.string.swipe_button_delete),
            tag = SwipeTags.BUTTON_DELETE,
            onClick = onDelete,
            modifier = Modifier.offset(x = cardWidth * DELETE_POSITION - radius),
        )
        DecisionButton(
            iconRes = UiR.drawable.ic_check,
            color = colors.keep,
            description = stringResource(R.string.swipe_button_keep),
            tag = SwipeTags.BUTTON_KEEP,
            onClick = onKeep,
            modifier = Modifier.offset(x = cardWidth * KEEP_POSITION - radius),
        )
    }
}

@Composable
private fun DecisionButton(
    iconRes: Int,
    color: Color,
    description: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(SwipeLayout.DecisionButton)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(width = 2.dp, color = color, shape = CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(30.dp),
            tint = color,
        )
    }
}
