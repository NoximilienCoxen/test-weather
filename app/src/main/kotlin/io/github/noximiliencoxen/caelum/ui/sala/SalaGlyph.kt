package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Il glifo del tempo: sole, nuvola, pioggia, neve, grandine, temporale.
 *
 * **La prima stesura era un cerchio, un'ellisse e tre trattini**, e a trenta
 * punti di lato non si distingueva una nuvola da una pioggia: le stesse due
 * forme con qualche riga in piu'. In una striscia di otto giorni il glifo e'
 * l'unica cosa che dice che tempo fara', quindi o si riconosce a colpo
 * d'occhio o tanto vale togliere la striscia.
 *
 * Adesso la nuvola e' una **sagoma a tre lobi con la base piatta** - quella con
 * cui chiunque disegna una nuvola - il sole ha i raggi, e cio' che cade ha una
 * forma sua: tratti inclinati per la pioggia, pallini per la grandine, la
 * saetta per il temporale.
 *
 * Disegnato e non importato, come le tre linee del menu: un'app che si disegna
 * le lune a mano non apre una libreria di icone per sei simboli.
 */
fun DrawScope.weatherGlyph(condition: SalaCondition, ink: Color) {
    val w = size.width
    val h = size.height
    val coperto = condition != SalaCondition.SERENO
    val temporale = condition == SalaCondition.TEMPORALE ||
        condition == SalaCondition.TEMPORALE_GRANDINE

    // ── Il sole ──────────────────────────────────────────────────────────────
    // Con la nuvola il sole si ritira in alto a sinistra e perde i raggi:
    // spunta da dietro, non illumina la scena.
    val cx = if (coperto) w * 0.34f else w * 0.50f
    val cy = if (coperto) h * 0.30f else h * 0.46f
    val r = if (coperto) w * 0.16f else w * 0.20f
    drawCircle(color = ink.copy(alpha = if (coperto) 0.50f else 0.95f), radius = r, center = Offset(cx, cy))
    if (!coperto) {
        val lungo = w * 0.085f
        for (i in 0 until 8) {
            val a = (i * 2.0 * PI / 8.0).toFloat()
            val da = Offset(cx + cos(a) * r * 1.45f, cy + sin(a) * r * 1.45f)
            val a2 = Offset(cx + cos(a) * (r * 1.45f + lungo), cy + sin(a) * (r * 1.45f + lungo))
            drawLine(ink.copy(alpha = 0.9f), da, a2, strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        }
        return
    }

    // ── La nuvola: tre lobi e la base piatta ─────────────────────────────────
    val baseY = h * 0.60f
    val sinistra = w * 0.14f
    val destra = w * 0.90f
    val nuvola = Path().apply {
        moveTo(sinistra, baseY)
        arcTo(Rect(sinistra, baseY - w * 0.26f, sinistra + w * 0.30f, baseY + w * 0.04f), 160f, 170f, false)
        arcTo(Rect(w * 0.32f, baseY - w * 0.40f, w * 0.72f, baseY), 175f, 185f, false)
        arcTo(Rect(destra - w * 0.30f, baseY - w * 0.28f, destra, baseY + w * 0.02f), 200f, 150f, false)
        lineTo(sinistra, baseY)
        close()
    }
    drawPath(nuvola, color = ink.copy(alpha = if (temporale) 0.85f else 0.60f))

    // ── Cio' che cade ────────────────────────────────────────────────────────
    val cima = baseY + h * 0.05f
    when (condition) {
        SalaCondition.PIOGGIA -> listOf(0.32f, 0.52f, 0.72f).forEach { fx ->
            drawLine(
                color = ink.copy(alpha = 0.9f),
                start = Offset(w * fx, cima),
                end = Offset(w * (fx - 0.07f), cima + h * 0.24f),
                strokeWidth = w * 0.045f,
                cap = StrokeCap.Round,
            )
        }
        SalaCondition.GRANDINE, SalaCondition.TEMPORALE_GRANDINE ->
            listOf(0.30f to 0.07f, 0.74f to 0.07f).forEach { (fx, dy) ->
                drawCircle(
                    color = ink.copy(alpha = 0.9f),
                    radius = w * 0.05f,
                    center = Offset(w * fx, cima + h * dy),
                )
            }
        else -> Unit
    }

    if (temporale) {
        val x = w * 0.46f
        val saetta = Path().apply {
            moveTo(x + w * 0.10f, cima)
            lineTo(x - w * 0.06f, cima + h * 0.17f)
            lineTo(x + w * 0.04f, cima + h * 0.17f)
            lineTo(x - w * 0.02f, cima + h * 0.32f)
            lineTo(x + w * 0.18f, cima + h * 0.12f)
            lineTo(x + w * 0.07f, cima + h * 0.12f)
            close()
        }
        drawPath(saetta, color = SalaTokens.accent2)
    }
}

/**
 * Il fiocco di neve: tre assi incrociati.
 *
 * E' il minimo per cui si legge "neve" e non "grandine"; a questa dimensione
 * sei braccia con le loro ramificazioni diventano una macchia.
 */
fun DrawScope.fioccoDiNeve(centro: Offset, raggio: Float, ink: Color, alfa: Float = 1f) {
    for (i in 0 until 3) {
        val a = (i * PI / 3.0).toFloat()
        drawLine(
            color = ink.copy(alpha = alfa),
            start = Offset(centro.x - cos(a) * raggio, centro.y - sin(a) * raggio),
            end = Offset(centro.x + cos(a) * raggio, centro.y + sin(a) * raggio),
            strokeWidth = raggio * 0.34f,
            cap = StrokeCap.Round,
        )
    }
}

/** Un cerchietto vuoto: il segno del giorno scelto sul grafico. */
fun DrawScope.anello(centro: Offset, raggio: Float, colore: Color, spessore: Float) {
    drawCircle(color = colore, radius = raggio, center = centro, style = Stroke(width = spessore))
}
