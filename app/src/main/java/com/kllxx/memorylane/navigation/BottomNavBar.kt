package com.kllxx.memorylane.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.theme.NunitoFontFamily

private val BarHeight = 64.dp
private val IconSize = 24.dp
private val IconLabelGap = 2.dp
private val LabelSize = 11.sp
private val LabelLetterSpacing = 0.44.sp // 4% of the label size
private const val RIPPLE_ALPHA = 0.12f

internal data class BottomBarTab(
    val label: String,
    @DrawableRes val outlinedIcon: Int,
    @DrawableRes val filledIcon: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Bottom navigation bar. Selection is shown only by icon fill, color and label weight; there is no
 * indicator pill. The bar content is [BarHeight] tall, the system navigation bar inset goes below it.
 */
@Composable
internal fun BottomNavBar(tabs: List<BottomBarTab>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth().height(BarHeight - 1.dp)) {
                tabs.forEach { tab -> BottomBarItem(tab, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BottomBarItem(tab: BottomBarTab, modifier: Modifier = Modifier) {
    val color = if (tab.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(BarHeight - 1.dp)
            .selectable(
                selected = tab.selected,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary.copy(alpha = RIPPLE_ALPHA)),
                role = Role.Tab,
                onClick = tab.onClick,
            )
            .semantics { contentDescription = tab.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IconLabelGap, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(if (tab.selected) tab.filledIcon else tab.outlinedIcon),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(color),
        )
        Text(
            text = tab.label,
            color = color,
            fontFamily = NunitoFontFamily,
            fontSize = LabelSize,
            fontWeight = if (tab.selected) FontWeight.ExtraBold else FontWeight.Bold,
            letterSpacing = LabelLetterSpacing,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
