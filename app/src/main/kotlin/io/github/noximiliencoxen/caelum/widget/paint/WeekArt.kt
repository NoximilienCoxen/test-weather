package io.github.noximiliencoxen.caelum.widget.paint

import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import kotlin.math.roundToInt

/**
 * La settimana: un giorno per colonna, e per ogni giorno quanto oscilla.
 *
 * La striscia dei giorni del widget grande dice massima e minima come due
 * numeri; qui c'e' spazio per dire anche **dove stanno rispetto agli altri
 * giorni**. Ogni colonna ha una barretta verticale sulla stessa scala per
 * tutta la settimana: il giorno che si raffredda si vede scendere prima di
 * leggere un numero.
 *
 * Il resto e' lo stesso vocabolario degli altri widget - il nome in alto nel
 * colore secondario, i glifi pieni di [dayGlyph], le cifre strette - perche'
 * sulla stessa Home devono sembrare della stessa mano.
 */
internal fun DrawScope.weekArt(
    frame: Frame,
    place: String,
    days: List<DayForecast>,
    type: WidgetType,
    ink: WidgetInk,
) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)

    // Nel taglio stretto sette colonne diventano stecchini: meglio quattro
    // giorni leggibili che sette da indovinare.
    val shown = days.take(if (frame.cut == Cut.PICCOLO) 4 else 7)

    val nameSize = box.height * 0.095f
    val tag = type.brush(nameSize, weight = 600, width = 78, letterSpacingEm = 0.10f)
    val tagWidth = type.widthOf(TITOLO, tag)
    val name = fitText(
        value = place,
        x = box.left,
        y = box.top,
        maxWidth = box.width - tagWidth - box.height * 0.10f,
        sizePx = nameSize,
        type = type,
        color = ink.secondary,
    )
    text(TITOLO, box.right - tagWidth, box.top, tag, ink.secondary, alpha = 0.7f)

    val top = box.top + lineHeight(name) * 1.35f
    if (shown.isEmpty()) {
        // Nessun dato: si dice, invece di disegnare colonne vuote che
        // sembrerebbero una settimana senza tempo.
        val empty = type.brush(box.height * 0.16f, weight = 700, width = 72)
        textCentered("--", box.center.x, top + (box.bottom - top - lineHeight(empty)) / 2f, empty, ink.primary)
        return
    }

    columns(Rect(box.left, top, box.right, box.bottom), shown, type, ink)
}

private fun DrawScope.columns(
    area: Rect,
    days: List<DayForecast>,
    type: WidgetType,
    ink: WidgetInk,
) {
    val step = area.width / days.size
    val scale = WeekScale.of(days)

    val label = fittingBrush(
        days.map { it.label }, step * 0.92f, area.height * 0.105f, type,
        weight = 600, width = 78, letterSpacingEm = 0.08f,
    )
    val high = type.brush(area.height * 0.125f, weight = 700, width = 82)
    val low = type.brush(area.height * 0.115f, weight = 500, width = 82)

    val glyphTop = area.top + lineHeight(label) * 1.05f
    val glyphSize = minOf(step * 0.56f, area.height * 0.20f)
    val highTop = glyphTop + glyphSize + area.height * 0.03f
    val lowTop = area.bottom - lineHeight(low)
    val trackTop = highTop + lineHeight(high) * 1.02f
    val trackBottom = lowTop - lineHeight(low) * 0.08f
    val trackWidth = minOf(step * 0.16f, area.height * 0.05f)
    // Sotto questa altezza la barretta sarebbe un puntino che non dice niente:
    // restano i numeri, che dicono lo stesso.
    val withTrack = trackBottom - trackTop > area.height * 0.12f

    val warm = Color(0xFFF08A3C)
    val cool = ink.rain

    days.forEachIndexed { i, day ->
        val cx = area.left + step * (i + 0.5f)
        val today = i == 0
        textCentered(day.label, cx, area.top, label, if (today) ink.primary else ink.secondary)

        dayGlyph(
            Rect(cx - glyphSize / 2f, glyphTop, cx + glyphSize / 2f, glyphTop + glyphSize),
            Wmo.family(day.weatherCode),
            ink,
        )

        textCentered(day.tempMax?.roundToInt()?.let { "$it°" } ?: "--", cx, highTop, high, ink.primary)
        textCentered(day.tempMin?.roundToInt()?.let { "$it°" } ?: "--", cx, lowTop, low, ink.secondary)

        if (!withTrack) return@forEachIndexed
        val left = cx - trackWidth / 2f
        val corner = CornerRadius(trackWidth / 2f)
        drawRoundRect(
            color = ink.secondary.copy(alpha = 0.16f),
            topLeft = Offset(left, trackTop),
            size = Size(trackWidth, trackBottom - trackTop),
            cornerRadius = corner,
        )

        val span = scale?.let { WeekScale.span(day.tempMin, day.tempMax, it) } ?: return@forEachIndexed
        val height = trackBottom - trackTop
        // La scala va dall'alto (la massima della settimana) al basso: il
        // giorno piu' caldo sta piu' in alto, come in un termometro.
        var fillTop = trackTop + height * (1f - span.second)
        var fillBottom = trackTop + height * (1f - span.first)
        // Un giorno con massima e minima vicine resta almeno un tondino,
        // centrato dove cade e dentro la guida.
        if (fillBottom - fillTop < trackWidth) {
            val mid = (fillTop + fillBottom) / 2f
            fillTop = (mid - trackWidth / 2f).coerceIn(trackTop, trackBottom - trackWidth)
            fillBottom = fillTop + trackWidth
        }
        drawRoundRect(
            // Il colore sta fermo sulla scala e non sul giorno: la stessa
            // temperatura ha lo stesso colore in ogni colonna.
            brush = Brush.verticalGradient(listOf(warm, cool), startY = trackTop, endY = trackBottom),
            topLeft = Offset(left, fillTop),
            size = Size(trackWidth, fillBottom - fillTop),
            cornerRadius = corner,
        )
    }
}

/**
 * La scala comune a tutte le colonne: dalla minima alla massima della settimana.
 *
 * Tenuta fuori dal disegno perche' e' l'unica parte con dei conti, e i conti
 * si provano senza un telefono.
 */
internal object WeekScale {

    /** Da quanto a quanto va la settimana, o nullo se non c'e' nessuna temperatura. */
    fun of(days: List<DayForecast>): ClosedFloatingPointRange<Double>? {
        val values = days.flatMap { listOfNotNull(it.tempMin, it.tempMax) }.filter { it.isFinite() }
        val lo = values.minOrNull() ?: return null
        val hi = values.maxOrNull() ?: return null
        return lo..hi
    }

    /**
     * Dove cadono minima e massima di un giorno, come frazioni da 0 (il fondo
     * della scala) a 1 (la cima). Nullo se al giorno manca una delle due.
     *
     * Una settimana tutta alla stessa temperatura non ha un'escursione da
     * dividere: le barrette stanno a meta', invece di dividere per zero.
     */
    fun span(
        min: Double?,
        max: Double?,
        scale: ClosedFloatingPointRange<Double>,
    ): Pair<Float, Float>? {
        if (min == null || max == null || !min.isFinite() || !max.isFinite()) return null
        val width = scale.endInclusive - scale.start
        if (width <= 0.0) return 0.5f to 0.5f
        fun at(v: Double) = ((v - scale.start) / width).coerceIn(0.0, 1.0).toFloat()
        val a = at(minOf(min, max))
        val b = at(maxOf(min, max))
        return a to b
    }
}

private val TITOLO: String get() = tr("SETTIMANA", "WEEK")
