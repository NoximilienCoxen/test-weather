package com.caelumweather.ui.sala

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import com.caelumweather.data.HourForecast
import com.caelumweather.data.Wmo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * L'aria fra il cielo e la terra: nebbia, vento, caldo, gelo, e l'ora dopo un
 * rovescio.
 *
 * **Sta accanto a [Scena] e non dentro**, apposta: la scena sono i sette numeri
 * di cio' che c'e' in cielo, con le loro prove e le loro molle tarate, e questi
 * sono cinque numeri di cio' che c'e' **nell'aria**. Ognuno viene da un dato
 * vero dell'ora mostrata, e ogni effetto che accende e' quel dato disegnato -
 * un arcobaleno col sereno di mezzogiorno sarebbe falso, una nebbia senza
 * visibilita' bassa pure.
 *
 * Tutti fra 0 e 1, gia' passati per le molle nella Shell come la scena.
 */
data class Atmosfera(
    /** Nebbia (codici 45/48) o visibilita' bassa: 1 sotto il chilometro. */
    val nebbia: Float = 0f,
    /** Il vento medio, 0 a calma piatta e 1 dai 14 m/s (50 km/h) in su. */
    val vento: Float = 0f,
    /** Dove va il vento sullo schermo: +1 verso destra (soffia da ovest), -1 verso sinistra. */
    val versoVento: Float = 1f,
    /** Il caldo che fa tremolare l'orizzonte: da 0 a 29° fino a 1 a 36°. */
    val caldo: Float = 0f,
    /** Il gelo che fa la brina: da 0 a zero gradi fino a 1 a -5°. */
    val gelo: Float = 0f,
    /** Ha appena smesso di piovere: 1 nell'ora dopo la pioggia, 0,5 in quella dopo ancora. */
    val dopoPioggia: Float = 0f,
)

/**
 * Dove vuole arrivare l'atmosfera all'ora [indice] di [ore]. Puro, per poterlo
 * provare.
 *
 * [codice] e' il codice gia' scelto dalla Shell (quello imposto dalla cattura
 * vince su quello vero), cosi' la nebbia segue la stessa regola del cielo.
 */
fun atmosferaBersaglio(ore: List<HourForecast>, indice: Int, codice: Int?): Atmosfera {
    val ora = ore.getOrNull(indice) ?: return Atmosfera()
    val nebbiaDaCodice = Wmo.family(codice) == Wmo.Family.NEBBIA
    val visibilita = ora.visibility
    val nebbia = when {
        nebbiaDaCodice -> 1f
        visibilita == null -> 0f
        visibilita < 1000.0 -> 0.85f
        visibilita < 4000.0 -> 0.35f
        else -> 0f
    }
    val vento = ((ora.windSpeed ?: 0.0) / 14.0).coerceIn(0.0, 1.0).toFloat()
    // Il dato dice **da dove** viene, in gradi: il verso in cui va e' l'opposto,
    // e sullo schermo conta solo la sua parte est-ovest. Da ovest (270) va a est,
    // cioe' a destra. Senza direzione, a destra: e' quello che fa il vento qui
    // la maggior parte delle volte.
    val verso = ora.windDirection?.let { (-sin(Math.toRadians(it))).toFloat() } ?: 1f
    val t = ora.temperature
    val caldo = t?.let { ((it - 29.0) / 7.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val gelo = t?.let { (-it / 5.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    fun bagnata(h: HourForecast?) = (h?.precipitation ?: 0.0) >= 0.2 ||
        Wmo.family(h?.weatherCode) == Wmo.Family.PIOGGIA || Wmo.family(h?.weatherCode) == Wmo.Family.TEMPORALE
    val asciutta = !bagnata(ora) && Wmo.family(codice) != Wmo.Family.PIOGGIA && Wmo.family(codice) != Wmo.Family.TEMPORALE
    val dopo = when {
        !asciutta -> 0f
        bagnata(ore.getOrNull(indice - 1)) -> 1f
        bagnata(ore.getOrNull(indice - 2)) -> 0.5f
        else -> 0f
    }
    return Atmosfera(nebbia = nebbia, vento = vento, versoVento = verso, caldo = caldo, gelo = gelo, dopoPioggia = dopo)
}

/** La larghezza e l'altezza su cui sono misurate colline e nebbia, come in `SalaCielo`. */
private const val RIF_L = 411f
private const val RIF_H = 914f

/** Un tocco nella nebbia: la apre, poi si richiude. */
internal data class VarcoNebbia(val centro: Offset, val nato: Float)

/** Quanto resta aperto un varco nella nebbia, in secondi. */
internal const val DURATA_VARCO = 2.8f

/**
 * Banchi di nebbia che scivolano bassi fra le colline, e un velo su tutto il
 * cielo che spegne il sole e le stelle quanto basta.
 *
 * I banchi sono ellissi sfumate in verticale; ognuno ha il suo passo e la sua
 * altezza, e rientra dall'altro lato quando esce. Toccando, si apre un varco
 * che si richiude da solo ([VarcoNebbia]).
 */
internal fun DrawScope.nebbia(forza: Float, notte: Float, tempo: Float, varchi: List<VarcoNebbia>) {
    if (forza <= 0.02f) return
    val sx = size.width / RIF_L
    val sy = size.height / RIF_H
    val colore = lerp(Color(0xFFF1EFE9), Color(0xFF8E98A4), notte)
    // Il velo: il cielo intero perde contrasto.
    drawRect(colore.copy(alpha = 0.32f * forza))
    val banchi = listOf(
        // altezza, spessore, larghezza, passo (unita'/s), sfasamento
        floatArrayOf(455f, 70f, 560f, 5.5f, 0f),
        floatArrayOf(520f, 90f, 700f, -3.8f, 180f),
        floatArrayOf(600f, 110f, 760f, 4.4f, 90f),
        floatArrayOf(700f, 130f, 820f, -2.9f, 300f),
        floatArrayOf(560f, 80f, 520f, 6.2f, 400f),
        floatArrayOf(650f, 100f, 640f, -4.9f, 520f),
    )
    // **Il livello fuori schermo solo quando serve.** Un varco nella nebbia si
    // scava con `DstOut`, che vuole un livello suo; ma un livello a tutto
    // schermo a ogni fotogramma ha fatto cadere l'emulatore della CI due volte
    // su due (visibilita' sotto il chilometro a Forli', 12 novembre 2026, nebbia
    // accesa per tutto il giro). Senza varchi aperti i banchi si disegnano
    // direttamente sul cielo, che e' quasi sempre.
    val varchiAperti = varchi.filter { tempo - it.nato in 0f..DURATA_VARCO }
    val conLivello = varchiAperti.isNotEmpty()
    drawIntoCanvas { tela ->
        if (conLivello) tela.saveLayer(Rect(Offset.Zero, size), Paint())
        banchi.forEachIndexed { i, (alto, spesso, largo, passo, sfaso) ->
            val giro = RIF_L + largo
            val x = (((sfaso + tempo * passo) % giro) + giro) % giro - largo
            val respiro = 0.85f + 0.15f * sin(tempo * 0.35f + i * 1.7f)
            // Una macchia rotonda sfumata, schiacciata in verticale: sfuma da
            // tutti i lati, mentre un'ellisse sfumata solo in alto e in basso
            // aveva i fianchi netti e pareva una lente.
            fun banco(sinistra: Float) {
                val centro = Offset((sinistra + largo / 2f) * sx, alto * sy)
                scale(scaleX = 1f, scaleY = spesso / largo * (sy / sx), pivot = centro) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to colore.copy(alpha = 0.9f * forza * respiro),
                            0.55f to colore.copy(alpha = 0.6f * forza * respiro),
                            1f to colore.copy(alpha = 0f),
                            center = centro,
                            radius = largo / 2f * sx,
                        ),
                        radius = largo / 2f * sx,
                        center = centro,
                    )
                }
            }
            banco(x)
            // Il banco esce da un lato e rientra dall'altro: la copia che entra.
            if (x + largo > RIF_L) banco(x - giro)
        }
        // I varchi: un buco morbido che si apre e si richiude.
        varchiAperti.forEach { v ->
            val eta = tempo - v.nato
            val apertura = sin(PI.toFloat() * eta / DURATA_VARCO)
            val r = 95f * sx * (0.4f + 0.6f * apertura)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Black.copy(alpha = apertura),
                    1f to Color.Transparent,
                    center = v.centro,
                    radius = r,
                ),
                radius = r,
                center = v.centro,
                blendMode = BlendMode.DstOut,
            )
        }
        if (conLivello) tela.restore()
    }
}

/**
 * Il tremolio del caldo sopra l'orizzonte: una fascia calda e qualche riga che
 * ondeggia, come l'aria sopra l'asfalto d'estate. Sta dietro le colline.
 */
internal fun DrawScope.caldoSullOrizzonte(forza: Float, tempo: Float) {
    if (forza <= 0.02f) return
    val sx = size.width / RIF_L
    val sy = size.height / RIF_H
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0x00FFC27A),
            0.6f to Color(0xFFFFC27A).copy(alpha = 0.22f * forza),
            1f to Color(0x00FFC27A),
            startY = 400f * sy,
            endY = 520f * sy,
        ),
        topLeft = Offset(0f, 400f * sy),
        size = Size(size.width, 120f * sy),
    )
    val riga = Path()
    for (k in 0 until 4) {
        val base = (450f + k * 13f) * sy
        riga.reset()
        var x = 0f
        riga.moveTo(0f, base)
        while (x <= size.width) {
            val y = base + sin(x / (19f * sx) + tempo * (2.2f + k * 0.5f) + k) * 3.2f * sy * forza
            riga.lineTo(x, y)
            x += 6f * sx
        }
        drawPath(riga, Color.White.copy(alpha = 0.26f * forza * (1f - k * 0.18f)), style = Stroke(width = 1.6f * sx))
    }
}

/** La quota del bordo superiore di un'ellisse alla x data, o null fuori. */
private fun bordoDiEllisse(x: Float, sinistra: Float, alto: Float, largo: Float, altezza: Float): Float? {
    val cx = sinistra + largo / 2f
    val a = largo / 2f
    val b = altezza / 2f
    val u = (x - cx) / a
    if (u <= -1f || u >= 1f) return null
    return alto + b - b * sqrt(1f - u * u)
}

/**
 * L'erba sul crinale delle colline, piegata dal vento.
 *
 * Ciuffi di tre fili lungo il bordo di ciascuna collina, dello stesso colore
 * della collina: una frangia, non un prato disegnato. Si piegano nel verso del
 * vento e ondeggiano tanto piu' in fretta quanto piu' soffia; a calma piatta
 * stanno quasi dritti e respirano appena. Molto e' coperto dalla scheda, e va
 * bene: si vede ai lati e sotto.
 */
internal fun DrawScope.erbaAlVento(
    vento: Float,
    verso: Float,
    tempo: Float,
    colline: List<Pair<Color, FloatArray>>,
) {
    val sx = size.width / RIF_L
    val sy = size.height / RIF_H
    val filo = Path()
    val segno = if (verso >= 0f) 1f else -1f
    val piega = segno * (0.08f + 0.75f * vento) * (0.4f + 0.6f * kotlin.math.abs(verso))
    colline.forEachIndexed { indice, (colore, ellisse) ->
        // Tutti i fili di una collina in un tracciato solo, disegnato una
        // volta: il cielo ridisegna a ogni fotogramma, e centinaia di
        // `drawPath` per l'erba erano il pezzo piu' caro della scena.
        filo.reset()
        val (sinistra, alto, largo, altezza) = ellisse.let { listOf(it[0], it[1], it[2], it[3]) }
        // Le colline davanti coprono il crinale di quelle dietro: lì l'erba non c'e'.
        val davanti = colline.drop(indice + 1).map { it.second }
        var x = maxOf(sinistra + 6f, -4f)
        var i = 0
        while (x < minOf(sinistra + largo - 6f, RIF_L + 4f)) {
            val y = bordoDiEllisse(x, sinistra, alto, largo, altezza)
            val coperta = y != null && davanti.any { e ->
                val bordo = bordoDiEllisse(x, e[0], e[1], e[2], e[3])
                bordo != null && bordo < y + 2f
            }
            if (y != null && !coperta) {
                for (f in 0 until 3) {
                    val semeFilo = ((i * 7 + f * 3) % 11) / 11f
                    val lungo = (5.5f + 4.5f * semeFilo) * sy
                    val ondeggia = sin(tempo * (1.3f + 3.2f * vento) + x * 0.09f + f) * (0.05f + 0.22f * vento)
                    val angolo = piega + ondeggia + (f - 1) * 0.22f
                    val bx = (x + (f - 1) * 1.6f) * sx
                    val by = y * sy + 1.5f * sy
                    val px = bx + sin(angolo) * lungo
                    val py = by - cos(angolo) * lungo
                    filo.moveTo(bx, by)
                    filo.quadraticTo(bx + sin(angolo * 0.4f) * lungo * 0.5f, by - lungo * 0.55f, px, py)
                }
            }
            x += 8f
            i++
        }
        drawPath(filo, colore, style = Stroke(width = 1.5f * sx, cap = StrokeCap.Round))
    }
}

/**
 * Qualche foglia che attraversa lo schermo quando il vento e' forte (dai 28
 * km/h circa). Ruotano mentre volano e salgono e scendono a onde; vanno nel
 * verso del vento. Sotto quella soglia non ce n'e' nessuna.
 */
internal fun DrawScope.foglieAlVento(vento: Float, verso: Float, tempo: Float, colore: Color) {
    val forza = ((vento - 0.55f) / 0.45f).coerceIn(0f, 1f)
    if (forza <= 0f) return
    val sx = size.width / RIF_L
    val sy = size.height / RIF_H
    val segno = if (verso >= 0f) 1f else -1f
    val foglia = Path()
    val quante = 3 + (forza * 5).toInt()
    for (i in 0 until quante) {
        val periodo = 3.6f + (i % 4) * 0.9f - forza * 1.2f
        val fase = ((tempo / periodo) + i * 0.37f) % 1f
        val avanzamento = if (segno > 0f) fase else 1f - fase
        val x = (-30f + avanzamento * (RIF_L + 60f)) * sx
        val y = (360f + (i * 53f) % 300f + sin(fase * 2f * PI.toFloat() * 1.5f + i) * 24f) * sy
        val giro = (tempo * (220f + i * 40f) * segno + i * 70f) % 360f
        val lungo = (7f + (i % 3) * 2f) * sx
        rotate(degrees = giro, pivot = Offset(x, y)) {
            foglia.reset()
            foglia.moveTo(x - lungo, y)
            foglia.quadraticTo(x, y - lungo * 0.55f, x + lungo, y)
            foglia.quadraticTo(x, y + lungo * 0.55f, x - lungo, y)
            drawPath(foglia, colore.copy(alpha = 0.85f * forza.coerceAtLeast(0.5f)))
        }
    }
}
