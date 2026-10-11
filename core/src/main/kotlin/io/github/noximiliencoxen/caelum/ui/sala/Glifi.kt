package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.noximiliencoxen.caelum.data.Wmo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// I glifi del tempo, senza `@Composable`: solo codice di disegno, cosi' li
// usano il telefono e l'orologio (modulo `core`). `IconaMeteo`, che li mette in
// una `Canvas`, resta in `ui/sala/SalaChrome.kt` del modulo `app`.

/**
 * Il tempo di un giorno, ridotto a **cosa si disegna**.
 *
 * Non e' [SalaCondition] con altri nomi: quella distingue cio' che cambia il
 * colore del cielo, questa cio' che cambia la figuretta. Un cielo sereno e uno
 * poco coperto tingono la stessa carta e hanno due icone diverse; una pioggia e
 * una pioviggine hanno la stessa icona e non lo stesso cielo.
 */
enum class GlifoMeteo { SOLE, POCO, NUVOLE, NEBBIA, PIOGGIA, TEMPORALE, NEVE }

/**
 * Quale figuretta per questo codice WMO e questa nuvolosita'.
 *
 * La nuvolosita' entra solo dove il codice non decide gia' tutto: fra "sereno"
 * e "poco coperto" il codice WMO non ha una riga sola, ce l'ha il dato.
 */
fun glifoDi(weatherCode: Int?, cloudCover: Int?): GlifoMeteo {
    val famiglia = Wmo.family(weatherCode)
    return when {
        famiglia == Wmo.Family.TEMPORALE -> GlifoMeteo.TEMPORALE
        famiglia == Wmo.Family.NEVE -> GlifoMeteo.NEVE
        // Qui c'era una riga per la grandine, e non la raggiungeva nessun
        // codice: 77, 85 e 86 sono di famiglia NEVE, quindi li consuma gia' la
        // riga sopra. Era irraggiungibile, non inutilizzata.
        weatherCode == 96 || weatherCode == 99 -> GlifoMeteo.TEMPORALE
        famiglia == Wmo.Family.PIOGGIA -> GlifoMeteo.PIOGGIA
        famiglia == Wmo.Family.NEBBIA -> GlifoMeteo.NEBBIA
        // Senza nuvolosita' decide il codice: il 3 e' "coperto", e un sole che
        // spunta da dietro la nuvola lo racconterebbe meglio di com'e'.
        famiglia == Wmo.Family.NUVOLOSO ->
            if ((cloudCover ?: if (weatherCode == 3) 100 else 60) < 62) GlifoMeteo.POCO else GlifoMeteo.NUVOLE
        else -> if ((cloudCover ?: 0) < 25) GlifoMeteo.SOLE else GlifoMeteo.POCO
    }
}

/** I colori delle figurette: fissi, in tutti e due i temi. */
internal object ColoriGlifo {
    val sole = Color(0xFFFFC83D)
    val bordoSole = Color(0xFFF2A516)
    val nuvola = Color(0xFFF6F7FA)
    val bordoNuvola = Color(0xFF8C96A5)
    val nuvolaCarica = Color(0xFFC3CAD4)
    val bordoCarica = Color(0xFF737D8C)
    val goccia = Color(0xFF4A93E0)
    val luna = Color(0xFFA9BDF5)
    val fiocco = Color(0xFF7FBDEB)
}

fun DrawScope.disegnaGlifo(glifo: GlifoMeteo, notte: Boolean) {
    val u = size.minDimension / 24f
    // Centrata anche su una tela non quadrata: i widget ne danno di ogni forma.
    val ox = (size.width - 24f * u) / 2f
    val oy = (size.height - 24f * u) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)

    // Il sole a petali: otto tondi attorno a un disco col bordo appena piu' caldo.
    fun sole(cx: Float, cy: Float, r: Float) {
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            drawCircle(ColoriGlifo.sole, r * 0.36f * u, p(cx + cos(a) * r * 1.32f, cy + sin(a) * r * 1.32f))
        }
        drawCircle(ColoriGlifo.bordoSole, (r + 0.6f) * u, p(cx, cy))
        drawCircle(ColoriGlifo.sole, r * u, p(cx, cy))
    }

    // La falce: un disco meno un disco spostato, come il terminatore.
    fun luna(cx: Float, cy: Float, r: Float) {
        val disco = Path().apply { addOval(Rect(p(cx - r, cy - r), p(cx + r, cy + r))) }
        val morso = Path().apply {
            addOval(Rect(p(cx - r + r * 0.62f, cy - r - r * 0.18f), p(cx + r + r * 0.62f, cy + r - r * 0.18f)))
        }
        drawPath(Path().apply { op(disco, morso, PathOperation.Difference) }, ColoriGlifo.luna)
    }

    // La nuvola: una base arrotondata e due gobbe, unite in una sagoma sola
    // perche' il bordo giri attorno a tutto e non dentro.
    fun nuvola(ox: Float, oy: Float, scala: Float, carica: Boolean) {
        fun q(x: Float, y: Float) = p(ox + x * scala, oy + y * scala)
        val base = Path().apply {
            addRoundRect(RoundRect(Rect(q(3f, 11f), q(21f, 19f)), CornerRadius(4f * scala * u)))
        }
        val sinistra = Path().apply { addOval(Rect(q(4.5f, 7f), q(13.5f, 16f))) }
        val destra = Path().apply { addOval(Rect(q(9f, 4f), q(20f, 15f))) }
        val sagoma = Path().apply {
            op(base, sinistra, PathOperation.Union)
            op(this, destra, PathOperation.Union)
        }
        drawPath(
            sagoma,
            if (carica) ColoriGlifo.bordoCarica else ColoriGlifo.bordoNuvola,
            style = Stroke(width = 2.2f * u, join = StrokeJoin.Round),
        )
        drawPath(sagoma, if (carica) ColoriGlifo.nuvolaCarica else ColoriGlifo.nuvola)
    }

    // La goccia: un tondo con la punta in alto, che e' da dove viene.
    fun goccia(x: Float, y: Float) {
        drawCircle(ColoriGlifo.goccia, 1.3f * u, p(x, y))
        drawPath(
            Path().apply {
                p(x - 1.2f, y - 0.5f).let { moveTo(it.x, it.y) }
                p(x + 1.2f, y - 0.5f).let { lineTo(it.x, it.y) }
                p(x + 1.0f, y - 3.2f).let { lineTo(it.x, it.y) }
                close()
            },
            ColoriGlifo.goccia,
        )
    }

    // Il fiocco: tre stanghette incrociate, sei punte.
    fun fiocco(x: Float, y: Float, r: Float) {
        for (k in 0 until 3) {
            val a = k * PI.toFloat() / 3f
            drawLine(
                ColoriGlifo.fiocco,
                p(x - cos(a) * r, y - sin(a) * r),
                p(x + cos(a) * r, y + sin(a) * r),
                strokeWidth = 0.9f * u,
                cap = StrokeCap.Round,
            )
        }
    }

    when (glifo) {
        GlifoMeteo.SOLE -> if (notte) luna(12f, 12f, 8f) else sole(12f, 12f, 6f)
        GlifoMeteo.POCO -> {
            if (notte) luna(9f, 8f, 5.5f) else sole(9f, 8f, 4.6f)
            nuvola(3.5f, 4f, 0.85f, carica = false)
        }
        GlifoMeteo.NUVOLE -> {
            nuvola(-1f, -1.5f, 0.7f, carica = true)
            nuvola(2f, 3.5f, 0.9f, carica = false)
        }
        // La nebbia: una nuvola chiara che si sfalda in tre strisce.
        GlifoMeteo.NEBBIA -> {
            nuvola(0f, -4.5f, 1f, carica = false)
            // Da sinistra a destra, sfalsate: (inizio, riga, fine).
            listOf(Triple(4f, 17.5f, 16f), Triple(7f, 20f, 20f), Triple(5f, 22.5f, 14f)).forEach { (da, y, a) ->
                drawLine(ColoriGlifo.bordoNuvola, p(da, y), p(a, y), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            }
        }
        GlifoMeteo.PIOGGIA -> {
            nuvola(0f, -3.5f, 1f, carica = true)
            goccia(7f, 20f); goccia(12f, 21.5f); goccia(17f, 20f)
        }
        GlifoMeteo.TEMPORALE -> {
            nuvola(0f, -4f, 1f, carica = true)
            val punte = listOf(11f to 12f, 8f to 18f, 11f to 18f, 9.5f to 23f, 15f to 16f, 12f to 16f, 13.5f to 12f)
            val fulmine = Path().apply {
                punte.forEachIndexed { i, (x, y) ->
                    val q = p(x, y)
                    if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
                }
                close()
            }
            drawPath(fulmine, ColoriGlifo.sole)
            drawPath(fulmine, ColoriGlifo.bordoSole, style = Stroke(width = 0.7f * u, join = StrokeJoin.Round))
        }
        GlifoMeteo.NEVE -> {
            nuvola(0f, -3.5f, 1f, carica = false)
            fiocco(7f, 20f, 1.8f); fiocco(12f, 21.8f, 1.8f); fiocco(17f, 20f, 1.8f)
        }
    }
}
