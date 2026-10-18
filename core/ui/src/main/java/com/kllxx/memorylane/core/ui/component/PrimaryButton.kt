package com.kllxx.memorylane.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.kllxx.memorylane.core.ui.theme.BabyPowder
import com.kllxx.memorylane.core.ui.theme.DarkSpringGreen
import com.kllxx.memorylane.core.ui.theme.MemoryLaneShapes
import com.kllxx.memorylane.core.ui.theme.Spacing

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.PrimaryButtonHeight),
        enabled = enabled,
        shape = MemoryLaneShapes.PrimaryButton,
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkSpringGreen,
            contentColor = BabyPowder,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
