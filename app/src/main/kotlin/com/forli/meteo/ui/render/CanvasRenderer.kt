package com.forli.meteo.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Disegna la cifra come un solido: facce laterali e smussi con orientamento
 * noto, illuminati da una sola luce direzionale, piu' la faccia frontale.
 *
 * La gerarchia dei valori resta quella della specifica: la faccia frontale e'
 * il piano piu' chiaro, gli smussi stanno sotto perche' inclinati ricevono
 * meno luce, le facce laterali scendono lungo la rampa.
 */
class CanvasRenderer : TemperatureRenderer {

    private class Prepared(
        val geometry: GlyphGeometry,
        val depth: Float,
        val chamfer: Float,
        val palette: NumberPalette,
    ) : PreparedNumber {
        // L'estrusione e' obliqua: proietta cos e sin della propria lunghezza,
        // non la lunghezza intera. Sommarla tutta su entrambi gli assi faceva
        // credere l'oggetto piu' largo di quanto e', e lo scentrava.
        override val width: Float
            get() = geometry.width + depth * cos(EXTRUSION_REST * PI.toFloat() / 180f)
        override val height: Float
            get() = geometry.height + depth * sin(EXTRUSION_REST * PI.toFloat() / 180f)

        /**
         * Le superfici per orientamento, quantizzate. I vertici non cambiano
         * con la luce: cambia la fascia a cui ogni faccia appartiene. Tenere
         * qualche orientamento pronto rende la rotazione fluida senza
         * ricostruire i tracciati a ogni fotogramma.
         */
        private val cache = object : LinkedHashMap<Int, GlyphGeometry.Shading>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Int, GlyphGeometry.Shading>,
            ) = size > ORIENTATION_CACHE
        }

        fun shadingAt(orientationDeg: Float): GlyphGeometry.Shading {
            val key = (orientationDeg / ORIENTATION_STEP).roundToInt()
            cache[key]?.let { return it }

            val angle = key * ORIENTATION_STEP
            // L'estrusione ruota molto meno della luce: e' la luce a fare la
            // parte del leone nel raccontare la rotazione, la silhouette
            // accompagna appena.
            val extrusionDeg = EXTRUSION_REST +
                (angle - NumberMotion.REST_ORIENTATION) * EXTRUSION_FOLLOW
            val radians = extrusionDeg * PI.toFloat() / 180f

            val shading = geometry.shade(
                depth = depth,
                chamfer = chamfer,
                extrusion = Offset(cos(radians), sin(radians)),
                light = GlyphGeometry.Light.atAngle(angle),
            )
            cache[key] = shading
            return shading
        }
    }

    override fun prepare(spec: NumberSpec): PreparedNumber? {
        if (spec.text.isEmpty() || spec.fontSizePx <= 0f) return null

        var size = spec.fontSizePx
        var depth = spec.depthPx
        var geometry = GlyphGeometry.of(
            text = spec.text,
            typeface = spec.typeface,
            sizePx = size,
            letterSpacingEm = spec.letterSpacingEm,
            step = sampleStep(size),
        ) ?: return null

        // La cifra deve stare nella larghezza disponibile, estrusione inclusa.
        val occupied = geometry.width + depth + MARGIN * 2f
        if (spec.maxWidthPx >= 1f && occupied > spec.maxWidthPx) {
            val ratio = spec.maxWidthPx / occupied
            size *= ratio
            depth *= ratio
            geometry = GlyphGeometry.of(
                text = spec.text,
                typeface = spec.typeface,
                sizePx = size,
                letterSpacingEm = spec.letterSpacingEm,
                step = sampleStep(size),
            ) ?: return null
        }

        return Prepared(
            geometry = geometry,
            depth = depth,
            chamfer = (size * 0.014f).coerceIn(1.5f, 16f),
            palette = spec.palette,
        )
    }

    override fun draw(
        scope: DrawScope,
        prepared: PreparedNumber,
        center: Offset,
        motion: NumberMotion,
    ) = with(scope) {
        val model = prepared as? Prepared ?: return@with
        val shading = model.shadingAt(motion.orientationDeg)
        val palette = model.palette

        val left = center.x - model.width / 2f
        val top = center.y - model.height / 2f

        translate(left = left, top = top) {
            // Ombra portata, solo sul tema chiaro: su fondo quasi bianco e'
            // l'unica cosa che stacca un oggetto bianco dallo sfondo.
            if (palette.dropShadow) {
                translate(left = model.depth * 0.55f, top = model.depth * 0.9f) {
                    drawPath(shading.front, Color.Black.copy(alpha = 0.10f))
                }
            }

            // Facce laterali e smussi: ognuno con il tono della propria
            // esposizione. E' qui che l'oggetto smette di sembrare piatto.
            shading.facets.forEach { facet ->
                drawPath(facet.path, colorOf(facet, palette))
            }

            // Faccia frontale: tinta piatta, il piano piu' chiaro.
            drawPath(shading.front, palette.face)

            // Iridescenza dove la luce sfiora lo smusso. Non e' decorazione
            // sparsa sul contorno: e' la fascia di incidenza radente, quella
            // in cui un materiale reale scompone la luce.
            shading.facets.forEach { facet ->
                if (!facet.bevel) return@forEach
                val grazing = grazingWeight(facet.lambert)
                if (grazing <= 0.01f) return@forEach
                drawPath(
                    path = facet.path,
                    brush = Brush.linearGradient(
                        colors = palette.iridescence,
                        start = Offset.Zero,
                        end = Offset(model.width, model.height),
                    ),
                    alpha = palette.iridescenceAlpha * grazing,
                )
            }
        }
    }

    private fun colorOf(facet: GlyphGeometry.Facet, palette: NumberPalette): Color =
        if (facet.bevel) {
            lerp(
                lerp(palette.chamfer, palette.sideFar, 0.55f),
                palette.chamfer,
                facet.lambert,
            )
        } else {
            lerp(
                palette.sideFar,
                palette.sideNear,
                AMBIENT + (1f - AMBIENT) * facet.lambert,
            )
        }

    /** Campana centrata sull'incidenza radente. */
    private fun grazingWeight(lambert: Float): Float {
        val d = (lambert - GRAZING_CENTRE) / GRAZING_WIDTH
        return (1f - d * d).coerceAtLeast(0f)
    }

    private fun sampleStep(sizePx: Float): Float = (sizePx / 260f).coerceIn(0.9f, 3f)

    private companion object {
        const val MARGIN = 12f
        const val AMBIENT = 0.16f
        const val EXTRUSION_REST = 62f
        const val EXTRUSION_FOLLOW = 0.30f
        const val ORIENTATION_STEP = 3f
        const val ORIENTATION_CACHE = 24
        const val GRAZING_CENTRE = 0.42f
        const val GRAZING_WIDTH = 0.30f
    }
}
