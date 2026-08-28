package com.forli.meteo.ui.render3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** Bianco pieno, gia' in intero: e' verso questo che il lampo schiarisce i vertici. */
private const val LightningWhite = 0xFFFFFFFF.toInt()

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

    /** Il riquadro dello strato dell'ombra, riusato per non allocare a ogni fotogramma. */
    private val shadowArea = android.graphics.RectF()

    /** Il riquadro del glifo, che serve a sapere attorno a cosa allargare la penombra. */
    private val glyphBounds = android.graphics.RectF()

    /** La matrice della penombra: la proiezione piu' un allargamento. */
    private val haloMatrix = android.graphics.Matrix()

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
        // **Misurato su una sagoma di riferimento, non sul testo vero.**
        //
        // La larghezza dell'inchiostro dipende da quali cifre sono: l'uno ne ha
        // molto meno di uno zero. Misurando il testo vero, "31" restava sotto la
        // soglia o la sfiorava e veniva rimpicciolito meno di "32", e le due
        // temperature uscivano di corpo diverso a un'ora di distanza. Con tutte
        // le cifre ridotte a uno zero, qualunque valore della stessa lunghezza
        // riceve lo stesso corpo, ed e' l'unica cosa che conta: la cifra non
        // deve respirare mentre si scorre la barra.
        val gauge = buildString {
            for (c in spec.text) append(if (c.isDigit()) '0' else c)
        }
        val width = TextPrism.widthOf(
            text = gauge,
            typeface = spec.typeface,
            sizePx = size,
            letterSpacingEm = spec.letterSpacingEm,
            smallTail = spec.smallTail,
            variationSettings = spec.variationSettings,
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
            variationSettings = spec.variationSettings,
        ) ?: return null

        return Prepared(
            prism = prism,
            depth = depth,
            chamfer = (size * 0.016f).coerceIn(1.5f, 18f),
        ).also { cache[spec] = it }
    }

    override fun draw(
        scope: DrawScope,
        prepared: PreparedNumber,
        center: Offset,
        palette: NumberPalette,
        motion: NumberMotion,
        silhouette: Skyline?,
        lift: Float,
        glare: Float,
    ) = with(scope) {
        val model = prepared as? Prepared ?: return@with
        val prism = model.prism
        silhouette?.reset(size.width)

        val camera = Camera(
            yawDeg = motion.yawDeg,
            pitchDeg = motion.pitchDeg,
            distance = max(model.width, model.height) * EYE_DISTANCE,
            origin = center,
        )
        val ink = inkFor(palette)
        val reversed = prism.reversed(camera)

        // Quanto la cifra e' sollevata, in coordinate del modello. Serve
        // all'entrata, ed e' li' e non sulla tela apposta: uno scostamento
        // applicato dopo la proiezione sposterebbe un'immagine gia' piatta, e
        // la cifra scivolerebbe come un adesivo invece di salire nello spazio.
        val rise = lift * model.height

        // Le ombre tutte per prime, e tutte insieme. Sul fondo grigio unico
        // l'ombra portata non e' piu' un vezzo del tema chiaro: e' cio' che
        // stacca un oggetto bianco dal fondo quando la barra scorre verso le ore
        // di luce. Ma e' un disegno, non un fenomeno: deve stare sul fondo, non
        // sulla faccia del carattere accanto.
        //
        // **L'ombra e' il volume spazzato, non un piano.** Per tre giri e' stata
        // due copie della faccia proiettate su due piani dietro l'oggetto, ognuna
        // col suo alpha. Il difetto che si vedeva - "un foglio appiccicato dietro
        // il numero", e si notava alla prima rotazione - non era la distanza ne'
        // il colore: era che **un piano non e' un solido**. L'ombra di una lastra
        // estrusa e' il suo volume spinto lungo la luce, cioe' la faccia spazzata
        // da qui fino a la'; una faccia sola, per quanto ben proiettata, resta
        // una lastra sottile appoggiata dietro. E due gradini a bordo netto sono
        // esattamente due lastre.
        //
        // Adesso la faccia viene proiettata a piu' profondita' lungo lo stesso
        // viaggio e l'unione delle copie **e'** il volume. Tre cose la rendono
        // un'ombra invece che una pila di sagome:
        //
        // - **Uno strato fuori schermo, e l'alpha una volta sola.** Dentro lo
        //   strato le copie sono nero pieno, quindi sovrapporsi non le scurisce:
        //   l'unione e' una macchia sola. Sommandole direttamente a schermo ogni
        //   sovrapposizione si scurirebbe due volte e i gradini tornerebbero
        //   visibili - era proprio quello a tradire il trucco.
        // - **Il viaggio e' corto.** Non c'e' pavimento su cui cadere, quindi non
        //   e' un'ombra portata: e' il buio di contatto che stacca l'oggetto dal
        //   fondo. Corta, resta credibile a ogni angolo; lunga, chiede un
        //   pavimento che non esiste.
        // - **Una penombra vera**, due copie appena piu' larghe sotto al nucleo,
        //   allargate attorno al centro del glifo e non spostate: e' un alone che
        //   circonda la sagoma, non una terza lastra piu' in la'.
        //
        // Costa piu' di prima e va detto: sette riempimenti per carattere invece
        // di due. Restano tutti affini (la proiezione usa tre angoli apposta),
        // quindi restano sulla strada veloce, e lo strato e' ritagliato attorno
        // alla cifra invece che grande quanto lo schermo.
        //
        // Quanto la cifra e' aperta verso l'occhio. Di taglio non proietta piu'
        // niente, e la matrice che ce la porterebbe e' fatta di numeri che
        // divergono: l'ombra si spegne prima di arrivarci, invece di sfrangiarsi
        // in strisce lunghe mezzo schermo - che era il difetto che si vedeva
        // spuntare da sotto la cifra ogni volta che passava di profilo.
        val openness = prism.openness(camera)
        val castAlpha = palette.shadowAlpha *
            ((openness - SHADOW_FADE_FROM) / (SHADOW_FADE_TO - SHADOW_FADE_FROM)).coerceIn(0f, 1f)

        if (castAlpha > 0.001f) {
            val travel = model.depth * SHADOW_REACH
            // Lo strato sta attorno alla cifra, non su tutto lo schermo. La
            // meta' diagonale basta a contenerla comunque sia girata, piu' il
            // viaggio dell'ombra e un margine per la penombra.
            val span = max(model.width, model.height) * 0.75f + travel * 2f
            shadowArea.set(
                center.x - span, center.y - span,
                center.x + span, center.y + span,
            )
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.saveLayerAlpha(shadowArea, (castAlpha * 255f).toInt())
                shadowPaint.color = android.graphics.Color.BLACK
                for (index in 0 until prism.partCount) {
                    val outline = prism.outlineOf(index)
                    outline.computeBounds(glyphBounds, true)
                    val cx = glyphBounds.centerX()
                    val cy = glyphBounds.centerY()

                    // La penombra per prima, cosi' il nucleo le finisce sopra.
                    if (prism.prepareShadow(index, camera, rise, travel * 0.5f)) {
                        for (h in HALO_SPREADS.indices) {
                            haloMatrix.set(prism.shadowTransform)
                            haloMatrix.preScale(HALO_SPREADS[h], HALO_SPREADS[h], cx, cy)
                            shadowPaint.alpha = (HALO_ALPHAS[h] * 255f).toInt()
                            native.save()
                            native.concat(haloMatrix)
                            native.drawPath(outline, shadowPaint)
                            native.restore()
                        }
                    }

                    // Il nucleo: la faccia spazzata lungo la luce.
                    shadowPaint.alpha = 255
                    for (k in 0 until SHADOW_SAMPLES) {
                        val behind = travel * (k / (SHADOW_SAMPLES - 1f))
                        if (!prism.prepareShadow(index, camera, rise, behind)) continue
                        native.save()
                        native.concat(prism.shadowTransform)
                        native.drawPath(outline, shadowPaint)
                        native.restore()
                    }
                }
                native.restore()
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
                yOffset = rise,
            )
            silhouette?.let { prism.addSilhouette(index, it) }

            // Il lampo illumina anche la cifra, non solo le nuvole: senza
            // questo la scultura si accende e il numero accanto resta
            // indifferente, e i due smettono di sembrare nello stesso cielo.
            // Costa solo quando c'e' davvero un lampo in corso.
            if (glare > 0.01f) {
                val amount = (glare * WALL_GLARE).coerceIn(0f, 1f)
                for (c in 0 until surfaces.vertexValues / 2) {
                    surfaces.colours[c] = TextPrism.blend(surfaces.colours[c], LightningWhite, amount)
                }
            }

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
                    val base = lerp(
                        lerp(palette.sideNear, palette.sideFar, FACE_SHADE),
                        palette.face,
                        faceTone(surfaces),
                    )
                    facePaint.color = if (glare > 0.01f) {
                        lerp(base, Color.White, (glare * FACE_GLARE).coerceIn(0f, 1f))
                    } else {
                        base
                    }.toArgb()
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
         * Quanto il lampo schiarisce pareti e faccia, in proporzione al suo
         * valore. La faccia meno delle pareti: e' gia' la superficie piu'
         * chiara, e portarla anche lei fino al bianco pieno la farebbe
         * sparire nel bagliore invece di restare leggibile come materiale.
         */
        const val WALL_GLARE = 0.55f
        const val FACE_GLARE = 0.35f

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
         * Quante volte si campiona il viaggio della luce.
         *
         * Sono le fette del volume spazzato: fra una e l'altra deve restare meno
         * di un paio di pixel, se no l'unione mostra i gradini e si torna a
         * vedere una pila di sagome invece di una macchia sola. Cinque bastano
         * perche' il viaggio e' corto - se un giorno si allungasse, questo numero
         * va alzato insieme a [SHADOW_REACH], non uno senza l'altro.
         */
        const val SHADOW_SAMPLES = 5

        /**
         * La penombra: due copie appena piu' larghe **attorno al centro del
         * glifo**, non spostate piu' in la'.
         *
         * E' la differenza fra un alone che circonda la sagoma e una terza lastra
         * appoggiata dietro. Gli allargamenti sono minimi apposta: oltre il
         * cinque per cento l'alone smette di sembrare una sfumatura e comincia a
         * sembrare un contorno disegnato.
         */
        val HALO_SPREADS = floatArrayOf(1.045f, 1.020f)
        val HALO_ALPHAS = floatArrayOf(0.28f, 0.55f)

        /**
         * Quanto dietro l'oggetto cade l'ombra, in multipli del suo spessore.
         *
         * **Non e' lo scostamento sullo schermo, e la differenza e' costata un
         * giro.** Passando dalla copia traslata alla proiezione questo numero ha
         * cambiato significato: prima erano pixel di tela, adesso e' profondita'
         * su cui poi si viaggia lungo la luce. Lasciato al valore di prima
         * moltiplicato per il viaggio, lo scostamento a schermo era piu' che
         * raddoppiato e l'ombra si staccava dalla cifra come un fantasma.
         */
        const val SHADOW_REACH = 0.22f

        /**
         * Fra queste due aperture l'ombra passa da assente a piena.
         *
         * **La corsa si e' accorciata di molto, e non e' una taratura: e' la
         * conseguenza del volume.** Finche' l'ombra era un piano, girando smetteva
         * di somigliare all'oggetto e bisognava nasconderla presto - la corsa
         * arrivava fino a sessantadue centesimi di apertura, cioe' spariva ben
         * prima del quarto di giro. Adesso la sagoma e' quella giusta a ogni
         * angolo, quindi non c'e' piu' niente da nascondere: resta solo la
         * guardia contro il caso degenere, la cifra esattamente di taglio, dove
         * non c'e' superficie da proiettare e la matrice e' fatta di numeri che
         * divergono.
         */
        const val SHADOW_FADE_FROM = 0.04f
        const val SHADOW_FADE_TO = 0.17f
    }
}
