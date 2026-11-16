package com.caelumweather.widget.paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.caelumweather.data.EstremoMarea
import com.caelumweather.data.Mare
import com.caelumweather.data.OraMare
import com.caelumweather.data.StatoDelMare
import com.caelumweather.data.TipoMarea
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.TempUnit
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Il mare: l'onda grande, la parola che la descrive, la temperatura dell'acqua e
 * - dove c'e' marea - la prossima alta o bassa.
 *
 * **Dove il mare non c'e' lo dice**, invece di disegnare zeri: una localita'
 * dell'interno non ha onde, e "0,0 m" sarebbe un dato inventato. Le stesse
 * avvertenze del resto dell'app valgono qui, e il widget ne tiene una breve sotto
 * i numeri: stima al largo, non per navigare.
 */
internal fun DrawScope.mareArt(
    frame: Frame,
    place: String,
    ora: OraMare?,
    mare: Mare?,
    /** Il mare e' stato chiesto e la risposta e' "qui non c'e'". */
    senzaMare: Boolean,
    prossima: EstremoMarea?,
    adesso: LocalDateTime?,
    unita: TempUnit,
    type: WidgetType,
    ink: WidgetInk,
) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)
    creditoFonti(CREDITO_OPEN_METEO, type, ink)

    val nameSize = box.height * 0.095f
    val tag = type.brush(nameSize, weight = 600, width = 78, letterSpacingEm = 0.10f)
    val titolo = tr("MARE", "SEA", "MEER", "MER", "MAR")
    val tagWidth = type.widthOf(titolo, tag)
    // Nel quadrato "FONTEVIVO" accanto all'etichetta diventava "FON…": un nome
    // irriconoscibile. Se il nome non ci sta nemmeno stretto, l'etichetta cede
    // il posto - il disegno sotto dice gia' di che widget si tratta.
    val accantoAlNome = box.width - tagWidth - box.height * 0.10f
    val conEtichetta = type.widthOf(place, type.brush(nameSize, weight = 600, width = 58, letterSpacingEm = 0.10f)) <= accantoAlNome
    val name = fitText(
        value = place,
        x = box.left,
        y = box.top,
        maxWidth = if (conEtichetta) accantoAlNome else box.width,
        sizePx = nameSize,
        type = type,
        color = ink.secondary,
    )
    if (conEtichetta) text(titolo, box.right - tagWidth, box.top, tag, ink.secondary, alpha = 0.7f)
    val top = box.top + lineHeight(name) * 1.35f

    if (senzaMare) {
        val parola = fittingBrush(listOf(NESSUN_MARE), box.width, box.height * 0.16f, type, weight = 700, width = 72)
        val nota = fittingBrush(listOf(NON_DI_COSTA), box.width, box.height * 0.10f, type, weight = 600, width = 82)
        val alto = top + (box.bottom - top - lineHeight(parola) - lineHeight(nota)) / 2f
        textCentered(NESSUN_MARE, box.center.x, alto, parola, ink.primary)
        textCentered(NON_DI_COSTA, box.center.x, alto + lineHeight(parola), nota, ink.secondary)
        onde(Rect(box.left, box.bottom - box.height * 0.16f, box.right, box.bottom), 0.1f, ink.accent.copy(alpha = 0.35f))
        return
    }
    if (ora == null || ora.onda == null && ora.acqua == null) {
        val vuoto = type.brush(box.height * 0.2f, weight = 700, width = 72)
        textCentered("--", box.center.x, top + (box.bottom - top - lineHeight(vuoto)) / 2f, vuoto, ink.primary)
        return
    }

    val grande = frame.cut != Cut.PICCOLO
    val metri = ora.onda
    val altezza = metri?.let {
        if (unita == TempUnit.FAHRENHEIT) String.format(Lingue.locale, "%.1f", it * 3.28084) else String.format(Lingue.locale, "%.1f", it)
    } ?: "--"
    val sigla = if (unita == TempUnit.FAHRENHEIT) "ft" else "m"
    val stato = StatoDelMare.di(metri)?.label?.uppercase()

    // La zona alta: il numero, la sigla e lo stato; sotto, l'onda disegnata e le righe.
    val zonaOnde = box.height * (if (grande) 0.20f else 0.19f)
    // Nel quadrato la riga della marea sta sopra l'onda, e il corpo deve finire
    // sopra di lei. Prima il corpo arrivava fino all'onda e la riga ci veniva
    // scritta dentro: "acqua 19°" e "bassa 04:50" uno sopra l'altro.
    val rigaMareaInBasso = !(grande && mare != null && adesso != null) && prossima != null && adesso != null
    val pennelloMarea = type.brush(box.height * 0.10f, weight = 600, width = 82)
    val spazioMarea = if (rigaMareaInBasso) lineHeight(pennelloMarea) * 1.15f else 0f
    val corpo = Rect(box.left, top, box.right, box.bottom - zonaOnde - box.height * 0.02f - spazioMarea)
    val larghezzaNumero = if (grande) corpo.width * 0.50f else corpo.width
    val cifre = fittingBrush(listOf(altezza), larghezzaNumero * 0.72f, corpo.height * 0.55f, type, weight = 700, width = 72)
    val unitaPennello = type.brush(cifre.textSize * 0.36f, weight = 600, width = 82)
    text(altezza, corpo.left, corpo.top, cifre, ink.primary)
    text(sigla, corpo.left + type.widthOf(altezza, cifre) + box.height * 0.02f, corpo.top + lineHeight(cifre) * 0.92f - lineHeight(unitaPennello) * 1.05f, unitaPennello, ink.secondary)
    var y = corpo.top + lineHeight(cifre) * 0.95f
    stato?.let {
        val p = fittingBrush(listOf(it), larghezzaNumero, corpo.height * 0.15f, type, weight = 700, width = 74, letterSpacingEm = 0.04f)
        text(it, corpo.left, y, p, ink.accent)
        y += lineHeight(p)
    }
    ora.acqua?.let {
        val p = fittingBrush(listOf("00"), larghezzaNumero, corpo.height * 0.14f, type, weight = 600, width = 82)
        val riga = tr("acqua ${unita.from(it).roundToInt()}°", "water ${unita.from(it).roundToInt()}°", "Wasser ${unita.from(it).roundToInt()}°", "eau ${unita.from(it).roundToInt()}°", "agua ${unita.from(it).roundToInt()}°")
        fitText(riga, corpo.left, y, larghezzaNumero, p.textSize, type, ink.secondary, weight = 600, letterSpacingEm = 0.02f)
    }

    // A destra, nei tagli larghi: la curva della marea e la prossima alta o bassa.
    if (grande && mare != null && adesso != null) {
        val destra = Rect(corpo.left + larghezzaNumero + box.height * 0.04f, corpo.top, corpo.right, corpo.bottom)
        curvaMarea(destra, mare, adesso, prossima, type, ink)
    } else if (rigaMareaInBasso && prossima != null && adesso != null) {
        // Nel quadrato la marea sta in una riga, sopra l'onda disegnata, nello
        // spazio tenuto libero qui sopra. Si rimpicciolisce prima di troncare:
        // "bassa 04:50 dom…" non dice se e' oggi o domani.
        val riga = rigaMarea(prossima, adesso)
        fitText(riga, box.left, corpo.bottom + box.height * 0.01f, box.width, pennelloMarea.textSize, type, ink.secondary, weight = 600, letterSpacingEm = 0.02f, minScale = 0.7f)
    }

    // L'onda disegnata in fondo: l'ampiezza segue i metri.
    onde(Rect(box.left, box.bottom - zonaOnde, box.right, box.bottom), ((metri ?: 0.0) / 3.0).coerceIn(0.0, 1.0).toFloat(), ink.accent)
}

private fun rigaMarea(e: EstremoMarea, adesso: LocalDateTime): String {
    val tipo = if (e.tipo == TipoMarea.ALTA) tr("alta", "high", "Hochwasser", "pleine mer", "pleamar") else tr("bassa", "low", "Niedrigwasser", "basse mer", "bajamar")
    val ora = String.format(Lingue.locale, "%02d:%02d", e.quando.hour, e.quando.minute)
    val domani = if (e.quando.toLocalDate() != adesso.toLocalDate()) tr(" domani", " tomorrow", " morgen", " demain", " mañana") else ""
    return "$tipo $ora$domani"
}

/** Le ventiquattro ore di marea come una curva, con un punto per ora di adesso e le prossime due estremi. */
private fun DrawScope.curvaMarea(area: Rect, mare: Mare, adesso: LocalDateTime, prossima: EstremoMarea?, type: WidgetType, ink: WidgetInk) {
    val ore = mare.ore.filter { it.livello != null && !it.time.isBefore(adesso.minusHours(1)) && it.time.isBefore(adesso.plusHours(25)) }
    if (ore.size < 4) return
    val riga = prossima?.let { rigaMarea(it, adesso) }
    val p = type.brush(area.height * 0.15f, weight = 600, width = 82)
    val fondo = if (riga != null) area.bottom - lineHeight(p) * 1.2f else area.bottom
    val alto = area.top + area.height * 0.06f
    val min = ore.minOf { it.livello!! }
    val max = ore.maxOf { it.livello!! }.coerceAtLeast(min + 0.05)
    fun x(o: OraMare) = area.left + area.width * (Duration.between(ore.first().time, o.time).toMinutes() / (24f * 60f)).coerceIn(0f, 1f)
    fun y(v: Double) = (fondo - (fondo - alto) * ((v - min) / (max - min))).toFloat()
    val tracciato = Path()
    ore.forEachIndexed { i, o -> if (i == 0) tracciato.moveTo(x(o), y(o.livello!!)) else tracciato.lineTo(x(o), y(o.livello!!)) }
    drawPath(tracciato, ink.accent, style = Stroke(width = area.height * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Il punto di adesso.
    ore.minByOrNull { Duration.between(it.time, adesso).abs() }?.let {
        drawCircle(ink.primary, radius = area.height * 0.05f, center = Offset(x(it), y(it.livello!!)))
    }
    riga?.let { fitText(it, area.left, fondo + lineHeight(p) * 0.2f, area.width, p.textSize, type, ink.secondary, weight = 600, letterSpacingEm = 0.02f) }
}

/** Due strati d'onda, fermi: un widget non si anima, ma il disegno dice "mare". */
private fun DrawScope.onde(area: Rect, forza: Float, colore: androidx.compose.ui.graphics.Color) {
    val ampiezza = area.height * (0.10f + forza * 0.28f)
    for ((livello, fase, alpha) in listOf(Triple(0.45f, 1.9f, 0.40f), Triple(0.62f, 0f, 0.85f))) {
        val p = Path()
        p.moveTo(area.left, area.bottom)
        var x = area.left
        val passo = 3.dp.toPx()
        while (x <= area.right + passo) {
            val a = ((x - area.left) / (area.width * 0.55f)) * 2f * PI.toFloat() + fase
            val onda = sin(a) + 0.22f * sin(2f * a + 0.6f)
            p.lineTo(x, area.top + area.height * livello - onda * ampiezza)
            x += passo
        }
        p.lineTo(area.right, area.bottom)
        p.close()
        drawPath(p, colore.copy(alpha = colore.alpha * alpha))
    }
}

private val NESSUN_MARE: String get() = tr("NESSUN MARE", "NO SEA", "KEIN MEER", "PAS DE MER", "SIN MAR")
private val NON_DI_COSTA: String get() = tr("località non di costa", "not a coastal place", "kein Küstenort", "pas un lieu côtier", "lugar sin costa")
