package io.github.noximiliencoxen.caelum.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.common.MeteoCard
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoAccents
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

/**
 * La settimana in una scheda sola.
 *
 * Le due curve - massime e minime - attraversano tutte le colonne, quindi non
 * possono vivere dentro le colonne: sono una tela unica larga quanto la scheda,
 * con le intestazioni sopra e le note sotto. Le zone toccabili stanno in una
 * riga trasparente distesa su tutto, cosi' un giorno si apre toccandolo
 * ovunque - l'icona, il numero, la percentuale - invece che centrando la sola
 * casella giusta.
 *
 * **La seconda fila di illustrazioni e' sparita.** Ce n'erano due per colonna,
 * una diurna e una notturna, ma disegnavano **lo stesso** `weatherCode` - quello
 * giornaliero - con il sole in una e la luna nell'altra. La riga notturna
 * quindi non portava alcun dato notturno: occupava un quinto della scheda per
 * ridisegnare cio' che era gia' scritto sopra. Al suo posto c'e' il giorno
 * selezionato, evidenziato, che prima non si vedeva da nessuna parte.
 */
@Composable
fun DailyForecastCard(
    days: List<DayForecast>,
    unit: TempUnit,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selected: Int = -1,
) {
    if (days.isEmpty()) return
    val shown = days.take(7)
    val accents = LocalMeteoAccents.current

    MeteoCard(modifier = modifier) {
        Text(
            text = "LA SETTIMANA",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    shown.forEachIndexed { index, day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val active = index == selected
                            Text(
                                text = day.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = day.date.format(DATE_FORMAT),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                            WeatherGlyph(
                                weatherCode = day.weatherCode,
                                isDay = true,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(32.dp),
                            )
                        }
                    }
                }

                TemperatureBand(
                    days = shown,
                    unit = unit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                ) {
                    shown.forEachIndexed { index, day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Lo zero per cento non e' una notizia: sotto un
                            // cielo sereno una colonna di "0%" e' solo rumore.
                            // Lo spazio pero' va tenuto lo stesso, se no le
                            // colonne asciutte si alzano rispetto alle bagnate.
                            Text(
                                text = day.precipProbability
                                    ?.takeIf { it > 0 }
                                    ?.let { "$it%" }
                                    .orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.rain,
                                maxLines = 1,
                            )
                            Spacer(
                                Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth(0.5f)
                                    .height(2.dp)
                                    .background(
                                        if (index == selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            androidx.compose.ui.graphics.Color.Transparent
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            // Zone toccabili, una per giorno. Con il nome dichiarato: una
            // colonna trasparente senza etichetta e' invisibile a chi ascolta.
            Row(modifier = Modifier.matchParentSize()) {
                shown.forEachIndexed { index, day ->
                    val spoken = buildString {
                        append(day.label)
                        append(", ")
                        append(Wmo.condition(day.weatherCode).lowercase())
                        append(", da ")
                        append(day.tempMin.asPlainDegrees(unit))
                        append(" a ")
                        append(day.tempMax.asPlainDegrees(unit))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 48.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Apri il dettaglio",
                                onClick = { onSelectDay(index) },
                            )
                            .clearAndSetSemantics { contentDescription = spoken },
                    )
                }
            }
        }
    }
}

/**
 * Massime e minime come due curve, coi valori scritti sopra e sotto.
 *
 * Fra le due c'e' l'area, sfumata secondo i gradi: e' la stessa scala di colore
 * del grafico orario, quindi una settimana calda e una fredda non si somigliano
 * anche quando la forma delle curve e' identica.
 */
@Composable
private fun TemperatureBand(
    days: List<DayForecast>,
    unit: TempUnit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        val maxima = days.map { it.tempMax }
        val minima = days.map { it.tempMin }
        val known = (maxima + minima).filterNotNull()
        if (known.size < 2) return@Canvas

        var lo = known.min().toFloat()
        var hi = known.max().toFloat()
        if (hi - lo < 1f) { hi += 1f; lo -= 1f }

        // I numeri scritti sopra la curva alta e sotto la bassa hanno bisogno
        // del loro spazio: senza, la massima di oggi uscirebbe dalla tela.
        val padTop = 15.dp.toPx()
        val padBottom = 15.dp.toPx()
        val plotTop = padTop
        val plotH = size.height - padTop - padBottom
        if (plotH <= 0f) return@Canvas

        val slot = size.width / days.size
        fun xOf(index: Int): Float = slot * (index + 0.5f)
        fun yOf(celsius: Double): Float =
            plotTop + plotH * (1f - (celsius.toFloat() - lo) / (hi - lo))

        val highPts = maxima.mapIndexed { i, v -> v?.let { Offset(xOf(i), yOf(it)) } }
        val lowPts = minima.mapIndexed { i, v -> v?.let { Offset(xOf(i), yOf(it)) } }

        drawPath(
            path = nullSafeRibbonPath(highPts, lowPts),
            brush = Brush.verticalGradient(
                colors = temperatureRamp(lo, hi, alpha = 0.30f),
                startY = plotTop,
                endY = plotTop + plotH,
            ),
        )

        val warm = Brush.verticalGradient(
            colors = temperatureRamp(lo, hi),
            startY = plotTop,
            endY = plotTop + plotH,
        )
        drawPath(
            path = buildLinePath(highPts),
            brush = warm,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = buildLinePath(lowPts),
            brush = warm,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        val valueStyle = TextStyle(fontSize = 11.sp, color = onSurface)
        fun label(value: Double?, at: Offset?, above: Boolean) {
            if (value == null || at == null) return
            val layout = measurer.measure("${unit.from(value).roundToInt()}°", valueStyle)
            val maxX = size.width - layout.size.width
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    // Con sette colonne su uno schermo stretto l'etichetta puo'
                    // essere piu' larga della tela: li' `coerceIn` avrebbe il
                    // minimo sopra il massimo, e solleva invece di arrotondare.
                    x = if (maxX <= 0f) 0f else (at.x - layout.size.width / 2f).coerceIn(0f, maxX),
                    y = if (above) {
                        at.y - layout.size.height - 2.dp.toPx()
                    } else {
                        at.y + 2.dp.toPx()
                    },
                ),
            )
        }

        maxima.forEachIndexed { i, v -> label(v, highPts[i], above = true) }
        minima.forEachIndexed { i, v -> label(v, lowPts[i], above = false) }
    }
}
