package com.kllxx.memorylane.feature.swipe.impl.result.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.core.ui.theme.semanticColors

private enum class Tint { Primary, Green, Red, Yellow }

/** A dot ([round]) or a rounded bar, placed in the 260 by 150dp area as the design has it; [angle] is in degrees. */
private class Piece(val x: Float, val y: Float, val width: Float, val height: Float, val tint: Tint, val round: Boolean = false, val angle: Float = 0f)

private val WarmYellow = Color(0xFFFFC857)

private val Pieces = listOf(
    Piece(18f, 28f, 13f, 9.6f, Tint.Green, angle = -20f),
    Piece(62f, 12.6f, 12.9f, 10.3f, Tint.Yellow, angle = 30f),
    Piece(214f, 21f, 12.4f, 11.5f, Tint.Red, angle = -35f),
    Piece(236f, 84f, 13f, 8.8f, Tint.Green, angle = 25f),
    Piece(244f, 49f, 12.9f, 7.9f, Tint.Primary, angle = 60f),
    Piece(10f, 119f, 10.3f, 12.9f, Tint.Yellow, angle = 35f),
    Piece(150f, 130.2f, 12f, 12f, Tint.Red, angle = 45f),
    Piece(40f, 98f, 9f, 9f, Tint.Red, round = true),
    Piece(30f, 66.5f, 9f, 9f, Tint.Primary, round = true),
    Piece(200f, 117.6f, 9f, 9f, Tint.Yellow, round = true),
    Piece(96f, 123.2f, 9f, 9f, Tint.Green, round = true),
    Piece(176f, 5.6f, 9f, 9f, Tint.Green, round = true),
)

/** Static confetti around the circle of «Итоги»: small dots and rounded bars in primary, green, red and yellow. */
@Composable
internal fun Confetti(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val green = MaterialTheme.semanticColors.keep
    val red = MaterialTheme.colorScheme.error
    Canvas(modifier.size(ConfettiWidth, ConfettiHeight)) {
        Pieces.forEach { piece ->
            val color = when (piece.tint) {
                Tint.Primary -> primary
                Tint.Green -> green
                Tint.Red -> red
                Tint.Yellow -> WarmYellow
            }
            val topLeft = Offset(piece.x.dp.toPx(), piece.y.dp.toPx())
            val size = Size(piece.width.dp.toPx(), piece.height.dp.toPx())
            if (piece.round) {
                drawCircle(color, radius = size.width / 2, center = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2))
            } else {
                rotate(piece.angle, pivot = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)) {
                    drawRoundRect(color, topLeft, size, CornerRadius(2.dp.toPx()))
                }
            }
        }
    }
}

internal val ConfettiWidth = 260.dp
internal val ConfettiHeight = 150.dp
