package com.forli.meteo.ui.render3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import com.forli.meteo.ui.render.NumberMotion
import com.forli.meteo.ui.render.NumberPalette
import com.forli.meteo.ui.render.NumberSpec
import com.forli.meteo.ui.render.PreparedNumber
import com.forli.meteo.ui.render.TemperatureRenderer
import kotlin.math.hypot
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
        /** La scritta da cui viene: serve a sapere quali cifre sono cambiate. */
        val text: String,
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

    /**
     * Le geometrie gia' estratte, dalla piu' usata di recente alla piu' vecchia.
     *
     * Estrarre una cifra vuol dire campionare tutti i contorni del font e
     * ricavarne spigoli e normali, che il documento di passaggio chiama "la
     * parte cara". Scorrendo la barra delle ore la scritta cambia a ogni ora e
     * la si rifaceva ogni volta - un lavoro intero dentro un fotogramma solo,
     * che e' il modo in cui uno scorrimento diventa a scatti. I valori distinti
     * in una giornata sono una decina, quindi tenerli costa poco e li si trova
     * gia' pronti.
     *
     * Dodici e non di piu': ogni prisma si porta dietro i propri buffer
     * riusabili, e sono decine di kilobyte a testa.
     */
    private val cache = object : LinkedHashMap<NumberSpec, Prepared>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<NumberSpec, Prepared>,
        ): Boolean = size > CACHE_SIZE
    }

    override fun prepare(spec: NumberSpec): PreparedNumber? {
        if (spec.text.isEmpty() || spec.fontSizePx <= 0f) return null
        cache[spec]?.let { return it }

        var size = spec.fontSizePx
        var depth = spec.depthPx

        // Quanto occupera', chiesto **prima** di costruirla. Girata, la meta'
        // vicina dell'oggetto ingrandisce: se la cifra occupasse tutta la
        // larghezza da ferma, ruotandola uscirebbe di scena. Il margine e' quel
        // guadagno prospettico, tenuto da parte.
        //
        // Prima la cifra si costruiva, si misurava, e se non ci stava **si
        // ricostruiva da capo**: due campionamenti di tutti i contorni per
        // sapere un numero che si ottiene con tre chiamate a `getTextPath`.
        val width = TextPrism.widthOf(
            text = spec.text,
            typeface = spec.typeface,
            sizePx = size,
            letterSpacingEm = spec.letterSpacingEm,
            smallTail = spec.smallTail,
        )
        val occupied = width * SWING_ALLOWANCE + MARGIN * 2f
        if (spec.maxWidthPx >= 1f && occupied > spec.maxWidthPx) {
            val ratio = spec.maxWidthPx / occupied
            size *= ratio
            depth *= ratio
        }

        val prism = TextPrism.of(
            text = spec.text,
            typeface = spec.typeface,
            sizePx = size,
            letterSpacingEm = spec.letterSpacingEm,
            step = sampleStep(size),
            smallTail = spec.smallTail,
        ) ?: return null

        return Prepared(
            prism = prism,
            depth = depth,
            chamfer = (size * 0.016f).coerceIn(1.5f, 18f),
            text = spec.text,
        ).also { cache[spec] = it }
    }

    override fun draw(
        scope: DrawScope,
        prepared: PreparedNumber,
        center: Offset,
        palette: NumberPalette,
        motion: NumberMotion,
        silhouette: Skyline?,
        previous: PreparedNumber?,
        progress: Float,
    ) = with(scope) {
        val model = prepared as? Prepared ?: return@with
        silhouette?.reset(size.width)

        val camera = Camera(
            yawDeg = motion.yawDeg,
            pitchDeg = motion.pitchDeg,
            distance = max(model.width, model.height) * EYE_DISTANCE,
            origin = center,
        )
        val ink = inkFor(palette)

        val from = (previous as? Prepared)
            ?.takeIf { progress < 1f && it !== model }
        if (from == null) {
            drawNumber(model, camera, ink, palette, silhouette, yOffset = 0f, only = null, shadow = true)
            return@with
        }

        // Quali colonne cambiano davvero. Ventotto che diventa ventinove deve
        // muovere il nove: far rotolare anche il due sarebbe un'animazione
        // vistosa al posto di un'informazione.
        //
        // Le colonne si corrispondono solo se i due numeri sono lunghi uguale e
        // se ogni carattere ha prodotto un carattere - nove che diventa dieci
        // non ha colonne da confrontare. Li' rotola tutto insieme, che e' anche
        // quello che fa un contachilometri quando gli cresce una cifra.
        val aligned = from.text.length == model.text.length &&
            from.prism.partCount == from.text.length &&
            model.prism.partCount == model.text.length
        val still = if (aligned) {
            BooleanArray(model.text.length) { i -> from.text[i] == model.text[i] }
        } else {
            null
        }

        // In su quando il valore sale, in giu' quando scende: un contachilometri
        // che gira sempre nello stesso verso mentre il numero cala racconta il
        // contrario di quello che succede.
        val span = model.height * ROLL_SPAN
        val forward = numberIn(model.text) >= numberIn(from.text)
        val way = if (forward) -1f else 1f
        val exit = way * span * progress
        val enter = -way * span * (1f - progress)

        // I caratteri fermi per primi, con la loro ombra: sono l'oggetto, non
        // la transizione, e non hanno motivo di stare dentro un ritaglio.
        if (still != null) {
            drawNumber(model, camera, ink, palette, silhouette, 0f, still, shadow = true)
        }

        // I mobili dentro una fascia alta quanto la cifra, se no si vedrebbero
        // sfilare sopra la nuvola e sotto la scritta della condizione.
        //
        // Senza ombra portata, e non per pigrizia: e' la voce piu' cara del
        // disegno (trappola #18), qui andrebbe raddoppiata perche' i corpi sono
        // due, e il ritaglio la taglierebbe di netto proprio dove sfuma. Per due
        // decimi di secondo non manca a nessuno, e torna appena il numero si
        // ferma.
        val moving = still?.let { flip(it) }
        val half = model.height * ROLL_CLIP
        clipRect(top = center.y - half, bottom = center.y + half) {
            drawNumber(from, camera, ink, palette, null, exit, moving, shadow = false)
            drawNumber(model, camera, ink, palette, silhouette, enter, moving, shadow = false)
        }
    }

    /**
     * Un numero intero, a un certo scostamento verticale.
     *
     * @param only quali caratteri disegnare, o nullo per tutti.
     * @param shadow se disegnarne l'ombra portata.
     */
    private fun DrawScope.drawNumber(
        model: Prepared,
        camera: Camera,
        ink: TextPrism.Ink,
        palette: NumberPalette,
        silhouette: Skyline?,
        yOffset: Float,
        only: BooleanArray?,
        shadow: Boolean,
    ) {
        val prism = model.prism
        val reversed = prism.reversed(camera)

        fun wanted(index: Int): Boolean = only == null || index >= only.size || only[index]

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
        //
        // Quanto la cifra e' aperta verso l'occhio. Di taglio non proietta piu'
        // niente, e la matrice che ce la porterebbe e' fatta di numeri che
        // divergono: l'ombra si spegne prima di arrivarci, invece di sfrangiarsi
        // in strisce lunghe mezzo schermo - che era il difetto che si vedeva
        // spuntare da sotto la cifra ogni volta che passava di profilo.
        val openness = prism.openness(camera)
        val castAlpha = palette.shadowAlpha *
            ((openness - SHADOW_FADE_FROM) / (SHADOW_FADE_TO - SHADOW_FADE_FROM)).coerceIn(0f, 1f)

        if (shadow && castAlpha > 0.001f) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for (index in 0 until prism.partCount) {
                    if (!wanted(index)) continue
                    if (!prism.prepareShadow(index, camera, yOffset)) continue
                    val outline = prism.outlineOf(index)
                    for (layer in SHADOW_STEPS.indices) {
                        val reach = SHADOW_STEPS[layer]
                        shadowPaint.color = Color.Black
                            .copy(alpha = castAlpha * SHADOW_WEIGHTS[layer])
                            .toArgb()
                        native.save()
                        val away = model.depth * SHADOW_REACH * reach
                        native.translate(
                            SHADOW_DIRECTION.x * away,
                            SHADOW_DIRECTION.y * away,
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
            if (!wanted(index)) continue
            val surfaces = prism.shape(
                index = index,
                camera = camera,
                light = Light.Standard,
                depth = model.depth,
                chamfer = model.chamfer,
                ink = ink,
                yOffset = yOffset,
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

    /** L'opposto di una maschera: i fermi diventano i mobili. */
    private fun flip(mask: BooleanArray) = BooleanArray(mask.size) { !mask[it] }

    /** Il valore dentro una scritta, per sapere da che parte far girare le cifre. */
    private fun numberIn(text: String): Int =
        text.filter { it.isDigit() || it == '-' }.toIntOrNull() ?: 0

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

    private fun inkFor(palette: NumberPalette) = TextPrism.Ink(
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
        /** Quante geometrie si tengono pronte. Vedi il commento sulla cache. */
        const val CACHE_SIZE = 12

        /**
         * Quanto viaggia una cifra che rotola, in altezze del numero.
         *
         * Deve bastare a portarla **fuori** dalla fascia ritagliata: la meta'
         * del ritaglio piu' la meta' della cifra, con un margine per la
         * prospettiva che ingrandisce la meta' vicina. Piu' corta e a fine
         * corsa si vedrebbe ancora un pezzo del valore vecchio appeso in alto.
         */
        const val ROLL_SPAN = 1.45f

        /** Mezza fascia dentro cui il rotolamento e' visibile, in altezze del numero. */
        const val ROLL_CLIP = 0.72f

        val SHADOW_STEPS = floatArrayOf(0.28f, 1f)
        val SHADOW_WEIGHTS = floatArrayOf(0.60f, 0.40f)

        /**
         * Quanto si allontana l'ombra, in multipli dello spessore della cifra.
         *
         * Corta apposta. Piu' in la' i due gradini si staccano dalla sagoma e
         * fra l'oggetto e la sua ombra si vede il fondo: a quel punto non si
         * legge piu' come ombra ma come una seconda cifra scura appoggiata
         * dietro la prima.
         */
        const val SHADOW_REACH = 0.46f

        /**
         * Dove cade l'ombra: nel verso opposto alla luce, e da nessun'altra
         * parte. Presa dalla luce stessa cosi' spostare la lampada sposta anche
         * l'ombra, invece di lasciarle contraddirsi - la luce sfiorava la cifra
         * da sinistra in alto e l'ombra cadeva quasi in verticale, cioe' come se
         * venisse da un'altra lampada.
         */
        val SHADOW_DIRECTION: Offset = run {
            val l = Light.Standard
            val len = hypot(l.x, l.y).takeIf { it > 1e-4f } ?: 1f
            Offset(-l.x / len, -l.y / len)
        }

        /**
         * Fra queste due aperture l'ombra passa da assente a piena. Un gradino
         * secco si vedrebbe scattare proprio nell'istante in cui la cifra passa
         * di profilo, che e' il momento in cui la si sta guardando.
         */
        const val SHADOW_FADE_FROM = 0.05f
        const val SHADOW_FADE_TO = 0.20f
    }
}
