package com.forli.meteo.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forli.meteo.data.DayForecast
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.theme.MeteoType
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
 */
@Composable
fun DailyForecastCard(
    days: List<DayForecast>,
    unit: TempUnit,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val shown = days.take(7)

    DetailCard(modifier = modifier) {
        Text(
            text = "PREVISIONI GIORNALIERE",
            style = MeteoType.caption,
            color = MetricLabel,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 10.dp),
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Intestazioni: sigla, data, illustrazione diurna ──────────
                Row(modifier = Modifier.fillMaxWidth()) {
                    shown.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = day.label,
                                style = MeteoType.caption,
                                color = MetricValue,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = day.date.format(DATE_FORMAT),
                                style = MeteoType.tabular,
                                color = MetricLabel,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                            WeatherGlyph(
                                weatherCode = day.weatherCode,
                                isDay = true,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(34.dp),
                            )
                        }
                    }
                }

                // ── Le due curve, su una tela sola ──────────────────────────
                TemperatureBand(
                    days = shown,
                    unit = unit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                )

                // ── Note: probabilita' e illustrazione notturna ─────────────
                Row(modifier = Modifier.fillMaxWidth()) {
                    shown.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Lo zero per cento non e' una notizia: sotto un
                            // cielo sereno una colonna di "0%" e' solo rumore.
                            Text(
                                text = day.precipProbability
                                    ?.takeIf { it > 0 }
                                    ?.let { "$it%" }
                                    .orEmpty(),
                                style = MeteoType.tabular,
                                color = RainAccent,
                                maxLines = 1,
                            )
                            WeatherGlyph(
                                weatherCode = day.weatherCode,
                                isDay = false,
                                modifier = Modifier
                                    .padding(top = 2.dp, bottom = 12.dp)
                                    .size(30.dp),
                            )
                        }
                    }
                }
            }

            // ── Zone toccabili, una per giorno ──────────────────────────────
            Row(modifier = Modifier.matchParentSize()) {
                shown.forEachIndexed { index, _ ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onSelectDay(index) },
                            ),
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
    val measurer = rememberTextMeasurer(cacheSize = 0)

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

        // L'area fra le due: usa nullSafeRibbonPath che preserva le X originali.
        // ribbonPath(filterNotNull, filterNotNull) comprimeva la lista e
        // disegnava i punti alla X sbagliata quando c'erano null intermedi.
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

        val valueStyle = TextStyle(fontSize = 11.sp, color = MetricValue)
        fun label(value: Double?, at: Offset?, above: Boolean) {
            if (value == null || at == null) return
            val layout = measurer.measure(
                "${unit.from(value).roundToInt()}\u00B0",
                valueStyle,
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = (at.x - layout.size.width / 2f)
                        .coerceIn(0f, size.width - layout.size.width),
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
