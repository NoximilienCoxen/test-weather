package com.forli.meteo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private val RainBlue = Color(0xFF2C7BF2)

private data class Drop(
    val x: Float,
    val length: Float,
    val speed: Float,
    val phase: Float,
    val alpha: Float,
)

/**
 * Pioggia a barrette verticali. La densita' segue la probabilita' del giorno
 * selezionato: a zero non cade nulla, al cento lo schermo e' pieno.
 */
@Composable
fun RainOverlay(
    probability: Int,
    modifier: Modifier = Modifier,
) {
    val clamped = probability.coerceIn(0, 100)
    val count = (clamped * MAX_DROPS / 100f).toInt()
    if (count == 0) return

    val drops = remember(count) {
        val random = Random(count * 7919)
        List(count) {
            Drop(
                x = random.nextFloat(),
                length = 0.05f + random.nextFloat() * 0.16f,
                speed = 0.65f + random.nextFloat() * 0.75f,
                phase = random.nextFloat(),
                alpha = 0.5f + random.nextFloat() * 0.5f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "pioggia")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "caduta",
    )

    Canvas(modifier) {
        val barWidth = size.width * 0.014f
        drops.forEach { drop ->
            val travel = (drop.phase + progress * drop.speed) % 1f
            val lengthPx = size.height * drop.length
            val y = travel * (size.height + lengthPx) - lengthPx
            drawRoundRect(
                color = RainBlue.copy(alpha = drop.alpha),
                topLeft = Offset(drop.x * size.width, y),
                size = Size(barWidth, lengthPx),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

private const val MAX_DROPS = 46
