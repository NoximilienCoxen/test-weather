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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estrusione ottenuta ristampando il testo lungo un vettore fisso.
 *
 * L'ordine di disegno decide il materiale, e la gerarchia dei valori con esso:
 * la faccia frontale e' il piano piu' chiaro, lo smusso sta appena sotto
 * perche' inclinato riceve meno luce, poi scende la rampa laterale. Invertire
 * faccia e smusso fa leggere la cifra come un contorno vuoto.
 *
 * La rampa segue la direzione della luce, non la profondita': dare un colore
 * per fascia di profondita' significa ridisegnare il contorno del glifo a ogni
 * fascia, e il risultato si legge come strati di cipolla.
 *
 * Il disegno viene cotto in tre piani distinti e riusato: il movimento li fa
 * scorrere l'uno sull'altro senza ricostruire nulla.
 */
class CanvasRenderer : TemperatureRenderer {

    override fun bake(
        density: Density,
        layoutDirection: LayoutDirection,
        measurer: TextMeasurer,
        spec: NumberSpec,
    ): BakedNumber? {
        if (spec.text.isEmpty() || spec.fontSizePx <= 0f) return null

        fun styleAt(sizePx: Float) = with(density) {
            TextStyle(
                fontSize = sizePx.toSp(),
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.02f).em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
        }

        var fontPx = spec.fontSizePx
        var depthPx = spec.depthPx
        var baseStyle = styleAt(fontPx)
        var solid = measurer.measure(spec.text, baseStyle)

        // La cifra deve stare nella larghezza disponibile, estrusione inclusa:
        // senza questo vincolo un valore a tre cifre uscirebbe dallo schermo.
        val occupied = solid.size.width + (depthPx * 1.8f + 12f) * 2f
        if (spec.maxWidthPx >= 1f && occupied > spec.maxWidthPx) {
            val ratio = spec.maxWidthPx / occupied
            fontPx *= ratio
            depthPx *= ratio
            baseStyle = styleAt(fontPx)
            solid = measurer.measure(spec.text, baseStyle)
        }

        val glyph = Size(solid.size.width.toFloat(), solid.size.height.toFloat())
        if (glyph.width <= 0f || glyph.height <= 0f) return null

        val chamfer = (fontPx * 0.013f).coerceIn(1f, 10f)
        val strokeWidth = (fontPx * 0.008f).coerceIn(0.8f, 5f)

        // Il corpo ha bisogno di tutto lo spazio: estrusione e ombra escono dal
        // glifo. Faccia e iridescenza restano invece attaccate al glifo, quindi
        // ritagliarle stretto risparmia parecchia memoria: tre piani a piena
        // dimensione, moltiplicati per le pagine tenute vive dal pager,
        // costerebbero decine di megabyte.
        val bodyMargin = depthPx * 1.8f + 12f
        val frontMargin = chamfer + strokeWidth + 4f

        val width = ceil(glyph.width + bodyMargin * 2f).toInt().coerceIn(1, MAX_SIDE)
        val height = ceil(glyph.height + bodyMargin * 2f).toInt().coerceIn(1, MAX_SIDE)
        val frontWidth = ceil(glyph.width + frontMargin * 2f).toInt().coerceIn(1, MAX_SIDE)
        val frontHeight = ceil(glyph.height + frontMargin * 2f).toInt().coerceIn(1, MAX_SIDE)

        val radians = spec.angleDeg * PI.toFloat() / 180f
        val direction = Offset(cos(radians), sin(radians))

        val body = render(density, layoutDirection, width, height) {
            paintBody(this, measurer, spec, baseStyle, Offset(bodyMargin, bodyMargin), depthPx, direction, glyph)
        }
        val face = render(density, layoutDirection, frontWidth, frontHeight) {
            paintFace(this, measurer, spec, baseStyle, Offset(frontMargin, frontMargin), chamfer)
        }
        val sheen = render(density, layoutDirection, frontWidth, frontHeight) {
            paintSheen(this, measurer, spec, baseStyle, Offset(frontMargin, frontMargin), chamfer, strokeWidth, glyph)
        }

        val frontOffset = Offset(bodyMargin - frontMargin, bodyMargin - frontMargin)
        return BakedNumber(
            body = NumberLayer(body, Offset.Zero),
            face = NumberLayer(face, frontOffset),
            sheen = NumberLayer(sheen, frontOffset),
            width = width.toFloat(),
            height = height.toFloat(),
            parallaxPx = depthPx,
            sheenAlpha = spec.palette.iridescenceAlpha,
        )
    }

    override fun draw(
        scope: DrawScope,
        baked: BakedNumber,
        center: Offset,
        motion: NumberMotion,
    ) = with(scope) {
        val origin = Offset(
            x = center.x - baked.width / 2f,
            y = center.y - baked.height / 2f,
        ) + motion.push

        // Il corpo si sposta CONTRO l'inclinazione mentre la faccia resta
        // ferma: e' questo che fa leggere una rotazione dell'oggetto invece di
        // una traslazione dell'immagine.
        drawImage(
            image = baked.body.image,
            topLeft = origin + baked.body.offset - motion.tilt * (baked.parallaxPx * 0.35f),
        )
        drawImage(
            image = baked.face.image,
            topLeft = origin + baked.face.offset,
        )
        drawImage(
            image = baked.sheen.image,
            topLeft = origin + baked.sheen.offset +
                motion.tilt * (baked.parallaxPx * 0.18f) +
                Offset(motion.sheenShift, 0f),
            alpha = baked.sheenAlpha,
        )
    }

    private fun render(
        density: Density,
        layoutDirection: LayoutDirection,
        width: Int,
        height: Int,
        block: DrawScope.() -> Unit,
    ): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = Canvas(bitmap),
            size = Size(width.toFloat(), height.toFloat()),
            block = block,
        )
        return bitmap
    }

    private fun paintBody(
        scope: DrawScope,
        measurer: TextMeasurer,
        spec: NumberSpec,
        baseStyle: TextStyle,
        origin: Offset,
        depth: Float,
        direction: Offset,
        glyph: Size,
    ) = with(scope) {
        // Ombra portata morbida, solo sul tema chiaro: su fondo #EFEFF2 e'
        // l'unica cosa che stacca un oggetto bianco dallo sfondo.
        if (spec.palette.dropShadow) {
            val shadowLayout = measurer.measure(
                text = spec.text,
                style = baseStyle.copy(color = Color.Black.copy(alpha = 0.045f)),
            )
            val tail = direction * (depth * 1.02f)
            for (k in 1..SHADOW_STAMPS) {
                val spread = depth * 0.085f * k
                drawText(
                    textLayoutResult = shadowLayout,
                    topLeft = origin + tail + Offset(spread * 0.55f, spread),
                )
            }
        }

        val sideLayout = measurer.measure(
            text = spec.text,
            style = baseStyle.copy(
                // Prevalentemente verticale, non diagonale sull'intero blocco:
                // con una diagonale la cifra di destra risultava molto piu'
                // scura di quella di sinistra, come due materiali diversi.
                // Estremi di specifica, distribuiti. La fascia piu' chiara resta
                // sottile in cima: allargandola il volume arrivava al valore
                // della faccia frontale e lo spigolo anteriore spariva.
                brush = Brush.linearGradient(
                    0.00f to spec.palette.sideNear,
                    0.10f to lerp(spec.palette.sideNear, spec.palette.sideFar, 0.30f),
                    0.55f to lerp(spec.palette.sideNear, spec.palette.sideFar, 0.58f),
                    1.00f to spec.palette.sideFar,
                    start = Offset.Zero,
                    end = Offset(glyph.height * 0.22f, glyph.height),
                ),
            ),
        )

        // Il passo fra una ristampa e l'altra deve restare sotto il pixel,
        // altrimenti sui bordi obliqui si vede la scalinata.
        val steps = ceil(depth / 0.9f).toInt().coerceIn(spec.steps, MAX_STEPS)
        for (i in steps downTo 1) {
            drawText(
                textLayoutResult = sideLayout,
                topLeft = origin + direction * (depth * i.toFloat() / steps),
            )
        }
    }

    private fun paintFace(
        scope: DrawScope,
        measurer: TextMeasurer,
        spec: NumberSpec,
        baseStyle: TextStyle,
        origin: Offset,
        chamfer: Float,
    ) = with(scope) {
        // Il colore sta nello stile, non fra i parametri di disegno: e' lo
        // stesso meccanismo usato dai piani con pennello, e li' funziona.
        // Smusso a 45 gradi rivolto alla luce: sbuca appena oltre la faccia e
        // ne definisce lo spigolo. Va tenuto sotto il valore della faccia.
        drawText(
            textLayoutResult = measurer.measure(
                text = spec.text,
                style = baseStyle.copy(color = spec.palette.chamfer),
            ),
            topLeft = origin + Offset(-chamfer, -chamfer),
        )
        // Faccia frontale: tinta piatta, satinata, il piano piu' chiaro.
        drawText(
            textLayoutResult = measurer.measure(
                text = spec.text,
                style = baseStyle.copy(color = spec.palette.face),
            ),
            topLeft = origin,
        )
    }

    private fun paintSheen(
        scope: DrawScope,
        measurer: TextMeasurer,
        spec: NumberSpec,
        baseStyle: TextStyle,
        origin: Offset,
        chamfer: Float,
        strokeWidth: Float,
        glyph: Size,
    ) = with(scope) {
        // Iridescenza: solo sugli smussi e negli angoli interni. E' un filo di
        // contorno sfalsato, non un riempimento. L'alpha viene applicata in
        // composizione, cosi' il movimento potra' modularla senza ricuocere.
        val iridescent = measurer.measure(
            text = spec.text,
            style = baseStyle.copy(
                brush = Brush.linearGradient(
                    colors = spec.palette.iridescence,
                    start = Offset.Zero,
                    end = Offset(glyph.width, glyph.height),
                ),
                drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round),
            ),
        )
        drawText(
            textLayoutResult = iridescent,
            topLeft = origin + Offset(-chamfer * 0.7f, -chamfer * 0.7f),
        )
    }

    private companion object {
        const val SHADOW_STAMPS = 10
        const val MAX_STEPS = 420
        const val MAX_SIDE = 4096
    }
}
