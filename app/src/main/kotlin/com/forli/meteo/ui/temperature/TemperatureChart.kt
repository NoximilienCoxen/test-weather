package com.forli.meteo.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forli.meteo.data.HourForecast
import com.forli.meteo.prefs.TempUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * L'andamento della temperatura in una giornata con crosshair interattivo.
 *
 * L'area usa un gradiente verticale che segue la scala dei gradi: la stessa
 * curva comunica quanto caldo fa, non solo come cambia.
 *
 * Trascinare il dito sul grafico mostra una linea verticale tratteggiata, un
 * cerchio sul punto e l'etichetta con temperatura e ora.
 *
 * [normTemp] e' la media storica mensile (la "Norma"): linea tratteggiata
 * orizzontale. Nulla = omessa silenziosamente.
 */
@Composable
fun HourlyTemperatureChart(
    hours: List<HourForecast>,
    unit: TempUnit,
    /** Vero per PERCEPITI: la percepita a colori, l'effettiva in grigio dietro. */
    feelsLike: Boolean,
    /** Media storica mensile in gradi Celsius, o null se non disponibile. */
    normTemp: Double? = null,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)

    // Posizione del tocco come frazione [0,1] della larghezza del plot.
    // -1f = nessun tocco attivo.
    var touchFraction by remember { mutableFloatStateOf(-1f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(hours, feelsLike) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touchFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = { touchFraction = -1f },
                    onDragCancel = { touchFraction = -1f },
                )
            },
    ) {
        if (hours.size < 2) return@Canvas

        val main = hours.map { if (feelsLike) it.apparent else it.temperature }
        val ghost = if (feelsLike) hours.map { it.temperature } else emptyList()
        val known = (main + ghost).filterNotNull()
        if (known.size < 2) return@Canvas

        // ── Zone di layout ───────────────────────────────────────────────────
        val markBand   = 20.dp.toPx()
        val hourBand   = 16.dp.toPx()
        val gutter     = 34.dp.toPx()
        val plotTop    = markBand + 6.dp.toPx()
        val plotBottom = size.height - hourBand
        val plotLeft   = 0f
        val plotRight  = size.width - gutter
        val plotW      = plotRight - plotLeft
        val plotH      = plotBottom - plotTop
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        // ── Scala verticale arrotondata ──────────────────────────────────────
        val step  = niceStep(((known.max() - known.min()) / 4.0).toFloat())
        val gridLo = floor(known.min().toFloat() / step) * step
        val gridHi = ceil(known.max().toFloat() / step) * step
        val span  = (gridHi - gridLo).takeIf { it > 0.01f } ?: 1f

        fun yOf(celsius: Double): Float =
            plotTop + plotH * (1f - (celsius.toFloat() - gridLo) / span)
        fun xOf(index: Int): Float =
            plotLeft + plotW * index / (hours.size - 1).coerceAtLeast(1).toFloat()

        val labelStyle = TextStyle(fontSize = 10.sp, color = MetricLabel)

        // ── Griglia e scale asse destro ──────────────────────────────────────
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        var tick = gridLo
        while (tick <= gridHi + 0.01f) {
            val y = yOf(tick.toDouble())
            drawLine(
                color = CardBorder,
                start = Offset(plotLeft, y),
                end   = Offset(plotRight, y),
                strokeWidth = 1f,
                pathEffect  = dashes,
            )
            val layout = measurer.measure(
                "${unit.from(tick.toDouble()).roundToInt()}\u00B0", labelStyle,
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = size.width - layout.size.width,
                    y = y - layout.size.height / 2f,
                ),
            )
            tick += step
        }

        // ── Linea "Norma" ────────────────────────────────────────────────────
        if (normTemp != null) {
            val normCelsius = normTemp.toFloat()
            if (normCelsius in gridLo..gridHi) {
                val normY = yOf(normTemp)
                val normDashes = PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                )
                drawLine(
                    color  = NormLine,
                    start  = Offset(plotLeft, normY),
                    end    = Offset(plotRight, normY),
                    strokeWidth = 1.5f,
                    pathEffect  = normDashes,
                )
                val normLabel = measurer.measure(
                    "Norma",
                    TextStyle(fontSize = 9.sp, color = NormLine),
                )
                drawText(
                    textLayoutResult = normLabel,
                    topLeft = Offset(
                        x = plotLeft,
                        y = normY - normLabel.size.height - 2.dp.toPx(),
                    ),
                )
            }
        }

        // ── Iconcine sole e luna, una ogni due ore ───────────────────────────
        val markY      = markBand / 2f
        val markRadius = markBand * 0.40f
        hours.forEachIndexed { index, hour ->
            if (index % 2 != 0) return@forEachIndexed
            skyMark(
                center = Offset(xOf(index), markY),
                radius = markRadius,
                isDay  = hour.isDay,
            )
        }

        // ── Curva di riferimento (effettiva sotto la percepita) ──────────────
        if (ghost.isNotEmpty()) {
            val ghostPts = ghost.mapIndexed { i, v -> v?.let { Offset(xOf(i), yOf(it)) } }
            drawPath(
                path  = buildLinePath(ghostPts),
                color = GhostLine,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // ── Area e curva principale ──────────────────────────────────────────
        val pts = main.mapIndexed { i, v -> v?.let { Offset(xOf(i), yOf(it)) } }
        val fill = Brush.verticalGradient(
            colors = temperatureRamp(gridLo, gridHi, alpha = 0.82f),
            startY = plotTop,
            endY   = plotBottom,
        )
        val stroke = Brush.verticalGradient(
            colors = temperatureRamp(gridLo, gridHi),
            startY = plotTop,
            endY   = plotBottom,
        )
        drawPath(path = buildAreaPath(pts, plotBottom), brush = fill)
        drawPath(
            path  = buildLinePath(pts),
            brush = stroke,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // ── Etichette ore in fondo ───────────────────────────────────────────
        listOf(6, 12, 18).forEach { wanted ->
            val index = hours.indexOfFirst { it.time.hour == wanted }
            if (index < 0) return@forEach
            val layout = measurer.measure("%02d:00".format(wanted), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = (xOf(index) - layout.size.width / 2f)
                        .coerceIn(0f, plotRight - layout.size.width),
                    y = size.height - layout.size.height,
                ),
            )
        }

        // ── Crosshair interattivo ────────────────────────────────────────────
        if (touchFraction >= 0f) {
            // Indice nell'array ore proporzionale alla posizione del dito
            val rawIdx = touchFraction * (hours.size - 1)
            val idx    = rawIdx.roundToInt().coerceIn(0, hours.lastIndex)
            val tempValue = main[idx] ?: return@Canvas
            val cx = xOf(idx)
            val cy = yOf(tempValue)

            // Linea verticale tratteggiata
            drawLine(
                color  = Color.White.copy(alpha = 0.4f),
                start  = Offset(cx, plotTop),
                end    = Offset(cx, plotBottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect  = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                ),
            )

            // Cerchio sul punto della curva principale
            val dotColor = temperatureTint(tempValue.toFloat())
            drawCircle(color = dotColor, radius = 5.dp.toPx(), center = Offset(cx, cy))
            drawCircle(
                color  = Color(0xFF1D2026),
                radius = 3.dp.toPx(),
                center = Offset(cx, cy),
            )

            // Etichetta: ora + temperatura
            val hour = hours[idx]
            val tempStr = "${unit.from(tempValue).roundToInt()}\u00B0"
            val timeStr = "%02d:00".format(hour.time.hour)
            val callout = measurer.measure(
                "$timeStr  $tempStr",
                TextStyle(fontSize = 10.sp, color = MetricValue),
            )
            // Posizione sopra il punto, entro i bordi del grafico
            val lx = (cx - callout.size.width / 2f)
                .coerceIn(plotLeft, plotRight - callout.size.width)
            val ly = (cy - callout.size.height - 6.dp.toPx())
                .coerceIn(plotTop, plotBottom - callout.size.height.toFloat())
            // Sfondo pill per la leggibilita'
            val pad = 4.dp.toPx()
            drawRoundRect(
                color      = Color(0xFF2A2A35),
                topLeft    = Offset(lx - pad, ly - pad),
                size       = androidx.compose.ui.geometry.Size(
                    (callout.size.width + pad * 2),
                    (callout.size.height + pad * 2),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            )
            drawText(callout, topLeft = Offset(lx, ly))
        }
    }
}

/**
 * Passo della griglia arrotondato a valori leggibili (1, 2, 5 e multipli di 10).
 */
private fun niceStep(raw: Float): Float {
    if (raw <= 0f || raw.isNaN()) return 1f
    val magnitude  = 10f.pow(floor(kotlin.math.log10(raw.toDouble())).toFloat())
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1f -> 1f
        normalized <= 2f -> 2f
        normalized <= 5f -> 5f
        else             -> 10f
    }
    return (nice * magnitude).takeIf { abs(it) > 0.001f } ?: 1f
}
