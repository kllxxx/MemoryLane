package com.kllxx.memorylane.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.core.ui.theme.DarkSpringGreen
import com.kllxx.memorylane.core.ui.theme.Emerald

private val DotSize = 8.dp
private val ActiveWidth = 24.dp
private val DotSpacing = 6.dp

/** Dots with the active one stretched into a pill. */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = DarkSpringGreen,
    inactiveColor: Color = Emerald.copy(alpha = 0.3f),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DotSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            val active = page == currentPage
            val width: Dp by animateDpAsState(if (active) ActiveWidth else DotSize, label = "dotWidth")
            val color by animateColorAsState(if (active) activeColor else inactiveColor, label = "dotColor")
            Box(
                Modifier
                    .size(width = width, height = DotSize)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
