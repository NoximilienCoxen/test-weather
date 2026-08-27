package com.forli.meteo.ui.render3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import com.forli.meteo.ui.render.NumberMotion
import com.forli.meteo.ui.render.NumberPalette
import com.forli.meteo.ui.render.NumberSpec
import com.forli.meteo.ui.render.PreparedNumber
import com.forli.meteo.ui.render.TemperatureRenderer
import kotlin.math.max

/**
 * Disegna la cifra come un solido guardato da una camera prospettica.
 *
 * La gerarchia dei valori resta quella della specifica - base frontale il piano
 * piu' chiaro, smussi sotto perche' inclinati prendono meno luce, pareti giu'
 * per la rampa - ma ora ogni tono e' calcolato dall'orientamento reale della
 * superficie, non da una direzione di estrusione decisa a tavolino.
 */
class PrismRenderer : TemperatureRenderer {

    private class Prepared(
        val prism: TextPrism,
        val depth: Float,
        val chamfer: Float,
    ) : PreparedNumber {
        override val width: Float get() = prism.width
        override val height: Float get() = prism.height
    }

    /**
     * Pennello dei triangoli, costruito una volta sola.
     *
     * Senza antialiasing, e non e' una rinuncia: i triangoli si toccano lungo
     * spigoli condivisi, e sfumare il bordo di ciascuno per conto proprio
     * aprirebbe una fessura chiara fra l'uno e l'altro. Il contorno esterno
     * dell'oggetto resta netto perche' lo disegna la base frontale, che e' una
     * sagoma vera e sfumata.
     */
    private val meshPaint = android.graphics.Paint().apply {
        isAntiAlias = false
        color = android.graphics.Color.WHITE
    }

    /** La base frontale: qui l'antialiasing serve, e' la sagoma dell'oggetto. */
    private val facePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    /**
     * L'ombra invece no.
     *
     * Sfumare il bordo di una macchia al sedici per cento di nero non si vede,
     * e costa: sotto una matrice prospettica una sagoma sfumata non passa dalla
     * strada veloce della scheda grafica ma viene rasterizzata a mano. Misurato
     * su questa cifra, le due copie dell'ombra da sole pesavano quanto tutto il
     * resto del disegno.
     */
    private val shadowPaint = android.graphics.Paint()

    /** Una sola, riusata: vedi [Camera.aim]. */
    private val camera = Camera()

    /**
     * L'inchiostro dipende solo dalla tavolozza, che cambia quando cambia l'ora
     * - qualche volta al minuto - non a ogni fotogramma. Calcolarlo dentro
     * `draw` costava due liste e un vettore di interi sessanta volte al secondo
     * per un risultato identico a se stesso.
     */
    private var inkPalette: NumberPalette? = null
    private var ink: TextPrism.Ink? = null

    override fun prepare(spec: NumberSpec): PreparedNumber? {
        if (spec.text.isEmpty() || spec.fontSizePx <= 0f) return null

        var size = spec.fontSizePx
        var depth = spec.depthPx
        var prism = TextPrism.of(
            text = spec.text,
            typeface = spec.typeface,
            sizePx = size,
            letterSpacingEm = spec.letterSpacingEm,
            step = sampleStep(size),
        ) ?: return null

        // Girata, la meta' vicina dell'oggetto ingrandisce: se la cifra
        // occupasse tutta la larghezza da ferma, ruotandola uscirebbe di scena.
        // Il margine e' quel guadagno prospettico, tenuto da parte.
        val occupied = prism.width * SWING_ALLOWANCE + MARGIN * 2f
        if (spec.maxWidthPx >= 1f && occupied > spec.maxWidthPx) {
            val ratio = spec.maxWidthPx / occupied
            size *= ratio
            depth *= ratio
            prism = TextPrism.of(
                text = spec.text,
                typeface = spec.typeface,
                sizePx = size,
                letterSpacingEm = spec.letterSpacingEm,
                step = sampleStep(size),
            ) ?: return null
        }

        return Prepared(
            prism = prism,
            depth = depth,
            chamfer = (size * 0.016f).coerceIn(1.5f, 18f),
        )
    }

    override fun draw(
        scope: DrawScope,
        prepared: PreparedNumber,
        center: Offset,
        palette: NumberPalette,
        motion: NumberMotion,
        silhouette: Skyline?,
    ) = with(scope) {
        val model = prepared as? Prepared ?: return@with
        val prism = model.prism
        silhouette?.reset(size.width)

        val camera = camera.aim(
            yawDeg = motion.yawDeg,
            pitchDeg = motion.pitchDeg,
            distance = max(model.width, model.height) * EYE_DISTANCE,
            origin = center,
        )
        val ink = inkFor(palette)
        val reversed = prism.reversed(camera)

        // Le ombre tutte per prime, e tutte insieme. Sul fondo grigio unico
        // l'ombra portata non e' piu' un vezzo del tema chiaro: e' cio' che
        // stacca un oggetto bianco dal fondo quando la barra scorre verso le ore
        // di luce. Ma e' un disegno, non un fenomeno: deve stare sul fondo, non
        // sulla faccia del carattere accanto.
        //
        // Due copie sempre piu' lontane e sempre piu' tenui, non una sola: una
        // copia sola col bordo netto non si legge come ombra ma come un secondo
        // oggetto scuro dietro il primo. Sfocarla davvero non si puo' a buon
        // mercato su tela accelerata, ma due gradini bastano.
        if (palette.shadowAlpha > 0.001f) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for (index in 0 until prism.partCount) {
                    if (!prism.capTransform(index, camera, model.depth)) continue
                    val outline = prism.outlineOf(index)
                    for (layer in SHADOW_STEPS.indices) {
                        val reach = SHADOW_STEPS[layer]
                        shadowPaint.color = Color.Black
                            .copy(alpha = palette.shadowAlpha * SHADOW_WEIGHTS[layer])
                            .toArgb()
                        native.save()
                        native.translate(
                            model.depth * 0.24f * reach,
                            model.depth * 0.42f * reach,
                        )
                        native.concat(prism.shadowTransform)
                        native.drawPath(outline, shadowPaint)
                        native.restore()
                    }
                }
            }
        }

        // Dal carattere piu' lontano al piu' vicino. Senza questo ordine, uno
        // che sta dietro puo' stamparsi sopra a chi gli sta davanti.
        for (step in 0 until prism.partCount) {
            val index = if (reversed) prism.partCount - 1 - step else step
            val surfaces = prism.shape(
                index = index,
                camera = camera,
                light = Light.Standard,
                depth = model.depth,
                chamfer = model.chamfer,
                ink = ink,
            )
            silhouette?.let { prism.addSilhouette(index, it) }

            // Pareti e smussi in una sola chiamata: il colore viaggia sui
            // vertici e la scheda grafica lo interpola. E' qui che l'oggetto
            // smette di sembrare piatto, e da qui in poi non costa quasi nulla.
            if (surfaces.vertexValues > 0) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawVertices(
                        android.graphics.Canvas.VertexMode.TRIANGLES,
                        surfaces.vertexValues,
                        surfaces.verts, 0,
                        null, 0,
                        surfaces.colours, 0,
                        null, 0, 0,
                        meshPaint,
                    )
                }
            }

            // La base rivolta all'occhio. Il suo tono dipende da quanto e'
            // girata: di fronte prende la luce in pieno, di taglio la perde.
            // Senza questo la cifra ruoterebbe restando bianca, e sarebbe la
            // parte immobile di un oggetto in movimento.
            if (surfaces.capVisible) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    facePaint.color = lerp(
                        lerp(palette.sideNear, palette.sideFar, FACE_SHADE),
                        palette.face,
                        faceTone(surfaces),
                    ).toArgb()
                    native.save()
                    native.concat(surfaces.matrix)
                    native.drawPath(surfaces.outline, facePaint)
                    native.restore()
                }
            }
        }
    }

    /**
     * Da quanto e' esposta la base a quanto e' chiara.
     *
     * Non una proporzione diretta, e nemmeno l'intera scala dei grigi. Ferma, la
     * faccia deve leggersi bianca e non grigio chiaro, altrimenti l'oggetto
     * sembra sporco prima ancora di essere toccato. Girata deve scurirsi quanto
     * basta a dire che ha girato, ma restare plastica bianca in ombra: portandola
     * fino al tono della parete lontana diventava ardesia, e a meta' rotazione il
     * materiale cambiava identita'. Il contrasto della luce lo raccontano le
     * pareti, che possono permetterselo.
     */
    private fun faceTone(surfaces: TextPrism.Surfaces): Float =
        ((surfaces.faceLambert - 0.30f) / 0.55f).coerceIn(0f, 1f)

    private fun inkFor(palette: NumberPalette): TextPrism.Ink {
        val cached = ink
        if (cached != null && inkPalette == palette) return cached
        val built = buildInk(palette)
        inkPalette = palette
        ink = built
        return built
    }

    private fun buildInk(palette: NumberPalette) = TextPrism.Ink(
        wallFar = palette.sideFar.toArgb(),
        wallNear = palette.sideNear.toArgb(),
        bevelDark = lerp(palette.chamfer, palette.sideFar, 0.55f).toArgb(),
        bevelLight = palette.chamfer.toArgb(),
        ambient = AMBIENT,
        // Le fasce trasparenti dell'iridescenza qui non servono: la quantita'
        // e' gia' governata dalla campana dell'incidenza radente, e una tinta
        // trasparente mescolata a un colore opaco lo schiarirebbe soltanto.
        iridescence = palette.iridescence
            .filter { it.alpha > 0.5f }
            .map { it.toArgb() }
            .toIntArray(),
        iridescenceAlpha = palette.iridescenceAlpha,
    )

    /**
     * Le pareti sono strette e il loro bordo esterno si nota poco: campionare
     * fitto moltiplica i triangoli senza aggiungere fedelta', perche' la sagoma
     * che conta e' quella della base, che resta il tracciato vero del font.
     */
    private fun sampleStep(sizePx: Float): Float = (sizePx / 60f).coerceIn(3f, 19f)

    private companion object {
        const val MARGIN = 10f
        const val SWING_ALLOWANCE = 1.10f
        const val AMBIENT = 0.14f

        /** Quanto scende la faccia quando gira via dalla luce: un passo, non un salto. */
        const val FACE_SHADE = 0.22f

        /**
         * Distanza dell'occhio in multipli della dimensione dell'oggetto. Sotto
         * il doppio la prospettiva diventa da grandangolo e la cifra si deforma;
         * oltre il quadruplo la rotazione torna a somigliare a un'estrusione
         * ortografica, che e' esattamente il difetto da cui si viene.
         */
        const val EYE_DISTANCE = 2.7f

        /**
         * Quanto si allontana ogni gradino dell'ombra, e quanto pesa.
         *
         * Due e non tre: ogni gradino e' una copia intera della sagoma da
         * riempire, ed e' la superficie piu' grande che il disegno tocchi. Il
         * terzo aggiungeva pochissimo a vedersi e parecchio a costare.
         */
        val SHADOW_STEPS = floatArrayOf(0.40f, 1f)
        val SHADOW_WEIGHTS = floatArrayOf(0.60f, 0.40f)
    }
}
