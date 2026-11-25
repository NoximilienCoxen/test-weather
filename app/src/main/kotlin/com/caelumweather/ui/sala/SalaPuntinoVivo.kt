package com.caelumweather.ui.sala

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le icone delle sale **in movimento**, per il puntino della sala in cui si
 * arriva (`PuntiniSale`).
 *
 * A riposo i puntini sono puntini. Quando si sfoglia, quello della sala nuova
 * diventa per un attimo la sua figuretta viva - la nuvola che lascia cadere le
 * gocce, il sole che gira, la manica che sventola - e poi torna trattino. Dice
 * dove sei arrivato senza leggere niente.
 *
 * Stessa famiglia di [disegnaIcona]: griglia di ventiquattro, tratto di due,
 * punte arrotondate. [fase] corre libera (un giro ogni unita'), [comparsa] va
 * da zero a uno mentre la figura entra, per le parti che si costruiscono.
 */
internal fun DrawScope.disegnaIconaViva(sala: SalaRoom, ink: Color, fase: Float, comparsa: Float) {
    val u = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val tratto = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun linea(x0: Float, y0: Float, x1: Float, y1: Float, c: Color = ink) =
        drawLine(c, p(x0, y0), p(x1, y1), strokeWidth = 2f * u, cap = StrokeCap.Round)
    val giro = fase - kotlin.math.floor(fase)
    val tau = 2f * PI.toFloat()

    when (sala) {
        // Il sole che gira piano, coi raggi che respirano.
        SalaRoom.OGGI -> {
            drawCircle(ink, 4.2f * u, p(12f, 12f))
            rotate(degrees = fase * 90f, pivot = p(12f, 12f)) {
                for (k in 0 until 8) {
                    val a = k * PI.toFloat() / 4f
                    val respiro = 0.8f * sin(fase * tau + k * 0.8f)
                    val r1 = 9.2f + respiro
                    linea(12f + cos(a) * 7f, 12f + sin(a) * 7f, 12f + cos(a) * r1, 12f + sin(a) * r1)
                }
            }
        }
        // La nuvola, e le gocce che cadono e spariscono sotto.
        SalaRoom.PIOGGIA -> {
            val nuvola = Path().apply {
                arcTo(Rect(p(4f, 6.5f), p(11f, 13.5f)), 90f, 180f, true)
                arcTo(Rect(p(7.5f, 1f), p(17.5f, 11f)), 198f, 153f, false)
                arcTo(Rect(p(13.5f, 6.5f), p(20.5f, 13.5f)), -72.8f, 162.8f, false)
                close()
            }
            val xs = listOf(8f, 12.5f, 17f)
            xs.forEachIndexed { k, x ->
                val t = (fase * 1.6f + k * 0.37f).let { it - kotlin.math.floor(it) }
                val y = 14.5f + t * 7.5f
                val alfa = (1f - t) * comparsa
                linea(x - (y - 14.5f) * 0.18f, y, x - (y - 14.5f) * 0.18f - 0.5f, y + 2.2f, ink.copy(alpha = ink.alpha * alfa))
            }
            drawPath(nuvola, ink, style = tratto)
        }
        // La falce che dondola, e la stellina che brilla.
        SalaRoom.LUNA -> {
            rotate(degrees = 8f * sin(fase * tau), pivot = p(11f, 13f)) {
                val disco = Path().apply { addOval(Rect(p(3f, 5f), p(19f, 21f))) }
                val morso = Path().apply { addOval(Rect(p(8.6f, 2.4f), p(23f, 16.8f))) }
                drawPath(Path().apply { op(disco, morso, PathOperation.Difference) }, ink)
            }
            val brillio = 0.55f + 0.45f * sin(fase * tau * 2f)
            val stella = Path().apply {
                for (k in 0..8) {
                    val a = -PI.toFloat() / 2f + k * PI.toFloat() / 4f
                    val r = (if (k % 2 == 0) 2.8f else 2.8f * 0.38f) * (0.6f + 0.5f * brillio)
                    val q = Offset((18.5f + cos(a) * r) * u, (5f + sin(a) * r) * u)
                    if (k == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
                }
                close()
            }
            drawPath(stella, ink, alpha = 0.4f + 0.6f * brillio)
        }
        // L'aria: le correnti scorrono, i granelli vanno alla deriva.
        SalaRoom.ARIA -> {
            val s = sin(fase * tau) * 1.2f
            val correnti = Path().apply {
                moveTo((3f + s) * u, 8f * u); lineTo((14f + s) * u, 8f * u)
                cubicTo((17.5f + s) * u, 8f * u, (17.5f + s) * u, 3.5f * u, (14.5f + s) * u, 3.8f * u)
                moveTo((3f - s) * u, 12.5f * u); lineTo((19f - s) * u, 12.5f * u)
                cubicTo((22.5f - s) * u, 12.5f * u, (22.5f - s) * u, 17f * u, (19.5f - s) * u, 17f * u)
                moveTo((3f + s) * u, 17f * u); lineTo((10f + s) * u, 17f * u)
            }
            drawPath(correnti, ink, style = tratto)
            val d1 = (giro * 6f) % 6f
            drawCircle(ink, 1.2f * u, p(12f + d1, 17.2f + sin(fase * tau) * 0.8f))
            drawCircle(ink, 1.2f * u, p(5f + d1, 21f - sin(fase * tau) * 0.8f), alpha = 1f - d1 / 6f)
        }
        // La manica a vento che si gonfia e sventola.
        SalaRoom.VENTO -> {
            linea(4f, 21f, 4f, 3.5f)
            val onda = sin(fase * tau * 1.5f)
            val coda = 19f - 1.2f * (1f - comparsa)
            val dy = onda * 1.6f
            val manica = Path().apply {
                moveTo(4f * u, 5f * u)
                lineTo(coda * u, (7.2f + dy) * u)
                lineTo(coda * u, (11.8f + dy) * u)
                lineTo(4f * u, 14f * u)
                close()
            }
            drawPath(manica, ink, style = tratto)
            linea(9f, 5.8f + dy * 0.33f, 9f, 13.2f + dy * 0.33f)
            linea(14f, 6.5f + dy * 0.66f, 14f, 12.5f + dy * 0.66f)
        }
        // Il sole che sale sull'orizzonte e apre i raggi.
        SalaRoom.UV -> {
            val salita = 3f * (1f - comparsa)
            linea(3f, 18f, 21f, 18f)
            val mezzo = Path().apply {
                moveTo(7f * u, (18f + salita) * u)
                arcTo(Rect(p(7f, 13f + salita), p(17f, 23f + salita)), 180f, 180f, false)
                close()
            }
            // Sotto l'orizzonte il sole non si vede.
            clipRect(bottom = 18f * u) { drawPath(mezzo, ink) }
            for (k in 0 until 5) {
                val a = PI.toFloat() + (k + 0.5f) * PI.toFloat() / 5f
                val pulsa = 0.9f * sin(fase * tau - k * 0.9f)
                val r1 = (10f + pulsa) * comparsa + 7.5f * (1f - comparsa)
                linea(12f + cos(a) * 7.5f, 18f + sin(a) * 7.5f, 12f + cos(a) * r1, 18f + sin(a) * r1)
            }
        }
    }
}
