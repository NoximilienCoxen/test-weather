package io.github.noximiliencoxen.caelum.widget.paint

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Il widget da una cella: l'illustrazione del tempo e la temperatura, basta.
 *
 * Il nome della citta' non c'e': in una cella starebbe solo in un corpo che
 * non si legge, e chi mette un widget da una cella lo sa da se' dov'e'.
 */
internal fun DrawScope.miniArt(forecast: Forecast?, type: WidgetType, ink: WidgetInk) {
    val pad = 8.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)
    val current = forecast?.current
    val degrees = current?.temperature?.roundToInt()?.let { "$it°" } ?: "--"

    val digits = fittingBrush(listOf(degrees), box.width * 0.96f, box.height * 0.36f, type, weight = 700, width = 72)
    val digitsTop = box.bottom - lineHeight(digits)
    val side = minOf(box.width, digitsTop - box.top + lineHeight(digits) * 0.12f)
    weatherBody(
        Rect(box.center.x - side / 2f, box.top, box.center.x + side / 2f, box.top + side),
        Wmo.family(current?.weatherCode),
        current?.isDay ?: true,
        ink,
    )
    textCentered(degrees, box.center.x, digitsTop, digits, ink.primary)
}

/**
 * Le ore che vengono, dall'ora in corso: una colonna per ora con l'ora, il
 * tempo, la temperatura e - quando c'e' - la probabilita' di pioggia.
 */
internal fun DrawScope.hoursArt(
    frame: Frame,
    place: String,
    hours: List<HourForecast>,
    type: WidgetType,
    ink: WidgetInk,
) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)
    val shown = hours.take(if (frame.cut == Cut.PICCOLO) 4 else 6)

    val nameSize = box.height * 0.095f
    val tag = type.brush(nameSize, weight = 600, width = 78, letterSpacingEm = 0.10f)
    val tagWidth = type.widthOf(TITOLO_ORE, tag)
    val name = fitText(
        value = place,
        x = box.left,
        y = box.top,
        maxWidth = box.width - tagWidth - box.height * 0.10f,
        sizePx = nameSize,
        type = type,
        color = ink.secondary,
    )
    text(TITOLO_ORE, box.right - tagWidth, box.top, tag, ink.secondary, alpha = 0.7f)

    val top = box.top + lineHeight(name) * 1.35f
    if (shown.isEmpty()) {
        val empty = type.brush(box.height * 0.16f, weight = 700, width = 72)
        textCentered("--", box.center.x, top + (box.bottom - top - lineHeight(empty)) / 2f, empty, ink.primary)
        return
    }

    val area = Rect(box.left, top, box.right, box.bottom)
    val step = area.width / shown.size
    val labels = shown.mapIndexed { i, h -> if (i == 0) ADESSO else "%02d".format(h.time.hour) }
    val label = fittingBrush(labels, step * 0.92f, area.height * 0.12f, type, weight = 600, width = 78, letterSpacingEm = 0.06f)
    val temp = type.brush(area.height * 0.16f, weight = 700, width = 82)
    val rain = type.brush(area.height * 0.11f, weight = 600, width = 82)

    val glyphTop = area.top + lineHeight(label) * 1.1f
    val rainTop = area.bottom - lineHeight(rain)
    val tempTop = rainTop - lineHeight(temp) * 1.02f
    val glyphSize = minOf(step * 0.78f, tempTop - glyphTop - area.height * 0.02f)

    shown.forEachIndexed { i, hour ->
        val cx = area.left + step * (i + 0.5f)
        textCentered(labels[i], cx, area.top, label, if (i == 0) ink.primary else ink.secondary)
        weatherBody(
            Rect(cx - glyphSize / 2f, glyphTop, cx + glyphSize / 2f, glyphTop + glyphSize),
            Wmo.family(hour.weatherCode),
            hour.isDay,
            ink,
        )
        textCentered(hour.temperature?.roundToInt()?.let { "$it°" } ?: "--", cx, tempTop, temp, ink.primary)
        val p = hour.precipProbability ?: 0
        if (p >= 10) textCentered("$p%", cx, rainTop, rain, ink.rain)
    }
}

/**
 * Le ore dall'ora in corso in poi, nel fuso della citta'. Pura, per i test:
 * l'ora di adesso arriva da fuori.
 */
internal fun prossimeOre(
    forecast: Forecast?,
    adesso: LocalDateTime = forecast?.let { LocalDateTime.now(ZoneOffset.ofTotalSeconds(it.utcOffsetSeconds)) }
        ?: LocalDateTime.now(),
): List<HourForecast> {
    val tutte = forecast?.allHours?.ifEmpty { forecast.hours } ?: return emptyList()
    val da = adesso.truncatedTo(ChronoUnit.HOURS)
    return tutte.filter { !it.time.isBefore(da) }
}

private const val TITOLO_ORE = "PROSSIME ORE"
private const val ADESSO = "ORA"
