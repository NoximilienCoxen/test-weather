package com.forli.meteo.widget.paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.AirQuality

/**
 * La qualita' dell'aria: l'indice piccolo, la parola grande, il pallino.
 *
 * La parola sta grande e il numero piccolo, non il contrario: quaranta o
 * cinquanta non dice niente a nessuno, "buona" o "scarsa" dice tutto.
 */
internal fun DrawScope.airArt(
    place: String,
    air: AirQuality?,
    type: WidgetType,
    ink: WidgetInk,
) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)
    val band = air?.band

    val name = type.brush(box.height * 0.095f, weight = 600, width = 78, letterSpacingEm = 0.10f)
    val dotRadius = box.height * 0.055f
    text(place, box.left, box.top, name, ink.secondary)

    val dotCentre = Offset(box.right - dotRadius, box.top + lineHeight(name) * 0.45f)
    if (band != null) {
        airDot(dotCentre, dotRadius, band.tint())
    } else {
        airDotEmpty(dotCentre, dotRadius, ink.secondary)
    }

    val index = type.brush(box.height * 0.135f, weight = 700, width = 80)
    val indexTop = box.top + lineHeight(name) * 0.95f
    text(air?.europeanAqi?.toString() ?: "--", box.left, indexTop, index, ink.secondary)

    // La parola prende tutta la larghezza che resta: si sceglie il corpo piu'
    // grande che ci sta, invece di tagliarla o di rimpicciolirla sempre.
    val word = band?.label ?: "ARIA"
    val available = box.width
    var size = box.height * 0.42f
    var brush = type.brush(size, weight = 700, width = 70)
    while (type.widthOf(word, brush) > available && size > box.height * 0.14f) {
        size *= 0.92f
        brush = type.brush(size, weight = 700, width = 70)
    }

    val wordTop = indexTop + lineHeight(index) * 0.85f
    val room = box.bottom - wordTop
    text(
        word,
        box.left,
        wordTop + (room - lineHeight(brush)) / 2f,
        brush,
        ink.primary,
    )
}
