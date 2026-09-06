package io.github.noximiliencoxen.caelum.widget.paint

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Wmo
import kotlin.math.roundToInt

/**
 * Il widget del tempo adesso, nei tre tagli.
 *
 * La cifra e' stretta e piena - Archivo alla larghezza minima - perche' e' la
 * sola cosa che si legge da lontano, e una cifra tonda alla stessa altezza
 * occuperebbe il doppio dicendo lo stesso.
 */
internal fun DrawScope.currentArt(
    frame: Frame,
    place: Place,
    forecast: Forecast?,
    type: WidgetType,
    ink: WidgetInk,
) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)
    val current = forecast?.current
    val family = Wmo.family(current?.weatherCode)
    val isDay = current?.isDay ?: true
    val degrees = current?.temperature?.roundToInt()?.let { "$it°" } ?: "--"
    val condition = Wmo.condition(current?.weatherCode)

    when (frame.cut) {
        Cut.PICCOLO -> small(box, place, degrees, family, isDay, type, ink)
        Cut.MEDIO -> wide(box, place, degrees, condition, family, isDay, forecast, type, ink)
        Cut.GRANDE -> tall(box, place, degrees, condition, family, isDay, forecast, type, ink)
    }
}

/**
 * Quadrato: nome sopra, cifra sotto, illustrazione a riempire il resto.
 *
 * La cifra sta in alto e non al centro: l'illustrazione ha bisogno di tutto lo
 * spazio che avanza per leggersi come un corpo invece che come un puntino.
 */
private fun DrawScope.small(
    box: Rect,
    place: Place,
    degrees: String,
    family: Wmo.Family,
    isDay: Boolean,
    type: WidgetType,
    ink: WidgetInk,
) {
    val name = placeName(
        value = place.name.uppercase(),
        x = box.left,
        y = box.top,
        maxWidth = box.width,
        sizePx = box.height * 0.105f,
        type = type,
        color = ink.secondary,
    )

    val digits = type.brush(box.height * 0.30f, weight = 700, width = 72)
    val digitsTop = box.top + lineHeight(name) * 0.95f
    text(degrees, box.left, digitsTop, digits, ink.primary)

    val artTop = digitsTop + lineHeight(digits) * 0.86f
    val side = minOf(box.width, box.bottom - artTop)
    weatherBody(
        Rect(box.right - side, box.bottom - side, box.right, box.bottom),
        family,
        isDay,
        ink,
    )
}

/**
 * Largo: la cifra a sinistra, il posto e il tempo accanto, il corpo a destra.
 * Sotto, la striscia delle ore.
 */
private fun DrawScope.wide(
    box: Rect,
    place: Place,
    degrees: String,
    condition: String,
    family: Wmo.Family,
    isDay: Boolean,
    forecast: Forecast?,
    type: WidgetType,
    ink: WidgetInk,
) {
    val hours = forecast?.hours.orEmpty()
    val stripHeight = if (hours.isEmpty()) 0f else box.height * 0.30f
    val head = Rect(box.left, box.top, box.right, box.bottom - stripHeight)

    header(head, place, degrees, condition, family, isDay, type, ink)

    if (hours.isNotEmpty()) {
        hourStrip(
            Rect(box.left, box.bottom - stripHeight * 0.86f, box.right, box.bottom),
            hours.take(24),
            type,
            ink,
        )
    }
}

/** Alto: come il largo, piu' la settimana in fondo. */
private fun DrawScope.tall(
    box: Rect,
    place: Place,
    degrees: String,
    condition: String,
    family: Wmo.Family,
    isDay: Boolean,
    forecast: Forecast?,
    type: WidgetType,
    ink: WidgetInk,
) {
    val hours = forecast?.hours.orEmpty()
    val days = forecast?.days.orEmpty()

    val headHeight = box.height * 0.38f
    val stripHeight = if (hours.isEmpty()) 0f else box.height * 0.17f
    val daysTop = box.top + headHeight + stripHeight + box.height * 0.06f

    header(
        Rect(box.left, box.top, box.right, box.top + headHeight),
        place, degrees, condition, family, isDay, type, ink,
    )

    if (hours.isNotEmpty()) {
        hourStrip(
            Rect(box.left, box.top + headHeight, box.right, box.top + headHeight + stripHeight),
            hours.take(24),
            type,
            ink,
        )
    }
    if (days.isNotEmpty()) {
        dayStrip(Rect(box.left, daysTop, box.right, box.bottom), days, type, ink)
    }
}

/** La testata comune ai due tagli grandi. */
private fun DrawScope.header(
    box: Rect,
    place: Place,
    degrees: String,
    condition: String,
    family: Wmo.Family,
    isDay: Boolean,
    type: WidgetType,
    ink: WidgetInk,
) {
    val digits = type.brush(box.height * 0.72f, weight = 700, width = 72)
    val digitsHeight = lineHeight(digits)
    val digitsTop = box.top + (box.height - digitsHeight) * 0.35f
    text(degrees, box.left, digitsTop, digits, ink.primary)

    val nameSize = box.height * 0.15f
    val what = type.brush(box.height * 0.21f, weight = 700, width = 76, letterSpacingEm = 0.02f)
    val textLeft = box.left + type.widthOf(degrees, digits) + box.height * 0.10f
    val side = box.height * 0.92f
    // Il testo si ferma dove comincia il disegno del cielo, non al bordo: a
    // destra c'e' il sole o la nuvola, e il nome ci finiva sopra.
    val textWidth = (box.right - side) - textLeft - box.height * 0.06f

    // Il corpo non cambia stringendo, quindi il blocco si puo' misurare prima
    // di scrivere: `placeName` restituisce lo stesso `lineHeight` che avrebbe
    // avuto il pennello largo.
    val block = lineHeight(type.brush(nameSize, 600, 78, 0.10f)) + lineHeight(what)
    val blockTop = digitsTop + (digitsHeight - block) / 2f

    val name = placeName(
        value = place.name.uppercase(),
        x = textLeft,
        y = blockTop,
        maxWidth = textWidth,
        sizePx = nameSize,
        type = type,
        color = ink.secondary,
    )
    text(condition, textLeft, blockTop + lineHeight(name), what, ink.primary)

    weatherBody(
        Rect(box.right - side, box.top, box.right, box.top + side),
        family,
        isDay,
        ink,
    )
}
