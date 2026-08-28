package com.forli.meteo.widget.paint

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import com.forli.meteo.data.DayForecast
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Wmo
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

    val label = type.brush(box.height * 0.155f, weight = 600, width = 78, letterSpacingEm = 0.08f)
    val high = type.brush(box.height * 0.215f, weight = 700, width = 82)
    val low = type.brush(box.height * 0.195f, weight = 500, width = 82)

    shown.forEachIndexed { i, day ->
        val cx = box.left + step * (i + 0.5f)
        textCentered(day.label, cx, box.top, label, ink.secondary)

        val glyphTop = box.top + box.height * 0.24f
        val glyphSize = minOf(step * 0.62f, box.height * 0.30f)
        dayGlyph(
            Rect(cx - glyphSize / 2f, glyphTop, cx + glyphSize / 2f, glyphTop + glyphSize),
            Wmo.family(day.weatherCode),
            ink,
        )

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

/** Il segno del tempo in miniatura: pieno, leggibile, senza pretese di volume. */
private fun DrawScope.dayGlyph(box: Rect, family: Wmo.Family, ink: WidgetInk) {
    val r = minOf(box.width, box.height) / 2f
    val c = box.center
    when (family) {
        Wmo.Family.ASCIUTTO -> drawCircle(ink.sunCore, r * 0.72f, c)

        Wmo.Family.NUVOLOSO -> {
            drawCircle(ink.sunCore, r * 0.52f, Offset(c.x - r * 0.34f, c.y - r * 0.34f))
            puff(c, r, ink.cloudCore)
        }

        Wmo.Family.NEBBIA -> puff(c, r, ink.cloudCore.copy(alpha = 0.7f))

        Wmo.Family.PIOGGIA -> {
            puff(Offset(c.x, c.y - r * 0.16f), r, ink.rainCloudCore)
            drops(c, r, ink.rain)
        }

        Wmo.Family.NEVE -> {
            puff(Offset(c.x, c.y - r * 0.16f), r, ink.rainCloudCore)
            drops(c, r, ink.snow)
        }

        Wmo.Family.TEMPORALE -> {
            puff(Offset(c.x, c.y - r * 0.16f), r, ink.rainCloudShade)
            drawCircle(ink.bolt, r * 0.20f, Offset(c.x, c.y + r * 0.62f))
        }
    }
}

private fun DrawScope.puff(c: Offset, r: Float, colour: Color) {
    drawCircle(colour, r * 0.46f, Offset(c.x - r * 0.42f, c.y + r * 0.16f))
    drawCircle(colour, r * 0.40f, Offset(c.x + r * 0.44f, c.y + r * 0.18f))
    drawCircle(colour, r * 0.58f, Offset(c.x, c.y - r * 0.06f))
    drawRoundRect(
        color = colour,
        topLeft = Offset(c.x - r * 0.86f, c.y + r * 0.02f),
        size = Size(r * 1.72f, r * 0.52f),
        cornerRadius = CornerRadius(r * 0.26f),
    )
}

private fun DrawScope.drops(c: Offset, r: Float, colour: Color) {
    listOf(-0.42f, 0.06f, 0.54f).forEach { dx ->
        drawCircle(colour, r * 0.13f, Offset(c.x + dx * r, c.y + r * 0.78f))
    }
}
