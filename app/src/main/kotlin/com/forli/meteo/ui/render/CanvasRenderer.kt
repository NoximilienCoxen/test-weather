package com.forli.meteo.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Estrusione ottenuta ristampando il testo lungo un vettore fisso.
 *
 * L'ordine di disegno e' quello che decide il materiale:
 *   ombra -> corpo estruso (dal fondo alla faccia) -> smusso illuminato ->
 *   faccia frontale -> iridescenza come bordo sottile.
 *
 * La rampa di grigi e' quantizzata in fasce: una rampa continua leggerebbe come
 * un tubo arrotondato, che e' esattamente il risultato da evitare.
 */
class CanvasRenderer : TemperatureRenderer {

    override fun draw(
        scope: DrawScope,
        measurer: TextMeasurer,
        spec: NumberSpec,
        center: Offset,
    ) = with(scope) {
        if (spec.text.isEmpty() || spec.fontSizePx <= 0f) return@with

        val baseStyle = TextStyle(
            fontSize = spec.fontSizePx.toSp(),
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.045f).em,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )

        val solid = measurer.measure(spec.text, baseStyle)
        val w = solid.size.width.toFloat()
        val h = solid.size.height.toFloat()
        val origin = Offset(center.x - w / 2f, center.y - h / 2f)

        val rad = spec.angleDeg * PI.toFloat() / 180f
        val dir = Offset(cos(rad), sin(rad))
        val depth = spec.depthPx

        // 1. Ombra portata morbida, solo sul tema chiaro: su fondo #EFEFF2 e'
        //    l'unica cosa che stacca un oggetto bianco dallo sfondo.
        if (spec.palette.dropShadow) {
            val tail = dir * (depth * 1.02f)
            for (k in 1..SHADOW_STAMPS) {
                val spread = depth * 0.06f * k
                drawText(
                    textLayoutResult = solid,
                    color = Color.Black.copy(alpha = 0.026f),
                    topLeft = origin + tail + Offset(spread * 0.55f, spread),
                )
            }
        }

        // 2. Corpo estruso, dal fondo verso la faccia.
        for (i in spec.steps downTo 1) {
            val t = i.toFloat() / spec.steps
            val band = (floor(t * EXTRUSION_BANDS) / EXTRUSION_BANDS).coerceIn(0f, 1f)
            drawText(
                textLayoutResult = solid,
                color = lerp(spec.palette.sideNear, spec.palette.sideFar, band),
                topLeft = origin + dir * (depth * t),
            )
        }

        // 3. Smusso a 45 gradi rivolto alla sorgente di luce, in alto a sinistra.
        //    Sbuca appena oltre la faccia e ne definisce lo spigolo.
        val chamfer = (depth * 0.055f).coerceAtLeast(1f)
        drawText(
            textLayoutResult = solid,
            color = spec.palette.chamfer,
            topLeft = origin + Offset(-chamfer, -chamfer),
        )

        // 4. Faccia frontale: tinta piatta, satinata. Nessun gradiente colorato.
        drawText(
            textLayoutResult = solid,
            color = spec.palette.face,
            topLeft = origin,
        )

        // 5. Iridescenza: solo sugli smussi e negli angoli interni. E' un filo
        //    di contorno sfalsato, non un riempimento.
        val strokeWidth = (depth * 0.05f).coerceIn(1.5f, 6f)
        val iridescent = measurer.measure(
            text = spec.text,
            style = baseStyle.copy(
                brush = Brush.linearGradient(
                    colors = spec.palette.iridescence,
                    start = origin,
                    end = origin + Offset(w, h),
                ),
                drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round),
            ),
        )
        drawText(
            textLayoutResult = iridescent,
            topLeft = origin + Offset(-chamfer * 0.7f, -chamfer * 0.7f),
            alpha = spec.palette.iridescenceAlpha,
        )
    }

    private companion object {
        /** Poche fasce nette: e' cio' che rende le facce laterali piatte. */
        const val EXTRUSION_BANDS = 6f
        const val SHADOW_STAMPS = 10
    }
}
