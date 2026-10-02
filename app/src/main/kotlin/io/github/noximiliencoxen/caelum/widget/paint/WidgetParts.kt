package io.github.noximiliencoxen.caelum.widget.paint

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.sala.glifoDi
import io.github.noximiliencoxen.caelum.ui.sala.nuvolositaStimata
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("H")

/** Colore di un'ora: asciutto resta neutro, il resto si dichiara. */
private fun tintOf(hour: HourForecast, ink: WidgetInk): Color {
    val base = when (Wmo.family(hour.weatherCode)) {
        Wmo.Family.ASCIUTTO -> ink.secondary.copy(alpha = 0.45f)
        Wmo.Family.NUVOLOSO -> ink.secondary.copy(alpha = 0.62f)
        Wmo.Family.NEBBIA -> ink.secondary.copy(alpha = 0.40f)
        Wmo.Family.PIOGGIA -> Color(0xFF2C7BF2)
        Wmo.Family.NEVE -> Color(0xFF8FC7F5)
        Wmo.Family.TEMPORALE -> Color(0xFF5B4BC4)
    }
    // La notte smorza, cosi' la striscia racconta anche il passare del giorno.
    return if (hour.isDay) base else base.copy(alpha = base.alpha * 0.55f)
}

/**
 * La striscia delle ore: una barra continua colorata dal tempo che fara',
 * con la temperatura ai due capi e qualche ora scritta sotto.
 *
 * I due numeri stanno **dentro** la barra e non sopra: sono l'adesso e il
 * fra-un-po', e messi fuori diventavano due numeri in cerca di un'etichetta.
 */
internal fun DrawScope.hourStrip(
    box: Rect,
    hours: List<HourForecast>,
    type: WidgetType,
    ink: WidgetInk,
) {
    if (hours.isEmpty()) return

    val barHeight = box.height * 0.56f
    val bar = Rect(box.left, box.top, box.right, box.top + barHeight)
    val radius = CornerRadius(barHeight / 2f)

    val shape = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = bar.left, top = bar.top, right = bar.right, bottom = bar.bottom,
                cornerRadius = radius,
            ),
        )
    }
    clipPath(shape) {
        val step = bar.width / hours.size
        hours.forEachIndexed { i, hour ->
            drawRect(
                color = tintOf(hour, ink),
                topLeft = Offset(bar.left + step * i, bar.top),
                size = Size(step + 1f, bar.height),
            )
        }
    }

    // I due estremi, scritti sopra la barra nel colore del fondo: la barra e'
    // piena, e un numero chiaro sopra un colore chiaro non si leggerebbe.
    val numbers = type.brush(barHeight * 0.60f, weight = 700, width = 78)
    val inset = barHeight * 0.42f
    hours.firstOrNull()?.temperature?.let {
        text(
            "${it.roundToInt()}°",
            bar.left + inset,
            bar.top + (barHeight - lineHeight(numbers)) / 2f,
            numbers,
            Color(ink.background),
        )
    }
    hours.lastOrNull()?.temperature?.let {
        val label = "${it.roundToInt()}°"
        text(
            label,
            bar.right - inset - type.widthOf(label, numbers),
            bar.top + (barHeight - lineHeight(numbers)) / 2f,
            numbers,
            Color(ink.background),
        )
    }

    // Le ore sotto, una ogni tre: scriverle tutte le rende illeggibili.
    val ticks = type.brush(box.height * 0.20f, weight = 560, width = 78, letterSpacingEm = 0.06f)
    val y = bar.bottom + box.height * 0.10f
    val step = bar.width / hours.size
    hours.forEachIndexed { i, hour ->
        if (i % 3 != 0) return@forEachIndexed
        textCentered(hour.time.format(ORA), bar.left + step * (i + 0.5f), y, ticks, ink.secondary)
    }
}

/**
 * La striscia dei giorni: sigla, glifo, massima e minima.
 *
 * Il glifo e' una sagoma piena e non una sfera illuminata: a questa misura il
 * volume diventa poltiglia, e cio' che serve e' riconoscere sole da pioggia in
 * un colpo d'occhio.
 */
internal fun DrawScope.dayStrip(
    box: Rect,
    days: List<DayForecast>,
    type: WidgetType,
    ink: WidgetInk,
) {
    if (days.isEmpty()) return
    val shown = days.take(7)
    val step = box.width / shown.size

    val label = fittingBrush(
        shown.map { it.label }, step * 0.92f, box.height * 0.155f, type,
        weight = 600, width = 78, letterSpacingEm = 0.08f,
    )
    val high = type.brush(box.height * 0.215f, weight = 700, width = 82)
    val low = type.brush(box.height * 0.195f, weight = 500, width = 82)

    shown.forEachIndexed { i, day ->
        val cx = box.left + step * (i + 0.5f)
        textCentered(day.label, cx, box.top, label, ink.secondary)

        val glyphTop = box.top + box.height * 0.24f
        val glyphSize = minOf(step * 0.62f, box.height * 0.30f)
        dayGlyph(Rect(cx - glyphSize / 2f, glyphTop, cx + glyphSize / 2f, glyphTop + glyphSize), day)

        val numbersTop = glyphTop + glyphSize + box.height * 0.06f
        textCentered(
            day.tempMax?.roundToInt()?.toString() ?: "--",
            cx, numbersTop, high, ink.primary,
        )
        textCentered(
            day.tempMin?.roundToInt()?.toString() ?: "--",
            cx, numbersTop + lineHeight(high) * 0.92f, low, ink.secondary,
        )
    }
}

/**
 * Il segno del tempo in miniatura: la stessa figuretta a colori dell'app, dallo
 * stesso codice e dalla stessa nuvolosita' stimata, cosi' widget e app dicono
 * lo stesso giorno con lo stesso segno.
 */
internal fun DrawScope.dayGlyph(box: Rect, day: DayForecast) {
    glifoNelRiquadro(box, glifoDi(day.weatherCode, day.nuvolositaStimata()))
}

/**
 * Il credito delle fonti, nel margine in basso: CONTESTO §47.
 *
 * La licenza di Open-Meteo chiede un collegamento "next to any location
 * Open-Meteo data are displayed", e i widget i dati li mostrano. **Sta nel
 * margine di 16 punti e non nel riquadro**: cosi' non sposta niente
 * dell'impaginazione dei tre tagli, che e' stretta e misurata da
 * `WidgetOverflowTest`. Centrato e non in un angolo, perche' gli angoli del
 * widget sono arrotondati e lo taglierebbero. Il tocco sul widget apre l'app,
 * dove il collegamento vero sta sotto la barra delle ore.
 */
internal fun DrawScope.creditoFonti(forme: List<String>, type: WidgetType, ink: WidgetInk) {
    val margine = 16.dp.toPx()
    val pennello = type.brush(7.5.dp.toPx(), weight = 600, width = 82)
    val alto = size.height - margine + (margine - lineHeight(pennello)) / 2f
    // Dalla forma piu' lunga alla piu' corta: si prende la prima che ci sta.
    // L'ultima e' la piu' corta che dice ancora chi fornisce i dati.
    val scritta = forme.firstOrNull { type.widthOf(it, pennello) <= size.width - 2f * margine } ?: forme.last()
    textCentered(scritta, size.width / 2f, alto, pennello, ink.secondary, alpha = 0.75f)
}

/** Il credito dei widget che mostrano solo dati di Open-Meteo. */
internal val CREDITO_OPEN_METEO = listOf("Dati Open-Meteo.com", "Open-Meteo.com")

/** Il credito del widget dell'aria: Open-Meteo e i dati CAMS di Copernicus. */
internal val CREDITO_ARIA = listOf("Open-Meteo.com · CAMS Copernicus", "Open-Meteo · CAMS")
