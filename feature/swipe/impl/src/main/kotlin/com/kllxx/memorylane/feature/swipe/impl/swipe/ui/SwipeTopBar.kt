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
import androidx.compose.ui.platform.LocalDensity
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

// The line heights of the date and of the place, in sp: they grow with the font size, so the text is never clipped
private val DateLineHeight = 25.sp
private val LocationLineHeight = 18.sp
private val TextPadding = 4.dp
private val TextGap = 3.dp

/**
 * The date and the place of the photo on top, and the close button. The bar is as high as the date and the place
 * together need (at least 56dp), the same for every photo, so the card below never moves. Inside it the text column
 * holds only what is shown, so a photo without a place has the date alone, and the column is centred on the button.
 */
@Composable
internal fun SwipeTopBar(
    dateText: String?,
    location: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dateHeight = with(density) { DateLineHeight.toDp() }
    val locationHeight = with(density) { LocationLineHeight.toDp() }
    val barHeight = maxOf(SwipeLayout.TopBarHeight, TextPadding * 2 + dateHeight + TextGap + locationHeight)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .padding(horizontal = SwipeLayout.TopBarGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f).testTag(SwipeTags.TOP_BAR_TEXT)) {
            if (!dateText.isNullOrEmpty()) {
                Text(
                    text = dateText,
                    modifier = Modifier.testTag(SwipeTags.TOP_BAR_DATE),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    lineHeight = DateLineHeight,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!location.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.padding(top = TextGap).height(locationHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        lineHeight = LocationLineHeight,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
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
    val TopBarGutter = 16.dp
    val TopBarHeight = 56.dp
    val TopBarGap = 2.dp

    /** The card of the quick actions: its margin from the screen edges and the bottom, and the gap under the strip. */
    val ActionsCardMargin = 12.dp
    val ActionsCardRadius = 20.dp
    val StripGap = 8.dp

    /** The space on each side of the cards in the photo area: a 3:4 photo is 344dp wide on a 360dp screen. */
    val CardInset = 8.dp

    /**
     * The space above and below the cards in the photo area. The gap above the strip belongs to the area. In buttons
     * mode the room below grows to half of a ✕ / ✓ circle, so the circles that hang over the bottom edge of a tall
     * card stay inside the area and can be tapped, and the room above grows by the same amount, so the centre of
     * the cards does not move when the mode is switched.
     */
    val CardAreaTop = 4.dp
    val CardAreaBottom = StripGap

    val CardRadius = 20.dp
    val DecisionButton = 60.dp

    // Declared after DecisionButton, which it is computed from
    private val ButtonsReserve = DecisionButton / 2 - CardAreaBottom

    fun cardAreaTop(buttonsMode: Boolean) = if (buttonsMode) CardAreaTop + ButtonsReserve else CardAreaTop

    fun cardAreaBottom(buttonsMode: Boolean) = if (buttonsMode) CardAreaBottom + ButtonsReserve else CardAreaBottom
}
