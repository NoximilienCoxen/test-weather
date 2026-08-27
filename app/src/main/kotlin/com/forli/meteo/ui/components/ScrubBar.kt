package com.forli.meteo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.theme.LocalMeteoColors
import kotlin.math.roundToInt

/**
 * Barra di scrub trascinabile: un pallino per giorno e un pollice che scatta
 * sull'indice piu' vicino. Risponde sia al trascinamento sia al tocco secco.
 */
@Composable
fun ScrubBar(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 900f),
        label = "scrub",
    )

    fun indexAt(x: Float, width: Float): Int {
        if (count <= 1 || width <= 0f) return 0
        val inset = width * INSET_FRACTION
        val usable = (width - inset * 2f).coerceAtLeast(1f)
        return (((x - inset) / usable) * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(count) {
                detectTapGestures { offset -> onSelect(indexAt(offset.x, size.width.toFloat())) }
            }
            .pointerInput(count) {
                detectHorizontalDragGestures { change, _ ->
                    onSelect(indexAt(change.position.x, size.width.toFloat()))
                }
            },
    ) {
        val trackHeight = size.height * 0.62f
        val top = (size.height - trackHeight) / 2f
        val radius = trackHeight / 2f

        drawRoundRect(
            color = colors.line.copy(alpha = 0.55f),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
        )

        val inset = size.width * INSET_FRACTION
        val usable = size.width - inset * 2f
        val centreY = size.height / 2f

        repeat(count) { i ->
            val x = inset + usable * i / (count - 1).coerceAtLeast(1).toFloat()
            drawCircle(
                color = colors.label.copy(alpha = 0.9f),
                radius = trackHeight * 0.10f,
                center = Offset(x, centreY),
            )
        }

        val thumbX = inset + usable * position / (count - 1).coerceAtLeast(1).toFloat()
        val thumbWidth = trackHeight * 1.5f
        drawRoundRect(
            color = colors.pillBackground,
            topLeft = Offset(thumbX - thumbWidth / 2f, top - trackHeight * 0.12f),
            size = Size(thumbWidth, trackHeight * 1.24f),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private const val INSET_FRACTION = 0.045f
