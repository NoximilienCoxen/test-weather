package io.github.noximiliencoxen.caelum.ui.temperature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoAccents
import io.github.noximiliencoxen.caelum.ui.theme.readableOn
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Un grafico solo per tutta l'app.
 *
 * Prima ce n'erano due, quasi identici e divergenti nei dettagli: quello del
 * foglio di dettaglio e quello del dettaglio di un giorno. Entrambi
 * disegnavano griglia, spline, etichette e crosshair; uno arrotondava la scala
 * ai valori leggibili e l'altro no, uno metteva una pillola sotto l'etichetta
 * del crosshair e l'altro la lasciava nuda sopra l'area colorata.
 *
 * Tre cose sono cambiate rispetto a entrambi.
 *
 * **Le etichette hanno un fondo.** Erano scritte direttamente sulla tela: dove
 * cadevano sopra l'area riempita - che sotto la scala dei gradi e' coperta
 * all'ottanta per cento - diventavano illeggibili, e la scala dell'asse Y ci
 * cade sempre. Ora ognuna poggia su una pillola e il suo colore esce da
 * `readableOn` di quel fondo.
 *
 * **La scala non inventa valori.** Il vecchio `GraphSection`, con una serie
 * piatta, allargava l'intervallo a `mid ± 1.5`: su una probabilita' di pioggia
 * a zero l'asse dichiarava "-1" e "2", cioe' due percentuali che non esistono.
 * Qui l'intervallo si allarga entro i limiti dichiarati dalla grandezza.
 *
 * **Il gesto e' verticale-trasparente.** `detectDragGestures` consumava anche
 * lo scorrimento in su e in giu': dentro una colonna che scorre, trascinare
 * sul grafico bloccava la pagina. Qui si consuma solo la componente
 * orizzontale, e quella verticale prosegue verso chi scorre.
 */

/** L'intervallo che una grandezza puo' davvero assumere. */
data class ChartBounds(
    val min: Float? = null,
    val max: Float? = null,
) {
    companion object {
        /** Percentuali: fra zero e cento, senza sconti. */
        val Percent = ChartBounds(min = 0f, max = 100f)
        /** Quantita' che non possono essere negative: millimetri, velocita', indici. */
        val NonNegative = ChartBounds(min = 0f)
        /** Nessun limite: la temperatura, che sotto lo zero ci va. */
        val Free = ChartBounds()
    }
}

/** Una linea orizzontale di riferimento, tratteggiata, con la sua etichetta. */
data class ChartReference(val value: Float, val label: String)

/**
 * Il grafico.
 *
 * I valori arrivano **nell'unita' nativa** - i gradi in Celsius, sempre - e la
 * conversione la fa [formatValue] al momento di scriverli. E' la stessa regola
 * del resto dell'app, e qui e' obbligatoria: la scala di colore dei gradi e'
 * tarata in Celsius, e nutrirla di Fahrenheit la farebbe mentire di trenta
 * gradi.
 */
@Composable
fun MeteoChart(
    values: List<Float?>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    /** La curva di riferimento dietro quella principale, se ce n'e' una. */
    ghost: List<Float?> = emptyList(),
    /** Colonne sotto la curva: i millimetri caduti, sotto la probabilita'. */
    bars: List<Float?> = emptyList(),
    /** Sole o luna in cima, una ogni due punti: dice a colpo d'occhio dov'e' la notte. */
    daylight: List<Boolean> = emptyList(),
    reference: ChartReference? = null,
    bounds: ChartBounds = ChartBounds.Free,
    /** Vero per la temperatura: area e curva prendono la scala di colore dei gradi. */
    useTemperatureRamp: Boolean = false,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
    /** Cosa dire a chi ascolta invece di guardare. */
    description: String = "Andamento",
    /** Il fondo su cui poggia il grafico: serve a ritagliare la falce di luna. */
    surface: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val measurer = rememberTextMeasurer()
    val accents = LocalMeteoAccents.current
    val labelBackground = accents.chartLabelBackground
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    // Il contrasto costa qualche elevamento a potenza per colore: si calcola
    // al cambio di tema, non a ogni fotogramma di trascinamento.
    // I tre colori delle scritte dentro la tela, gia' resi leggibili sul fondo
    // della loro pillola. **Non finiscono nello stile passato al misuratore**:
    // la cache di `TextMeasurer` ignora colore e pennello (trappola #3), quindi
    // due scritte uguali di colore diverso si scambierebbero il tono. Si misura
    // con uno stile solo e il colore si passa a `drawText`.
    val labelColor = remember(labelBackground, onVariant) { onVariant.readableOn(labelBackground) }
    val strongLabelColor = remember(labelBackground) { Color.White.readableOn(labelBackground) }
    val normColor = remember(labelBackground, accents.norm) {
        accents.norm.readableOn(labelBackground)
    }
    val gridColor = accents.grid

    // Posizione del tocco come frazione [0,1] della larghezza del plot.
    // -1f = nessun tocco attivo.
    var touchFraction by remember { mutableFloatStateOf(-1f) }
    val liveValues by rememberUpdatedState(values)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = describe(description, values, formatValue) }
            .pointerInput(values.size) {
                // Non `detectDragGestures`: quello consuma qualunque direzione,
                // e dentro una colonna che scorre significa che trascinare sul
                // grafico blocca la pagina. Qui si consuma il solo movimento
                // orizzontale; quello verticale prosegue verso chi scorre.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var horizontal = false
                    var totalX = 0f
                    var totalY = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        totalX += abs(delta.x)
                        totalY += abs(delta.y)
                        if (!horizontal && totalX > totalY && totalX > 8.dp.toPx()) {
                            horizontal = true
                        }
                        if (horizontal) {
                            touchFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    touchFraction = -1f
                }
            },
    ) {
        val series = liveValues
        val known = (series + ghost + bars).filterNotNull()
        if (known.size < 2) return@Canvas

        // ── Zone di layout ───────────────────────────────────────────────────
        val markBand = if (daylight.isEmpty()) 0f else 20.dp.toPx()
        val hourBand = 18.dp.toPx()
        val gutter = 40.dp.toPx()
        val plotTop = markBand + 6.dp.toPx()
        val plotBottom = size.height - hourBand
        val plotLeft = 0f
        val plotRight = size.width - gutter
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        // ── Scala verticale, arrotondata e senza valori inventati ────────────
        var rawLo = known.min()
        var rawHi = known.max()
        if (bars.isNotEmpty()) rawLo = minOf(rawLo, 0f)
        if (rawHi - rawLo < 0.001f) {
            // Serie piatta. Se la grandezza ha un tetto dichiarato - una
            // percentuale, per dire - si mostra **tutta la scala**: una
            // giornata di zero per cento si legge come "zero su cento", che e'
            // l'informazione, invece che come una riga schiacciata fra due
            // tacche inventate a un punto di distanza.
            val max = bounds.max
            if (max != null) {
                rawLo = bounds.min ?: 0f
                rawHi = max
            } else {
                // Senza tetto si allarga, ma dentro i limiti che ci sono: e'
                // qui che la versione precedente dichiarava valori negativi.
                val pad = if (abs(rawHi) < 1f) 1f else abs(rawHi) * 0.1f
                rawLo -= pad
                rawHi += pad
            }
        }
        bounds.min?.let { rawLo = maxOf(rawLo, it) }
        bounds.max?.let { rawHi = minOf(rawHi, it) }
        if (rawHi - rawLo < 0.001f) rawHi = rawLo + 1f

        // Tutte le grandezze di questa app si scrivono con numeri interi, quindi
        // un passo sotto l'uno produce **due tacche con la stessa etichetta**:
        // su una giornata asciutta la scala della probabilita' andava a passo
        // di mezzo punto e l'asse mostrava "1%" due volte, una sopra l'altra.
        val step = niceStep((rawHi - rawLo) / 4f).coerceAtLeast(1f)
        var gridLo = floor(rawLo / step) * step
        var gridHi = ceil(rawHi / step) * step
        bounds.min?.let { gridLo = maxOf(gridLo, it) }
        bounds.max?.let { gridHi = minOf(gridHi, it) }
        // I due limiti si stringono da lati opposti e potrebbero incrociarsi.
        // Piu' avanti si usa `coerceIn(gridLo, gridHi)`, che con il minimo
        // sopra il massimo non arrotonda: solleva.
        if (gridHi - gridLo < 0.001f) gridHi = gridLo + 1f
        val span = gridHi - gridLo

        fun yOf(value: Float): Float = plotTop + plotH * (1f - (value - gridLo) / span)
        fun xOf(index: Int): Float =
            plotLeft + plotW * index / (series.size - 1).coerceAtLeast(1).toFloat()

        // Senza colore, apposta: vedi la nota sui tre colori qui sopra.
        val labelStyle = TextStyle(fontSize = 10.sp)
        val calloutStyle = TextStyle(fontSize = 11.sp)

        // ── Griglia e scala a destra ─────────────────────────────────────────
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        var tick = gridLo
        var guard = 0
        while (tick <= gridHi + 0.001f && guard < 24) {
            guard++
            val y = yOf(tick)
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1f,
                pathEffect = dashes,
            )
            val layout = measurer.measure(formatValue(tick), labelStyle)
            drawLabel(
                layout = layout,
                topLeft = Offset(size.width - layout.size.width, y - layout.size.height / 2f),
                background = labelBackground,
                color = labelColor,
            )
            tick += step
        }

        // ── Linea di riferimento ─────────────────────────────────────────────
        if (reference != null && reference.value in gridLo..gridHi) {
            val y = yOf(reference.value)
            drawLine(
                color = accents.norm,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )
            val layout = measurer.measure(reference.label, labelStyle)
            drawLabel(
                layout = layout,
                topLeft = Offset(plotLeft, y - layout.size.height - 3.dp.toPx()),
                background = labelBackground,
                color = normColor,
            )
        }

        // ── Sole e luna in cima ──────────────────────────────────────────────
        if (daylight.isNotEmpty()) {
            val markY = markBand / 2f
            val markRadius = markBand * 0.40f
            daylight.forEachIndexed { index, isDay ->
                if (index % 2 != 0 || index >= series.size) return@forEachIndexed
                skyMark(
                    center = Offset(xOf(index), markY),
                    radius = markRadius,
                    isDay = isDay,
                    // Il fondo vero, dichiarato da chi chiama: la falce e' un
                    // disco meno un disco, e il secondo va del colore di sotto.
                    behind = surface,
                    sun = accents.sun,
                    moon = MoonPale,
                )
            }
        }

        // ── Colonne ──────────────────────────────────────────────────────────
        if (bars.isNotEmpty()) {
            val slot = plotW / bars.size.coerceAtLeast(1)
            val barWidth = (slot * 0.5f).coerceAtLeast(1.5.dp.toPx())
            val baseline = yOf(gridLo.coerceAtLeast(0f))
            bars.forEachIndexed { index, value ->
                val v = value ?: return@forEachIndexed
                if (v <= 0f) return@forEachIndexed
                val top = yOf(v.coerceIn(gridLo, gridHi))
                drawRoundRect(
                    color = accent.copy(alpha = 0.45f),
                    topLeft = Offset(xOf(index) - barWidth / 2f, top),
                    size = Size(barWidth, (baseline - top).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        // ── Curva di riferimento, sotto quella principale ────────────────────
        if (ghost.isNotEmpty()) {
            val ghostPts = ghost.mapIndexed { i, v -> v?.let { Offset(xOf(i), yOf(it)) } }
            drawPath(
                path = buildLinePath(ghostPts),
                color = accents.ghost,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // ── Area e curva principale ──────────────────────────────────────────
        val pts = series.mapIndexed { i, v -> v?.let { Offset(xOf(i), yOf(it)) } }
        if (useTemperatureRamp) {
            drawPath(
                path = buildAreaPath(pts, plotBottom),
                brush = Brush.verticalGradient(
                    colors = temperatureRamp(gridLo, gridHi, alpha = 0.82f),
                    startY = plotTop,
                    endY = plotBottom,
                ),
            )
            drawPath(
                path = buildLinePath(pts),
                brush = Brush.verticalGradient(
                    colors = temperatureRamp(gridLo, gridHi),
                    startY = plotTop,
                    endY = plotBottom,
                ),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        } else {
            drawPath(buildAreaPath(pts, plotBottom), accent.copy(alpha = 0.16f))
            drawPath(
                path = buildLinePath(pts),
                color = accent,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // ── Etichette in fondo ───────────────────────────────────────────────
        val labelStep = when {
            xLabels.size <= 5 -> 1
            xLabels.size <= 10 -> 2
            xLabels.size <= 16 -> 4
            else -> xLabels.size / 5
        }.coerceAtLeast(1)
        xLabels.forEachIndexed { i, text ->
            if (i % labelStep != 0 || i >= series.size) return@forEachIndexed
            val layout = measurer.measure(text, labelStyle)
            drawLabel(
                layout = layout,
                topLeft = Offset(
                    // `clamp` e non `coerceIn`: con un grafico piu' stretto
                    // dell'etichetta il minimo supererebbe il massimo, e
                    // `coerceIn` in quel caso non arrotonda - solleva.
                    x = clamp(xOf(i) - layout.size.width / 2f, 0f, plotRight - layout.size.width),
                    y = size.height - layout.size.height.toFloat(),
                ),
                background = labelBackground,
                color = labelColor,
            )
        }

        // ── Crosshair ────────────────────────────────────────────────────────
        if (touchFraction >= 0f && series.isNotEmpty()) {
            val idx = (touchFraction * (series.size - 1)).roundToInt().coerceIn(0, series.lastIndex)
            val value = series[idx]
            val cx = xOf(idx)
            drawLine(
                color = accents.grid,
                start = Offset(cx, plotTop),
                end = Offset(cx, plotBottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            if (value != null) {
                val cy = yOf(value.coerceIn(gridLo, gridHi))
                val dot = if (useTemperatureRamp) temperatureTint(value) else accent
                drawCircle(dot, 5.dp.toPx(), Offset(cx, cy))
                drawCircle(labelBackground, 2.5.dp.toPx(), Offset(cx, cy))

                val text = buildString {
                    append(xLabels.getOrNull(idx)?.takeIf { it.isNotBlank() }?.plus("  ").orEmpty())
                    append(formatValue(value))
                }
                val layout = measurer.measure(text, calloutStyle)
                drawLabel(
                    layout = layout,
                    topLeft = Offset(
                        x = clamp(cx - layout.size.width / 2f, 0f, plotRight - layout.size.width),
                        y = clamp(
                            cy - layout.size.height - 8.dp.toPx(),
                            plotTop,
                            plotBottom - layout.size.height,
                        ),
                    ),
                    background = labelBackground,
                    color = strongLabelColor,
                    padding = 5.dp.toPx(),
                )
            }
        }
    }
}

/**
 * Un'etichetta con la sua pillola sotto.
 *
 * E' la differenza fra un'etichetta e un'etichetta leggibile: la scala dell'asse
 * Y cade sempre sopra l'area riempita del grafico, e li' un grigio su un
 * arancione all'ottanta per cento non si legge.
 */
private fun DrawScope.drawLabel(
    layout: TextLayoutResult,
    topLeft: Offset,
    background: Color,
    color: Color,
    padding: Float = 3f,
) {
    drawRoundRect(
        color = background.copy(alpha = 0.88f),
        topLeft = Offset(topLeft.x - padding, topLeft.y - padding),
        size = Size(layout.size.width + padding * 2, layout.size.height + padding * 2),
        cornerRadius = CornerRadius(padding + 2f),
    )
    // Il colore qui e non nello stile misurato: la cache del misuratore non lo
    // guarda, e due scritte uguali di colore diverso si scambierebbero il tono.
    drawText(textLayoutResult = layout, color = color, topLeft = topLeft)
}

/**
 * `coerceIn` solleva quando il minimo supera il massimo, e succede: su uno
 * schermo stretto un'etichetta puo' essere piu' larga del grafico. Qui si
 * preferisce il minimo e si tira avanti.
 */
private fun clamp(value: Float, min: Float, max: Float): Float =
    if (max <= min) min else value.coerceIn(min, max)

/** Passo della griglia arrotondato a valori leggibili (1, 2, 5 e multipli di 10). */
internal fun niceStep(raw: Float): Float {
    if (raw <= 0f || raw.isNaN() || raw.isInfinite()) return 1f
    val magnitude = 10f.pow(floor(log10(raw.toDouble())).toFloat())
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1f -> 1f
        normalized <= 2f -> 2f
        normalized <= 5f -> 5f
        else -> 10f
    }
    return (nice * magnitude).takeIf { abs(it) > 0.0001f } ?: 1f
}

/**
 * Cosa dice il grafico a chi non lo vede.
 *
 * Non l'elenco dei valori - sarebbero ventiquattro numeri di fila - ma la
 * forma: minimo, massimo, e da dove parte e dove arriva.
 */
private fun describe(
    title: String,
    values: List<Float?>,
    format: (Float) -> String,
): String {
    val known = values.filterNotNull()
    if (known.isEmpty()) return "$title: nessun dato"
    return "$title: da ${format(known.first())} a ${format(known.last())}, " +
        "minimo ${format(known.min())}, massimo ${format(known.max())}"
}
