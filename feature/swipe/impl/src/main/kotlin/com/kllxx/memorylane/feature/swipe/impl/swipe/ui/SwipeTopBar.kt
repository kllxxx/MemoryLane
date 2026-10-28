package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.feature.swipe.impl.R

private val DateHeight = 25.dp
private val LocationHeight = 18.dp

/**
 * The date and the place of the photo on top, and the close button. The height is fixed, so the card below never
 * jumps between photos: a photo without a place leaves its row empty.
 */
@Composable
internal fun SwipeTopBar(
    dateText: String?,
    location: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SwipeLayout.TopBarHeight)
            .padding(horizontal = SwipeLayout.Gutter),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
            Box(modifier = Modifier.height(DateHeight), contentAlignment = Alignment.CenterStart) {
                if (!dateText.isNullOrEmpty()) {
                    Text(
                        text = dateText,
                        modifier = Modifier.testTag(SwipeTags.TOP_BAR_DATE),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(modifier = Modifier.height(LocationHeight), verticalAlignment = Alignment.CenterVertically) {
                if (!location.isNullOrEmpty()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_location_on),
                        contentDescription = stringResource(R.string.swipe_location_icon),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = location,
                        modifier = Modifier.testTag(SwipeTags.TOP_BAR_LOCATION),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button, onClick = onClose)
                .testTag(SwipeTags.CLOSE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.swipe_close),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Sizes of the Swipe screen that more than one composable depends on. */
internal object SwipeLayout {
    val Gutter = 20.dp
    val TopBarHeight = 64.dp
    val TopBarGap = 4.dp

    /** The gap between the photo area and the bottom panel, and the panel's margin from the screen edges. */
    val PanelGap = 12.dp
    val PanelMargin = 12.dp
    val PanelRadius = 24.dp

    /**
     * The space around the cards in the photo area: the gutter on each side and above, and the same below plus the
     * panel gap (the gap belongs to the area, so the ✕ and ✓ that hang over a tall card stay inside it and can be tapped).
     */
    val CardAreaTop = 20.dp
    val CardAreaBottom = 20.dp + PanelGap

    val CardRadius = 20.dp
    val DecisionButton = 60.dp
}
