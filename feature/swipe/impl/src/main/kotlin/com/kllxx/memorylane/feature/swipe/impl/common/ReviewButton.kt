package com.kllxx.memorylane.feature.swipe.impl.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ReviewButtonHeight = 56.dp
private val ReviewButtonShape = RoundedCornerShape(16.dp)
private const val DISABLED_ALPHA = 0.5f

/** The 56dp, radius 16 button at the bottom of «Перед удалением» and «Итоги»; red or green by [containerColor]. */
@Composable
internal fun ReviewButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(ReviewButtonHeight),
        enabled = enabled,
        shape = ReviewButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = contentColor.copy(alpha = DISABLED_ALPHA),
        ),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
