package com.forli.meteo.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoColors
import kotlin.math.floor

/**
 * Le ore del giorno come una striscia continua, colorata dal meteo di ciascuna.
 *
 * Una fascia azzurra dice a colpo d'occhio quando piove, senza bisogno di
 * scorrere fin li' per scoprirlo: e' la barra stessa a raccontare la giornata.
 *
 * Le tacche non sono decorazione. Senza, la striscia sembra continua e non si
 * capisce quante posizioni abbia: se un'ora non si riesce a centrare, non c'e'
 * modo di accorgersi che il problema e' la mira e non la barra.
 */
@Composable
fun HourBar(
    hours: List<HourForecast>,
    selected: Int,
    nowIndex: Int,
    /** Alba e tramonto del giorno mostrato, se l'API li ha dati. */
    sunrise: java.time.LocalDateTime? = null,
    sunset: java.time.LocalDateTime? = null,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val haptics = LocalHapticFeedback.current
    if (hours.isEmpty()) return

    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
        label = "ora",
    )

    // Il riconoscitore di gesti vive dentro un pointerInput che viene ricreato
    // solo quando cambia il numero di ore. Tutto quello che la sua lambda
    // cattura resta fermo al valore che aveva la prima volta, e leggere di li'
    // l'ora selezionata significava confrontarsi per sempre con quella
    // dell'apertura: l'ora corrente diventava l'unica irraggiungibile della
    // giornata, perche' il confronto la dichiarava gia' scelta. Era questo a far
    // sembrare che la barra "saltasse" un'ora.
    val liveSelected by rememberUpdatedState(selected)
    val liveOnSelect by rememberUpdatedState(onSelect)

    fun indexAt(x: Float, width: Float): Int =
        floor(x / width * hours.size).toInt().coerceIn(0, hours.lastIndex)

    fun choose(index: Int) {
        if (index != liveSelected) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            liveOnSelect(index)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            // Un solo riconoscitore per tocco e trascinamento, e nessuna soglia
            // da superare. Con due riconoscitori separati il primo consuma
            // l'evento di discesa e il secondo annulla il proprio scorrimento:
            // il risultato e' una barra che ogni tanto ignora il dito, e ore
            // che sembrano non esistere.
            .pointerInput(hours.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    choose(indexAt(down.position.x, size.width.toFloat()))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        choose(indexAt(change.position.x, size.width.toFloat()))
                        change.consume()
                    }
                }
            },
    ) {
        val trackHeight = size.height * 0.34f
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

            // Una tacca ogni sei ore: abbastanza da far vedere la scansione,
            // poche da non trasformare la barra in un righello.
            for (index in hours.indices) {
                if (index == 0 || index % 6 != 0) continue
                drawRect(
                    color = colors.background.copy(alpha = 0.45f),
                    topLeft = Offset(index * slot - 0.5f, top),
                    size = Size(1f, trackHeight),
                )
            }
        }

        // Sotto la pista c'e' una fascia di annotazioni: l'ora vera, e i due
        // momenti in cui la giornata cambia luce.
        val noteY = top + trackHeight + size.height * 0.14f

        // Dove sta l'ora vera. Scorrendo la barra si guarda un'altra ora, e
        // senza questo segno non ci sarebbe piu' modo di tornare a casa.
        if (nowIndex in hours.indices) {
            drawCircle(
                color = colors.text,
                radius = size.height * 0.035f,
                center = Offset((nowIndex + 0.5f) * slot, noteY),
            )
        }

        // Alba e tramonto al minuto giusto. Il tono della pista gia' dice
        // giorno e notte, ma lo dice a scatti d'ora: la tacca dice **dove**
        // cade il confine, che finora si poteva solo intuire fra una casella e
        // la successiva. Due trattini e non due pallini, cosi' non si
        // confondono con l'ora corrente che vive sulla stessa riga.
        val span = hours.size * 60f
        val origin = hours.first().time
        listOfNotNull(sunrise, sunset).forEach { moment ->
            val minutes = java.time.Duration.between(origin, moment).toMinutes().toFloat()
            if (minutes < 0f || minutes > span) return@forEach
            val x = minutes / span * size.width
            drawRect(
                color = colors.label,
                topLeft = Offset(x - size.height * 0.017f, noteY - size.height * 0.05f),
                size = Size(size.height * 0.034f, size.height * 0.10f),
            )
        }

        val thumbX = (position + 0.5f) * slot
        val thumbWidth = trackHeight * 0.60f
        drawRoundRect(
            color = colors.pillBackground,
            topLeft = Offset(thumbX - thumbWidth / 2f, top - trackHeight * 0.52f),
            size = Size(thumbWidth, trackHeight * 2.04f),
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
