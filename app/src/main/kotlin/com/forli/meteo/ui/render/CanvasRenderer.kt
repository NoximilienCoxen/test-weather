package com.forli.meteo.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.ceil
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
 * La rampa di grigi e' quantizzata in fasce: una rampa continua leggerebbe
 * come un tubo arrotondato, che e' esattamente il risultato da evitare.
 *
 * Il disegno costa una trentina di ristampe del glifo a piena risoluzione,
 * troppo per rifarlo a ogni frame: il risultato finisce in una bitmap tenuta
 * in cache e i frame successivi si limitano a ricopiarla. L'aspetto e'
 * identico, il costo no.
 */
class CanvasRenderer : TemperatureRenderer {

    private data class CacheKey(
        val text: String,
        val fontSizePx: Float,
        val depthPx: Float,
        val steps: Int,
        val angleDeg: Float,
        val palette: NumberPalette,
    )

    /** Piu' cifre convivono sullo schermo: una cache a voce singola le farebbe sfrattare a vicenda. */
    private val cache = object : LinkedHashMap<CacheKey, ImageBitmap>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, ImageBitmap>) =
            size > CACHE_SIZE
    }

    override fun draw(
        scope: DrawScope,
        measurer: TextMeasurer,
        spec: NumberSpec,
        center: Offset,
    ) {
        if (spec.text.isEmpty() || spec.fontSizePx <= 0f) return

        val key = CacheKey(
            text = spec.text,
            fontSizePx = spec.fontSizePx,
            depthPx = spec.depthPx,
            steps = spec.steps,
            angleDeg = spec.angleDeg,
            palette = spec.palette,
        )
        val bitmap = cache[key] ?: bake(scope, measurer, spec)?.also { cache[key] = it } ?: return

        scope.drawImage(
            image = bitmap,
            topLeft = Offset(
                x = center.x - bitmap.width / 2f,
                y = center.y - bitmap.height / 2f,
            ),
        )
    }

    /** Disegna la cifra una volta sola dentro una bitmap fuori schermo. */
    private fun bake(scope: DrawScope, measurer: TextMeasurer, spec: NumberSpec): ImageBitmap? {
        val density = Density(scope.density, scope.fontScale)
        val baseStyle = with(density) {
            TextStyle(
                fontSize = spec.fontSizePx.toSp(),
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.045f).em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
        }

        val solid = measurer.measure(spec.text, baseStyle)
        val glyphWidth = solid.size.width.toFloat()
        val glyphHeight = solid.size.height.toFloat()
        if (glyphWidth <= 0f || glyphHeight <= 0f) return null

        // Margine per estrusione, smusso e ombra portata, che escono dal glifo.
        val margin = spec.depthPx * 1.8f + 12f
        val width = ceil(glyphWidth + margin * 2f).toInt().coerceIn(1, MAX_SIDE)
        val height = ceil(glyphHeight + margin * 2f).toInt().coerceIn(1, MAX_SIDE)

        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = scope.layoutDirection,
            canvas = Canvas(bitmap),
            size = Size(width.toFloat(), height.toFloat()),
        ) {
            paint(this, measurer, spec, baseStyle, solid, Offset(margin, margin))
        }
        return bitmap
    }

    private fun paint(
        scope: DrawScope,
        measurer: TextMeasurer,
        spec: NumberSpec,
        baseStyle: TextStyle,
        solid: TextLayoutResult,
        origin: Offset,
    ) = with(scope) {
        val radians = spec.angleDeg * PI.toFloat() / 180f
        val direction = Offset(cos(radians), sin(radians))
        val depth = spec.depthPx
        val glyphWidth = solid.size.width.toFloat()
        val glyphHeight = solid.size.height.toFloat()

        // 1. Ombra portata morbida, solo sul tema chiaro: su fondo #EFEFF2 e'
        //    l'unica cosa che stacca un oggetto bianco dallo sfondo.
        if (spec.palette.dropShadow) {
            val tail = direction * (depth * 1.02f)
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
                topLeft = origin + direction * (depth * t),
            )
        }

        // 3. Smusso a 45 gradi rivolto alla luce, in alto a sinistra: sbuca
        //    appena oltre la faccia e ne definisce lo spigolo.
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
                    end = origin + Offset(glyphWidth, glyphHeight),
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
        const val CACHE_SIZE = 8
        const val MAX_SIDE = 4096
    }
}
