package io.github.noximiliencoxen.caelum.widget.paint

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Il widget che non ha ancora una localita'.
 *
 * **Esiste perche' prima non esisteva.** Un widget senza configurazione
 * disegnava la citta' aperta nell'app, ed era un'immagine perfettamente
 * plausibile: chi ne aveva scelta un'altra vedeva un widget che sembrava
 * funzionare e diceva la cosa sbagliata, senza un appiglio per capire se la
 * scelta non fosse stata salvata o non fosse stata riletta. Un disegno che
 * dichiara di non sapere e' meno bello e vale di piu': si legge dallo schermo
 * invece che dal logcat.
 *
 * Il tocco porta alla configurazione di questa istanza - lo aggancia
 * `WidgetImage` - quindi il cartello indica una porta che si apre davvero.
 */
internal fun DrawScope.setupArt(title: String, type: WidgetType, ink: WidgetInk) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)

    val name = type.brush(box.height * 0.095f, weight = 600, width = 78, letterSpacingEm = 0.10f)
    text(title, box.left, box.top, name, ink.secondary)

    // L'invito prende tutto lo spazio che resta, spezzato sulle parole e
    // rimpicciolito finche' ci sta. Le due dimensioni del riquadro non sono
    // note qui - un widget 2x2 su un telefono denso e uno su un tablet non
    // hanno gli stessi pixel - e indovinare un corpo fisso vorrebbe dire
    // tagliare la parola piu' lunga su meta' dei dispositivi.
    val top = box.top + lineHeight(name) * 1.2f
    val room = box.bottom - top
    var size = box.height * 0.16f
    var brush = type.brush(size, weight = 700, width = 72)
    var lines = wrap(INVITO, box.width, brush, type)
    while (
        (lines.size * lineHeight(brush) > room || lines.any { type.widthOf(it, brush) > box.width }) &&
        size > box.height * 0.07f
    ) {
        size *= 0.92f
        brush = type.brush(size, weight = 700, width = 72)
        lines = wrap(INVITO, box.width, brush, type)
    }

    val block = lines.size * lineHeight(brush)
    var y = top + (room - block) / 2f
    lines.forEach { riga ->
        textCentered(riga, box.left + box.width / 2f, y, brush, ink.primary)
        y += lineHeight(brush)
    }
}

/**
 * Spezza sulle parole, mai dentro una parola.
 *
 * Una parola piu' larga del riquadro finisce da sola sulla sua riga e sborda:
 * a quel punto e' il ciclo del chiamante a rimpicciolire il corpo, che e' il
 * rimedio giusto. Tagliarla qui vorrebbe dire consegnare una riga monca senza
 * che nessuno se ne accorga.
 */
private fun wrap(
    text: String,
    width: Float,
    brush: android.graphics.Paint,
    type: WidgetType,
): List<String> {
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    text.split(' ').forEach { word ->
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (type.widthOf(candidate, brush) <= width || current.isEmpty()) {
            current = StringBuilder(candidate)
        } else {
            lines += current.toString()
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}

private const val INVITO = "TOCCA PER SCEGLIERE LA CITTÀ"
