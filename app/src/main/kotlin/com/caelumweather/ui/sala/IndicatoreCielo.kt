package com.caelumweather.ui.sala

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cosa gira mentre si aspetta: il sole di giorno, la luna di notte, un fiocco
 * quando nevica.
 *
 * Prima c'era il cerchio di Material, viola su bianco: l'unico pezzo di
 * un'altra app dentro un cielo dipinto. Questo e' lo stesso gesto - qualcosa
 * che gira finche' si carica - ma detto con le cose che l'app disegna.
 *
 * **Non gira da solo**: l'animazione infinita parte solo con [caricando] e
 * solo se [movimento]; mentre si tira per aggiornare, l'angolo e' quello del
 * dito ([tiro], da 0 a 1 e oltre), cosi' il sole "si carica" tirando e poi
 * continua da solo. A riposo non disegna niente: la regola e' che a schermo
 * fermo l'app disegna zero fotogrammi.
 */
enum class AstroDiAttesa { SOLE, LUNA, FIOCCO }

@Composable
fun IndicatoreCielo(
    astro: AstroDiAttesa,
    caricando: Boolean,
    tiro: Float,
    colore: Color,
    modifier: Modifier = Modifier,
    movimento: Boolean = true,
    misura: Dp = 28.dp,
) {
    val giro = if (caricando && movimento) {
        val transizione = rememberInfiniteTransition(label = "astro-giro")
        val durata = when (astro) {
            AstroDiAttesa.SOLE -> 3200
            AstroDiAttesa.LUNA -> 2600
            AstroDiAttesa.FIOCCO -> 4200
        }
        transizione.animateFloat(
            initialValue = 0f,
            targetValue = if (astro == AstroDiAttesa.LUNA) 1f else 360f,
            animationSpec = infiniteRepeatable(
                tween(durata, easing = LinearEasing),
                repeatMode = if (astro == AstroDiAttesa.LUNA) RepeatMode.Reverse else RepeatMode.Restart,
            ),
            label = "astro-angolo",
        ).value
    } else {
        0f
    }
    // Il respiro: il sole e il fiocco pulsano appena, la luna dondola.
    val respiro = if (caricando && movimento) {
        val t = rememberInfiniteTransition(label = "astro-respiro")
        t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "astro-scala").value
    } else {
        1f
    }
    Canvas(modifier = modifier.size(misura)) {
        disegnaAstro(astro, caricando, tiro, colore, giro, respiro)
    }
}

/** Il disegno dell'astro, fuori dalla composizione per provarlo (`IndicatoreCieloRenderTest`). */
internal fun DrawScope.disegnaAstro(astro: AstroDiAttesa, caricando: Boolean, tiro: Float, colore: Color, giro: Float, respiro: Float) {
    val lato = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val angoloDito = tiro.coerceIn(0f, 1.4f) * 140f
    when (astro) {
        AstroDiAttesa.SOLE -> sole(c, lato * respiro, colore, if (caricando) giro else angoloDito)
        AstroDiAttesa.FIOCCO -> fiocco(c, lato * respiro, colore, if (caricando) giro else angoloDito)
        // La luna oscilla fra -18 e +18 gradi, come un pendolo; tirando, si inclina.
        AstroDiAttesa.LUNA -> luna(c, lato, colore, if (caricando) (giro - 0.5f) * 36f else angoloDito * 0.25f - 10f, giro)
    }
}

private fun DrawScope.sole(c: Offset, lato: Float, colore: Color, angolo: Float) {
    val r = lato * 0.20f
    // L'alone.
    drawCircle(
        brush = Brush.radialGradient(0f to colore.copy(alpha = 0.40f), 1f to colore.copy(alpha = 0f), center = c, radius = lato * 0.52f),
        radius = lato * 0.52f,
        center = c,
    )
    rotate(angolo, c) {
        for (k in 0 until 8) {
            val a = k * (PI.toFloat() / 4f)
            // I raggi alternati sono piu' lunghi: sono l'otto, non un trattino ripetuto.
            val lungo = if (k % 2 == 0) 0.47f else 0.40f
            drawLine(
                colore,
                Offset(c.x + cos(a) * lato * 0.29f, c.y + sin(a) * lato * 0.29f),
                Offset(c.x + cos(a) * lato * lungo, c.y + sin(a) * lato * lungo),
                strokeWidth = lato * 0.075f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(colore, radius = r, center = c)
}

private fun DrawScope.luna(c: Offset, lato: Float, colore: Color, inclinazione: Float, fase: Float) {
    rotate(inclinazione, c) {
        val r = lato * 0.34f
        val disco = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        val morso = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.55f, c.y - r * 0.28f), r * 0.92f))
        }
        val falce = Path.combine(PathOperation.Difference, disco, morso)
        drawPath(falce, colore)
    }
    // Due stelline che brillano a turno.
    val brilla1 = 0.45f + 0.55f * sin(fase * 2f * PI.toFloat())
    val brilla2 = 0.45f + 0.55f * cos(fase * 2f * PI.toFloat())
    stellina(Offset(c.x + lato * 0.30f, c.y - lato * 0.30f), lato * 0.085f * brilla1, colore.copy(alpha = 0.4f + 0.6f * brilla1))
    stellina(Offset(c.x + lato * 0.38f, c.y + lato * 0.06f), lato * 0.055f * brilla2, colore.copy(alpha = 0.4f + 0.6f * brilla2))
}

private fun DrawScope.stellina(c: Offset, r: Float, colore: Color) {
    if (r <= 0.5f) return
    val tratto = Stroke(width = r * 0.5f, cap = StrokeCap.Round)
    drawLine(colore, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = tratto.width, cap = StrokeCap.Round)
    drawLine(colore, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = tratto.width, cap = StrokeCap.Round)
}

private fun DrawScope.fiocco(c: Offset, lato: Float, colore: Color, angolo: Float) {
    val braccio = lato * 0.46f
    val spessore = lato * 0.06f
    rotate(angolo, c) {
        for (b in 0 until 6) {
            val a = b * (PI.toFloat() / 3f)
            val punta = Offset(c.x + cos(a) * braccio, c.y + sin(a) * braccio)
            drawLine(colore, c, punta, strokeWidth = spessore, cap = StrokeCap.Round)
            // Due coppie di rametti, una a meta' e una vicino alla punta.
            for ((quota, lung) in listOf(0.50f to 0.30f, 0.80f to 0.20f)) {
                val base = Offset(c.x + cos(a) * braccio * quota, c.y + sin(a) * braccio * quota)
                for (lato2 in intArrayOf(-1, 1)) {
                    val ar = a + lato2 * 0.75f
                    drawLine(
                        colore,
                        base,
                        Offset(base.x + cos(ar) * braccio * lung, base.y + sin(ar) * braccio * lung),
                        strokeWidth = spessore * 0.8f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
