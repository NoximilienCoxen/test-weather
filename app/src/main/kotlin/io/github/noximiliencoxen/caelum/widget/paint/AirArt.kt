package io.github.noximiliencoxen.caelum.widget.paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.AirQuality

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

    val dotRadius = box.height * 0.055f
    // La larghezza per il nome finisce dove comincia il pallino, con un
    // respiro in mezzo: il pallino sta in alto a destra sulla stessa riga, e
    // prima il nome ci finiva sotto.
    val name = fitText(
        value = place,
        x = box.left,
        y = box.top,
        maxWidth = box.width - dotRadius * 2f - box.height * 0.04f,
        sizePx = box.height * 0.095f,
        type = type,
        color = ink.secondary,
    )

    val dotCentre = Offset(box.right - dotRadius, box.top + lineHeight(name) * 0.45f)
    if (band != null) {
        airDot(dotCentre, dotRadius, band.tint())
    } else {
        airDotEmpty(dotCentre, dotRadius, ink.secondary)
    }

    val index = type.brush(box.height * 0.135f, weight = 700, width = 80)
    val indexTop = box.top + lineHeight(name) * 0.95f
    text(air?.index?.toString() ?: "--", box.left, indexTop, index, ink.secondary)

    // La parola prende tutta la larghezza che resta: si sceglie il corpo piu'
    // grande che ci sta, invece di tagliarla o di rimpicciolirla sempre.
    //
    // **E se non ci sta su una riga va a capo.** Prima il corpo scendeva fino
    // a un minimo e li' si fermava, ci stesse o no: in un widget piu' alto che
    // largo "MOLTO SCARSA" arrivava al minimo ancora piu' larga del riquadro e
    // usciva dal bordo. Due righe grandi si leggono meglio di una minuscola.
    val word = band?.label ?: "ARIA"
    val wordTop = indexTop + lineHeight(index) * 0.85f
    val room = box.bottom - wordTop
    var size = box.height * 0.42f
    var brush = type.brush(size, weight = 700, width = 70)
    var lines = listOf(word)
    while (size > box.height * 0.06f) {
        brush = type.brush(size, weight = 700, width = 70)
        lines = if (type.widthOf(word, brush) <= box.width) listOf(word) else wrap(word, box.width, brush, type)
        val fits = lines.all { type.widthOf(it, brush) <= box.width } &&
            lines.size * lineHeight(brush) <= room
        if (fits) break
        size *= 0.92f
    }

    var y = wordTop + (room - lines.size * lineHeight(brush)) / 2f
    lines.forEach { line ->
        text(line, box.left, y, brush, ink.primary)
        y += lineHeight(brush)
    }
}
