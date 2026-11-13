package com.caelumweather.widget.paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.caelumweather.data.HourForecast
import com.caelumweather.data.Wmo
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.SalaWindUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Il vento: la velocita' grande, la bussola con la freccia, e - nei tagli piu'
 * larghi - le prossime sei ore a colonne.
 *
 * **La freccia punta dove il vento va, non da dove viene.** Chi guarda una
 * bussola con un ago si aspetta che l'ago indichi una direzione verso cui
 * muoversi, e un vento "da est" soffia verso ovest: la scritta sotto dice
 * "da ESE", che e' come lo dicono i bollettini, e la freccia lo mostra come lo
 * si vede - la direzione in cui le foglie volano.
 *
 * Le raffiche compaiono solo quando superano di un terzo il vento medio: sotto,
 * sono rumore, e un numero in piu' in un riquadro stretto ruba spazio a quello
 * che conta.
 */
internal fun DrawScope.ventoArt(
    frame: Frame,
    place: String,
    ore: List<HourForecast>,
    unita: SalaWindUnit,
    type: WidgetType,
    ink: WidgetInk,
) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)
    creditoFonti(CREDITO_OPEN_METEO, type, ink)

    val nameSize = box.height * 0.095f
    val tag = type.brush(nameSize, weight = 600, width = 78, letterSpacingEm = 0.10f)
    val titolo = TITOLO_VENTO
    val tagWidth = type.widthOf(titolo, tag)
    val name = fitText(
        value = place,
        x = box.left,
        y = box.top,
        maxWidth = box.width - tagWidth - box.height * 0.10f,
        sizePx = nameSize,
        type = type,
        color = ink.secondary,
    )
    text(titolo, box.right - tagWidth, box.top, tag, ink.secondary, alpha = 0.7f)

    val top = box.top + lineHeight(name) * 1.35f
    val ora = ore.firstOrNull()
    val velocita = ora?.windSpeed
    if (ora == null || velocita == null) {
        val vuoto = type.brush(box.height * 0.2f, weight = 700, width = 72)
        textCentered("--", box.center.x, top + (box.bottom - top - lineHeight(vuoto)) / 2f, vuoto, ink.primary)
        return
    }

    val conColonne = frame.cut != Cut.PICCOLO && ore.size >= 4
    val altezzaColonne = if (conColonne) (box.bottom - top) * 0.40f else 0f
    val principale = Rect(box.left, top, box.right, box.bottom - altezzaColonne)

    // ── La bussola, a destra ─────────────────────────────────────────────────
    val diametro = minOf(principale.height, principale.width * 0.44f)
    val centro = Offset(principale.right - diametro / 2f, principale.center.y)
    bussola(centro, diametro / 2f, ora.windDirection, type, ink)

    // ── Il numero, a sinistra ────────────────────────────────────────────────
    val larghezzaNumero = principale.width - diametro - box.height * 0.06f
    val numero = unita.from(velocita).roundToInt().toString()
    val cifre = fittingBrush(listOf(numero), larghezzaNumero, principale.height * 0.52f, type, weight = 700, width = 72)
    val sigla = fittingBrush(listOf(unita.label), larghezzaNumero * 0.5f, principale.height * 0.18f, type, weight = 600, width = 82, letterSpacingEm = 0.04f)
    val piccola = type.brush(principale.height * 0.15f, weight = 600, width = 82)

    val raffica = ora.windGusts?.takeIf { it > velocita * 1.33 && unita.from(it).roundToInt() > numero.toInt() }
    val da = ora.windDirection?.let { tr("da ${Wmo.windDirection(it)}", "from ${Wmo.windDirection(it)}", "aus ${Wmo.windDirection(it)}", "de ${Wmo.windDirection(it)}", "del ${Wmo.windDirection(it)}") }
    val righe = listOfNotNull(
        da,
        raffica?.let { tr("raffiche ${unita.from(it).roundToInt()}", "gusts ${unita.from(it).roundToInt()}", "Böen ${unita.from(it).roundToInt()}", "rafales ${unita.from(it).roundToInt()}", "rachas ${unita.from(it).roundToInt()}") },
    )

    val blocco = lineHeight(cifre) * 0.92f + lineHeight(sigla) + righe.size * lineHeight(piccola) * 0.95f
    var y = principale.top + (principale.height - blocco).coerceAtLeast(0f) / 2f
    text(numero, principale.left, y, cifre, ink.primary)
    val larghezzaCifre = type.widthOf(numero, cifre)
    // La sigla accanto alla cifra, sulla base, se ci sta; se no sotto.
    val accanto = larghezzaCifre + box.height * 0.03f + type.widthOf(unita.label, sigla) <= larghezzaNumero
    if (accanto) {
        text(unita.label, principale.left + larghezzaCifre + box.height * 0.03f, y + lineHeight(cifre) * 0.92f - lineHeight(sigla) * 1.05f, sigla, ink.secondary)
        y += lineHeight(cifre) * 0.92f
    } else {
        y += lineHeight(cifre) * 0.92f
        text(unita.label, principale.left, y, sigla, ink.secondary)
        y += lineHeight(sigla)
    }
    righe.forEach { riga ->
        fitText(riga, principale.left, y, larghezzaNumero, piccola.textSize, type, ink.secondary, weight = 600, letterSpacingEm = 0.02f)
        y += lineHeight(piccola) * 0.95f
    }

    // ── Le prossime ore ──────────────────────────────────────────────────────
    if (conColonne) colonneVento(Rect(box.left, principale.bottom + box.height * 0.02f, box.right, box.bottom), ore.take(6), type, ink)
}

/** La bussola: anello, quattro tacche, la N, e la freccia verso dove il vento va. */
private fun DrawScope.bussola(centro: Offset, raggio: Float, daGradi: Double?, type: WidgetType, ink: WidgetInk) {
    val r = raggio * 0.94f
    drawCircle(ink.secondary.copy(alpha = 0.30f), radius = r, center = centro, style = Stroke(width = r * 0.045f))
    // Quattro tacche e, ogni quarantacinque gradi, una piu' piccola.
    for (k in 0 until 8) {
        val a = k * (PI.toFloat() / 4f) - PI.toFloat() / 2f
        val grande = k % 2 == 0
        val dentro = r * (if (grande) 0.86f else 0.92f)
        drawLine(
            ink.secondary.copy(alpha = if (grande) 0.8f else 0.4f),
            Offset(centro.x + cos(a) * dentro, centro.y + sin(a) * dentro),
            Offset(centro.x + cos(a) * r, centro.y + sin(a) * r),
            strokeWidth = r * (if (grande) 0.05f else 0.03f),
            cap = StrokeCap.Round,
        )
    }
    val n = type.brush(r * 0.20f, weight = 700, width = 82)
    textCentered("N", centro.x, centro.y - r * 0.80f, n, ink.secondary, alpha = 0.9f)
    if (daGradi == null) return
    // La freccia: si disegna verso l'alto e poi si ruota. Verso dove va = da + 180.
    rotate(((daGradi + 180.0) % 360.0).toFloat(), centro) {
        val lunga = r * 0.60f
        val coda = r * 0.50f
        val ala = r * 0.20f
        drawLine(ink.accent, Offset(centro.x, centro.y + coda), Offset(centro.x, centro.y - lunga), strokeWidth = r * 0.085f, cap = StrokeCap.Round)
        val punta = Path().apply {
            moveTo(centro.x, centro.y - lunga - r * 0.05f)
            lineTo(centro.x - ala, centro.y - lunga + ala * 1.25f)
            lineTo(centro.x + ala, centro.y - lunga + ala * 1.25f)
            close()
        }
        drawPath(punta, ink.accent)
        drawPath(punta, ink.accent, style = Stroke(width = r * 0.05f, join = StrokeJoin.Round))
    }
    drawCircle(ink.primary, radius = r * 0.055f, center = centro)
}

/** Sei colonne: l'altezza e' la velocita', sotto c'e' l'ora. La prima e' "adesso". */
private fun DrawScope.colonneVento(area: Rect, ore: List<HourForecast>, type: WidgetType, ink: WidgetInk) {
    val passo = area.width / ore.size
    val etichetta = fittingBrush(ore.map { "00" } + listOf(ADESSO_VENTO), passo * 0.92f, area.height * 0.28f, type, weight = 600, width = 78, letterSpacingEm = 0.06f)
    val baseColonne = area.bottom - lineHeight(etichetta) * 1.15f
    val alte = (ore.mapNotNull { it.windSpeed }.maxOrNull() ?: 1.0).coerceAtLeast(6.0)
    val largo = (passo * 0.30f).coerceAtMost(area.height * 0.30f)
    ore.forEachIndexed { i, h ->
        val cx = area.left + passo * (i + 0.5f)
        val v = (h.windSpeed ?: 0.0)
        val altezza = ((baseColonne - area.top) * (v / alte).toFloat()).coerceAtLeast(largo * 0.9f)
        drawRoundRectColonna(cx, baseColonne, largo, altezza, if (i == 0) ink.accent else ink.accent.copy(alpha = 0.45f))
        textCentered(if (i == 0) ADESSO_VENTO else "%02d".format(h.time.hour), cx, baseColonne + lineHeight(etichetta) * 0.12f, etichetta, if (i == 0) ink.primary else ink.secondary)
    }
}

private fun DrawScope.drawRoundRectColonna(cx: Float, base: Float, largo: Float, alto: Float, colore: androidx.compose.ui.graphics.Color) {
    drawRoundRect(
        color = colore,
        topLeft = Offset(cx - largo / 2f, base - alto),
        size = androidx.compose.ui.geometry.Size(largo, alto),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(largo / 2f),
    )
}

private val TITOLO_VENTO: String get() = tr("VENTO", "WIND", "WIND", "VENT", "VIENTO")
private val ADESSO_VENTO: String get() = tr("ORA", "NOW", "JETZT", "MAINT.", "AHORA")
