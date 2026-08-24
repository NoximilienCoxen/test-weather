package com.forli.meteo.ui.render

import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Il contorno di un testo trasformato in facce con orientamento noto.
 *
 * E' la differenza fra questa implementazione e quella a ristampe: li' nessun
 * punto della superficie sapeva com'era orientato, quindi nessuno poteva
 * essere illuminato, e il risultato leggeva come strati piatti sovrapposti.
 * Qui ogni faccia ha una normale, e da una normale nasce un tono.
 */
class GlyphGeometry private constructor(
    /** Quattro coordinate per spigolo: inizio e fine. */
    private val edges: FloatArray,
    /** Normale uscente per spigolo, gia' normalizzata. */
    private val normals: FloatArray,
    private val edgeCount: Int,
    /** Contorni campionati, per costruire la faccia frontale rientrata. */
    private val contours: List<FloatArray>,
    private val contourNormals: List<FloatArray>,
    val width: Float,
    val height: Float,
) {

    /** Una superficie omogenea: stessa tinta perche' stesso orientamento. */
    class Facet(val path: Path, val lambert: Float, val bevel: Boolean)

    /**
     * Costruisce le superfici visibili per un dato orientamento.
     *
     * Le facce con la stessa esposizione alla luce finiscono nello stesso
     * tracciato: cosi' il disegno costa una decina di chiamate invece di una
     * per faccia, e il raggruppamento non si vede perche' facce con la stessa
     * normale hanno davvero lo stesso tono.
     */
    fun shade(
        depth: Float,
        chamfer: Float,
        extrusion: Offset,
        light: Light,
        bands: Int = BANDS,
    ): Shading {
        val sideBuckets = Array(bands) { Path() }
        val bevelBuckets = Array(bands) { Path() }
        val sideUsed = BooleanArray(bands)
        val bevelUsed = BooleanArray(bands)

        val ex = extrusion.x
        val ey = extrusion.y
        val eLen = hypot(ex, ey).takeIf { it > 1e-4f } ?: 1f
        val ux = ex / eLen
        val uy = ey / eLen

        for (i in 0 until edgeCount) {
            val nx = normals[i * 2]
            val ny = normals[i * 2 + 1]

            // Prova di silhouette: una faccia laterale si vede solo se guarda
            // nella direzione in cui il corpo si allontana. Senza questa prova
            // si disegnerebbero anche le facce nascoste dietro il glifo.
            if (nx * ux + ny * uy <= 0f) continue

            val px = edges[i * 4]
            val py = edges[i * 4 + 1]
            val qx = edges[i * 4 + 2]
            val qy = edges[i * 4 + 3]

            // Lo smusso occupa la prima parte della profondita', la faccia
            // laterale il resto.
            val bx = ux * chamfer
            val by = uy * chamfer
            val dx = ux * depth
            val dy = uy * depth

            val insetPx = px - nx * chamfer
            val insetPy = py - ny * chamfer
            val insetQx = qx - nx * chamfer
            val insetQy = qy - ny * chamfer

            val bevelLambert = light.lambert(nx, ny, -1f)
            val sideLambert = light.lambert(nx, ny, 0f)

            val bevelBand = band(bevelLambert, bands)
            quad(
                bevelBuckets[bevelBand],
                insetPx, insetPy, insetQx, insetQy,
                qx + bx, qy + by, px + bx, py + by,
            )
            bevelUsed[bevelBand] = true

            val sideBand = band(sideLambert, bands)
            quad(
                sideBuckets[sideBand],
                px + bx, py + by, qx + bx, qy + by,
                qx + dx, qy + dy, px + dx, py + dy,
            )
            sideUsed[sideBand] = true
        }

        val facets = ArrayList<Facet>(bands * 2)
        // Le facce laterali vanno sotto gli smussi, e gli smussi sotto la
        // faccia frontale: e' l'ordine in cui si sovrappongono nella realta'.
        for (b in 0 until bands) {
            if (sideUsed[b]) facets += Facet(sideBuckets[b], bandCentre(b, bands), bevel = false)
        }
        for (b in 0 until bands) {
            if (bevelUsed[b]) facets += Facet(bevelBuckets[b], bandCentre(b, bands), bevel = true)
        }

        return Shading(facets, frontFace(chamfer))
    }

    /** La faccia frontale e' il contorno rientrato di quanto misura lo smusso. */
    private fun frontFace(chamfer: Float): Path {
        val path = Path()
        contours.forEachIndexed { index, points ->
            val normals = contourNormals[index]
            val count = points.size / 2
            if (count < 3) return@forEachIndexed
            for (k in 0 until count) {
                val x = points[k * 2] - normals[k * 2] * chamfer
                val y = points[k * 2 + 1] - normals[k * 2 + 1] * chamfer
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        return path
    }

    class Shading(val facets: List<Facet>, val front: Path)

    /** Luce direzionale, in coordinate schermo con z che entra nello schermo. */
    class Light(x: Float, y: Float, z: Float) {
        private val lx: Float
        private val ly: Float
        private val lz: Float

        init {
            val len = sqrt(x * x + y * y + z * z).takeIf { it > 1e-4f } ?: 1f
            lx = x / len
            ly = y / len
            lz = z / len
        }

        fun lambert(nx: Float, ny: Float, nz: Float): Float {
            val len = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 1e-4f } ?: 1f
            return max(0f, (nx * lx + ny * ly + nz * lz) / len)
        }

        companion object {
            /** Ruota la luce attorno all'asse di vista, mantenendo l'inclinazione. */
            fun atAngle(degrees: Float, towardViewer: Float = -0.45f): Light {
                val r = degrees * Math.PI.toFloat() / 180f
                return Light(cos(r), sin(r), towardViewer)
            }
        }
    }

    companion object {
        const val BANDS = 12

        private fun band(lambert: Float, bands: Int): Int =
            (lambert * (bands - 1)).toInt().coerceIn(0, bands - 1)

        private fun bandCentre(band: Int, bands: Int): Float =
            band.toFloat() / (bands - 1)

        private fun quad(
            path: Path,
            ax: Float, ay: Float, bx: Float, by: Float,
            cx: Float, cy: Float, dx: Float, dy: Float,
        ) {
            path.moveTo(ax, ay)
            path.lineTo(bx, by)
            path.lineTo(cx, cy)
            path.lineTo(dx, dy)
            path.close()
        }

        /**
         * Estrae il contorno del testo e lo riduce a spigoli con normale.
         *
         * @param step distanza fra due campioni lungo il contorno. Piu' fitto
         *   significa curve piu' fedeli e piu' facce: sotto il paio di pixel
         *   la differenza non si vede piu'.
         */
        fun of(
            text: String,
            typeface: Typeface,
            sizePx: Float,
            letterSpacingEm: Float = 0f,
            step: Float = 2f,
        ): GlyphGeometry? {
            if (text.isEmpty() || sizePx <= 0f) return null

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = sizePx
                letterSpacing = letterSpacingEm
            }
            val outline = android.graphics.Path()
            paint.getTextPath(text, 0, text.length, 0f, 0f, outline)

            val bounds = RectF()
            outline.computeBounds(bounds, true)
            if (bounds.width() <= 0f || bounds.height() <= 0f) return null
            outline.offset(-bounds.left, -bounds.top)

            val sampled = ArrayList<FloatArray>()
            val measure = PathMeasure(outline, true)
            val position = FloatArray(2)
            do {
                val length = measure.length
                if (length > step) {
                    val count = max(3, (length / step).toInt())
                    val points = FloatArray(count * 2)
                    for (k in 0 until count) {
                        measure.getPosTan(length * k / count, position, null)
                        points[k * 2] = position[0]
                        points[k * 2 + 1] = position[1]
                    }
                    sampled += points
                }
            } while (measure.nextContour())

            if (sampled.isEmpty()) return null

            // Il verso di percorrenza dei contorni dipende dal font. Invece di
            // assumerlo, lo deduco: sul contorno piu' grande la normale deve
            // puntare lontano dal centro. Se punta dentro, inverto la
            // convenzione per tutti.
            val largest = sampled.maxBy { abs(signedArea(it)) }
            val flip = pointsInward(largest)

            val allEdges = ArrayList<Float>()
            val allNormals = ArrayList<Float>()
            val perContourNormals = ArrayList<FloatArray>()

            for (points in sampled) {
                val count = points.size / 2
                val positive = signedArea(points) > 0f
                val contourNormals = FloatArray(count * 2)
                for (k in 0 until count) {
                    val n = (k + 1) % count
                    val px = points[k * 2]
                    val py = points[k * 2 + 1]
                    val qx = points[n * 2]
                    val qy = points[n * 2 + 1]
                    val ex = qx - px
                    val ey = qy - py
                    val len = hypot(ex, ey)
                    if (len < 1e-4f) {
                        contourNormals[k * 2] = 0f
                        contourNormals[k * 2 + 1] = 0f
                        continue
                    }
                    var nx = ey / len
                    var ny = -ex / len
                    if (positive == flip) {
                        nx = -nx
                        ny = -ny
                    }
                    contourNormals[k * 2] = nx
                    contourNormals[k * 2 + 1] = ny

                    allEdges += px; allEdges += py; allEdges += qx; allEdges += qy
                    allNormals += nx; allNormals += ny
                }
                perContourNormals += contourNormals
            }

            return GlyphGeometry(
                edges = allEdges.toFloatArray(),
                normals = allNormals.toFloatArray(),
                edgeCount = allNormals.size / 2,
                contours = sampled,
                contourNormals = perContourNormals,
                width = bounds.width(),
                height = bounds.height(),
            )
        }

        private fun signedArea(points: FloatArray): Float {
            var area = 0f
            val count = points.size / 2
            for (k in 0 until count) {
                val n = (k + 1) % count
                area += points[k * 2] * points[n * 2 + 1] - points[n * 2] * points[k * 2 + 1]
            }
            return area / 2f
        }

        /** Somma di normale per raggio dal centro: se negativa le normali guardano dentro. */
        private fun pointsInward(points: FloatArray): Boolean {
            val count = points.size / 2
            var cx = 0f
            var cy = 0f
            for (k in 0 until count) {
                cx += points[k * 2]
                cy += points[k * 2 + 1]
            }
            cx /= count
            cy /= count

            val positive = signedArea(points) > 0f
            var sum = 0f
            for (k in 0 until count) {
                val n = (k + 1) % count
                val px = points[k * 2]
                val py = points[k * 2 + 1]
                val ex = points[n * 2] - px
                val ey = points[n * 2 + 1] - py
                val len = hypot(ex, ey)
                if (len < 1e-4f) continue
                var nx = ey / len
                var ny = -ex / len
                if (!positive) {
                    nx = -nx
                    ny = -ny
                }
                sum += nx * (px - cx) + ny * (py - cy)
            }
            return sum < 0f
        }
    }
}
