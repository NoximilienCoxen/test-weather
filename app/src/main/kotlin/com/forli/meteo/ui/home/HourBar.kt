package com.forli.meteo.ui.home

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoColors

/**
 * Le ore del giorno come una striscia continua, colorata dal meteo di ciascuna.
 *
 * Una fascia azzurra dice a colpo d'occhio quando piove, senza bisogno di
 * scorrere fin li' per scoprirlo: e' la barra stessa a raccontare la giornata.
 */
@Composable
fun HourBar(
    hours: List<HourForecast>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val haptics = LocalHapticFeedback.current
    if (hours.isEmpty()) return

    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "ora",
    )

    fun indexAt(x: Float, width: Float): Int =
        ((x / width) * hours.size).toInt().coerceIn(0, hours.lastIndex)

    fun choose(index: Int) {
        if (index != selected) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(index)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(hours.size) {
                detectTapGestures { offset -> choose(indexAt(offset.x, size.width.toFloat())) }
            }
            .pointerInput(hours.size) {
                detectHorizontalDragGestures { change, _ ->
                    choose(indexAt(change.position.x, size.width.toFloat()))
                }
            },
    ) {
        val trackHeight = size.height * 0.42f
        val top = (size.height - trackHeight) / 2f
        val radius = trackHeight / 2f
        val slot = size.width / hours.size

        // Ritaglio sulla pista arrotondata e poi dipingo le ore dentro: cosi'
        // gli estremi sono tondi senza dover coprire nulla.
        val track = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, top, size.width, top + trackHeight),
                    cornerRadius = CornerRadius(radius, radius),
                ),
            )
        }
        clipPath(track) {
            hours.forEachIndexed { index, hour ->
                drawRect(
                    color = tintOf(hour, colors),
                    topLeft = Offset(index * slot, top),
                    // Mezzo pixel in piu' evita la riga di fondo fra un'ora e
                    // l'altra dovuta all'antialiasing.
                    size = Size(slot + 0.5f, trackHeight),
                )
            }
        }

        val thumbX = (position + 0.5f) * slot
        val thumbWidth = trackHeight * 0.62f
        drawRoundRect(
            color = colors.pillBackground,
            topLeft = Offset(thumbX - thumbWidth / 2f, top - trackHeight * 0.42f),
            size = Size(thumbWidth, trackHeight * 1.84f),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
        )
    }
}

/** Colore di un'ora: asciutto resta neutro, il resto si dichiara. */
private fun tintOf(hour: HourForecast, colors: MeteoColors): Color {
    val base = when (Wmo.family(hour.weatherCode)) {
        Wmo.Family.ASCIUTTO -> colors.line
        Wmo.Family.NUVOLOSO -> colors.label.copy(alpha = 0.55f)
        Wmo.Family.NEBBIA -> colors.label.copy(alpha = 0.40f)
        Wmo.Family.PIOGGIA -> Color(0xFF2C7BF2)
        Wmo.Family.NEVE -> Color(0xFF8FC7F5)
        Wmo.Family.TEMPORALE -> Color(0xFF5B4BC4)
    }
    // La notte smorza, cosi' la striscia racconta anche il passare del giorno.
    return if (hour.isDay) base else base.copy(alpha = base.alpha * 0.55f)
}

internal fun nearestHourIndex(hours: List<HourForecast>, target: java.time.LocalDateTime): Int {
    if (hours.isEmpty()) return 0
    var best = 0
    var bestDistance = Long.MAX_VALUE
    hours.forEachIndexed { index, hour ->
        val distance = kotlin.math.abs(
            java.time.Duration.between(target, hour.time).toMinutes(),
        )
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

internal fun hourLabel(hour: HourForecast?): String =
    hour?.time?.hour?.let { "%02d:00".format(it) } ?: "--"
