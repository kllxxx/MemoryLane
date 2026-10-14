package com.kllxx.memorylane.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object MemoryLaneShapes {
    val Card = RoundedCornerShape(16.dp)
    val Dialog = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(8.dp)
    val PrimaryButton = RoundedCornerShape(12.dp)
    val TextField = RoundedCornerShape(8.dp)
    val StatCard = RoundedCornerShape(12.dp)
}

internal val MemoryLaneMaterialShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)
