package com.forli.meteo.ui.render3d

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Un testo trasformato in prismi retti veri, uno per carattere.
 *
 * Il corpo di ogni carattere e' compreso fra due piani paralleli, uno davanti e
 * uno dietro il centro. Le pareti stanno fra i due, ognuna con la propria
 * normale.
 *
 * **Le basi non vengono triangolate, e non serve**: una figura piana sotto una
 * proiezione prospettica si trasforma per omografia, quindi la base frontale si
 * disegna applicando al tracciato originale la matrice che porta i quattro
 * angoli del suo riquadro dove finiscono davvero. Il contorno resta quello vero
 * del font, curve comprese, e costa un solo disegno.
 *
 * **Le pareti invece sono triangoli, non sagome.** Raggrupparle per tono e
 * riempirle come sagome era la strada ovvia, e a queste dimensioni non regge:
 * riempire una sagoma con antialiasing costa in proporzione alla superficie, e
 * fra pareti, smussi e ombra il disegno ne copriva svariate volte lo schermo a
 * ogni fotogramma. Misurato: una decina di millisecondi per registrare i
 * comandi e altrettanti per eseguirli, con lo schermo che ne concede sedici in
 * tutto. Con i triangoli il colore sta sui vertici e viene interpolato: una
 * chiamata sola, nessuna sagoma da rasterizzare, e per giunta la sfumatura
 * diventa continua invece che a fasce.
 *
 * **Un prisma per carattere e non uno per l'intera scritta.** Senza un buffer di
 * profondita' l'ordine di disegno e' l'unica cosa che decide chi sta davanti, e
 * disegnare prima tutte le pareti e poi tutte le basi funziona solo dentro un
 * corpo solo. Fra due caratteri no: girando, quello a sinistra viene avanti e la
 * sua parete deve coprire la faccia di quello a destra. Con una scritta sola il
 * "6" si stampava sopra al "2" che gli stava davanti. Separati, basta disegnarli
 * dal piu' lontano al piu' vicino, e la rotazione essendo attorno all'asse
 * verticale rende quell'ordine esatto.
 */
class TextPrism private constructor(
    private val parts: List<Part>,
    val width: Float,
    val height: Float,
) {

    /** Un carattere. Le coordinate sono gia' centrate sull'intera scritta. */
    private class Part(
        /** Quattro coordinate per spigolo: inizio e fine. */
        val edges: FloatArray,
        /** Normale uscente per spigolo, nel piano, gia' normalizzata. */
        val normals: FloatArray,
        /**
         * Dove comincia ogni contorno, piu' un ultimo valore col totale.
         *
         * Serve a sapere chi confina con chi: la normale di un vertice e' la
         * media di quelle dei due spigoli che vi si incontrano, e senza i
         * confini dei contorni si finirebbe per mediare l'ultimo spigolo di una
         * curva col primo di un'altra.
         */
        val contourStart: IntArray,
        /**
         * Lo spigolo che segue, per ognuno, gia' richiuso sul proprio contorno.
         *
         * Serve perche' gli spigoli non si percorrono piu' nell'ordine in cui
         * stanno scritti: si disegnano dal piu' lontano al piu' vicino, e in
         * quell'ordine "il prossimo" non e' "quello dopo".
         */
        val next: IntArray,
        val edgeCount: Int,
        /**
         * Gli stessi punti campionati, in coppie x,y di seguito.
         *
         * Duplicano meta' di [edges] e valgono la memoria: cosi' la sagoma si
         * proietta con una sola chiamata nativa invece che punto per punto, e
         * chi deve sapere dov'e' la superficie la ottiene quasi gratis.
         */
        val points: FloatArray,
        /** Il contorno vero, non campionato. */
        val outline: android.graphics.Path,
        /**
         * Quanto e' spesso questo carattere, in frazione dello spessore comune.
         *
         * Uno per le cifre. Per il grado vale quanto il suo corpo: un simbolo
         * rimpicciolito solo in altezza e larghezza, ma lasciato spesso come una
         * cifra, non e' piu' un simbolo - e' un tubo. Il suo anello e' largo
         * novanta pixel e lo spessore comune ne misura centoventi: visto di
         * sbieco usciva un pezzo di tubo appoggiato accanto al numero. Ridotto
         * nella stessa proporzione resta la stessa lastra, ritagliata piu'
         * piccola.
         */
        val depthScale: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val centreX: Float get() = (left + right) / 2f
    }

    /**
     * I colori del materiale, gia' risolti in interi.
     *
     * Risolti perche' il colore finisce sui vertici, e un vertice non puo'
     * portarsi dietro una tavolozza: porta un numero.
     */
    class Ink(
        val wallFar: Int,
        val wallNear: Int,
        val bevelDark: Int,
        val bevelLight: Int,
        val ambient: Float,
        /** Le tinte dell'iridescenza, gia' in interi, e quanto pesano. */
        val iridescence: IntArray,
        val iridescenceAlpha: Float,
    )

    // Contenitori riusati a ogni fotogramma e a ogni carattere: ricrearli
    // sarebbe l'unica allocazione degna di nota di tutto il disegno.
    private val maxEdges = parts.maxOf { it.edgeCount }

    /**
     * Gli spigoli visibili, ordinabili dal piu' lontano al piu' vicino.
     *
     * Ogni voce impacchetta la profondita' nei trentadue bit alti e l'indice
     * dello spigolo in quelli bassi, cosi' un solo `sort` di interi lunghi -
     * senza allocare e senza comparatori - mette in fila le pareti. La
     * profondita' e' un `Float` reinterpretato in modo che l'ordine dei bit
     * coincida con l'ordine dei numeri.
     */
    private val order = LongArray(maxEdges)

    /**
     * Esposizione alla luce nel punto d'inizio di ogni spigolo, non sullo
     * spigolo. E' la differenza fra una sfumatura continua e una a fasce: il
     * tono viene interpolato lungo il triangolo invece di restare costante su
     * tutta la faccia.
     */
    private val wallLambert = FloatArray(maxEdges)
    private val bevelLambert = FloatArray(maxEdges)

    /** Sei vertici per spigolo (due triangoli), per le pareti e per gli smussi. */
    private val verts = FloatArray(maxEdges * 24)
    private val colours = IntArray(maxEdges * 12)

    private val matrix = Matrix()
    private val shadowMatrix = Matrix()
    private var mapped = FloatArray(0)
    private val cornersLocal = FloatArray(8)
    private val cornersScreen = FloatArray(8)

    private val surfaces = Surfaces()

    val partCount: Int get() = parts.size

    /**
     * Cosa disegnare per un carattere.
     *
     * L'oggetto e' sempre lo stesso e viene riscritto a ogni chiamata: va
     * consumato prima di chiedere il carattere successivo.
     */
    class Surfaces {
        /** Coppie x,y: due triangoli per parete, e in coda quelli degli smussi. */
        var verts: FloatArray = FloatArray(0); internal set
        var colours: IntArray = IntArray(0); internal set

        /** Quanti valori di [verts] sono validi. E' il doppio dei vertici. */
        var vertexValues: Int = 0; internal set

        /**
         * Il contorno del carattere e la matrice che lo porta sullo schermo,
         * tenuti separati.
         *
         * Trasformare il tracciato punto per punto era la strada ovvia, e con
         * una matrice prospettica costa: le curve del font non restano curve
         * dello stesso tipo, e vanno spezzate in tante piu' parti. Rifatto a
         * ogni fotogramma era fra le voci piu' care del disegno. Applicando
         * invece la matrice alla tela, il tracciato resta quello di sempre e la
         * prospettiva la fa chi disegna.
         */
        var outline: android.graphics.Path = android.graphics.Path(); internal set
        var matrix: Matrix = Matrix(); internal set

        /**
         * Falso quando l'oggetto e' troppo di taglio perche' la base abbia
         * ancora superficie.
         *
         * Non basta chiedersi se la matrice esiste. Avvicinandosi al quarto di
         * giro i quattro angoli del riquadro finiscono quasi in fila, e una
         * matrice quasi singolare esiste eccome: e' fatta di numeri enormi, e
         * ci stampa il tracciato come una colata di strisce lunghe mezzo
         * schermo. Sotto la soglia la base non si disegna, e non manca a
         * nessuno: li' e' larga meno di un pelo.
         */
        var capVisible: Boolean = true; internal set

        /** Esposizione della base rivolta all'occhio: cambia ruotando, ed e' meta' dell'effetto. */
        var faceLambert: Float = 1f; internal set
    }

    /**
     * Vero se i caratteri vanno percorsi dall'ultimo al primo.
     *
     * Con una rotazione attorno al solo asse verticale la profondita' cresce in
     * modo monotono lungo l'asse orizzontale del modello, e i caratteri sono
     * gia' in quell'ordine: disporli dal piu' lontano al piu' vicino si riduce a
     * scegliere da che parte cominciare.
     */
    fun reversed(camera: Camera): Boolean {
        if (parts.size < 2) return false
        camera.place(parts.first().centreX, 0f, 0f)
        val first = camera.vz
        camera.place(parts.last().centreX, 0f, 0f)
        return camera.vz > first
    }

    /** Il contorno di un carattere, per chi deve disegnarlo da solo. */
    fun outlineOf(index: Int): android.graphics.Path = parts[index].outline

    /**
     * Quanto la base e' aperta verso l'occhio: uno di fronte, zero di taglio.
     *
     * E' il coseno fra la normale della base e la direzione di vista, e serve a
     * due cose: sapere quando smettere di disegnare la base, e sapere quanto
     * pesa l'ombra portata. Entrambe spariscono quando l'oggetto si mette di
     * taglio, ed entrambe, prima di sparire, diventano garbage numerico.
     */
    fun openness(camera: Camera): Float {
        camera.normal(0f, 0f, -1f)
        return abs(camera.nvz)
    }

    /**
     * La matrice dell'ombra portata di un carattere, senza costruirne le pareti.
     *
     * Serve a disegnare tutte le ombre prima di tutti i corpi. Disegnandole
     * insieme al proprio carattere, l'ombra di quello vicino finiva sulla faccia
     * di quello lontano e gliela ingrigiva: girando la scena di mezzo passo, una
     * cifra diventava sporca senza motivo apparente. L'ombra qui e' un disegno,
     * non un fenomeno: sta sul fondo, non sugli altri oggetti.
     *
     * **Sul piano mediano, non su una delle due basi.** Prima seguiva la base
     * rivolta all'occhio, e quella cambia identita' al quarto di giro: nello
     * stesso istante in cui la cifra passava di taglio, l'ombra saltava
     * dall'altra parte dello spessore e si vedeva scattare. Il piano di mezzo
     * non ha un davanti e un dietro, quindi attraversa il giro intero senza
     * accorgersene - e per giunta e' il posto giusto da cui far partire
     * un'ombra, che non e' una copia della faccia ma la proiezione del volume.
     *
     * Scrive in [shadowTransform]. Torna falso se non c'e' piu' superficie da
     * proiettare.
     */
    fun prepareShadow(index: Int, camera: Camera): Boolean {
        val part = parts[index]
        cornersLocal[0] = part.left; cornersLocal[1] = part.top
        cornersLocal[2] = part.right; cornersLocal[3] = part.top
        cornersLocal[4] = part.right; cornersLocal[5] = part.bottom
        cornersLocal[6] = part.left; cornersLocal[7] = part.bottom
        for (k in 0 until 4) {
            camera.place(cornersLocal[k * 2], cornersLocal[k * 2 + 1], 0f)
            cornersScreen[k * 2] = camera.sx
            cornersScreen[k * 2 + 1] = camera.sy
        }
        // Tre angoli e non quattro: con tre la matrice che ne esce e' affine,
        // senza il termine prospettico. La differenza sulla sagoma e' di qualche
        // pixel sull'angolo piu' lontano, invisibile su una macchia al dodici per
        // cento di nero; la differenza sul costo no. Una sagoma grande sotto una
        // matrice prospettica non passa dalla strada veloce della scheda grafica,
        // e questa qui va ridisegnata a ogni goccia che cade.
        return shadowMatrix.setPolyToPoly(cornersLocal, 0, cornersScreen, 0, 3)
    }

    val shadowTransform: Matrix get() = shadowMatrix

    /**
     * Scrive in [into] la sagoma della base di un carattere.
     *
     * Va chiamata dopo [shape], che e' quella che calcola la matrice. Una sola
     * chiamata nativa trasforma tutti i punti campionati in un colpo: e' lo
     * stesso contorno che gia' serve alle pareti, riusato per dire alla pioggia
     * dove trova superficie.
     */
    fun addSilhouette(index: Int, into: Skyline) {
        val part = parts[index]
        if (mapped.size != part.points.size) mapped = FloatArray(part.points.size)
        matrix.mapPoints(mapped, 0, part.points, 0, part.points.size / 2)

        // I contorni si percorrono chiusi e uno alla volta: unendo l'ultimo
        // punto di una curva al primo della successiva si tirerebbe un tratto
        // attraverso il vuoto, e la pioggia si fermerebbe a mezz'aria fra un
        // vuoto e l'altro della cifra.
        for (c in 0 until part.contourStart.size - 1) {
            val from = part.contourStart[c]
            val until = part.contourStart[c + 1]
            if (until - from < 2) continue
            for (k in from until until) {
                val n = if (k + 1 == until) from else k + 1
                into.addSpan(
                    mapped[k * 2], mapped[k * 2 + 1],
                    mapped[n * 2], mapped[n * 2 + 1],
                )
            }
        }
    }

    /**
     * Costruisce le superfici visibili di un carattere.
     *
     * @param depth spessore del prisma, in pixel.
     * @param chamfer quanto della profondita' se ne va nello smusso frontale.
     */
    fun shape(
        index: Int,
        camera: Camera,
        light: Light,
        depth: Float,
        chamfer: Float,
        ink: Ink,
    ): Surfaces {
        val part = parts[index]
        val thickness = depth * part.depthScale
        val half = thickness / 2f
        val bevel = (chamfer * part.depthScale).coerceIn(0f, thickness * 0.4f)

        // Girando oltre il quarto di giro si finisce a guardare l'oggetto da
        // dietro, e allora la base che si vede e' l'altra. Disegnare sempre
        // quella davanti la stamperebbe sopra le pareti che dovrebbero
        // nasconderla, e la cifra sembrerebbe trasparente. Un prisma non ha un
        // davanti assoluto: ha una base rivolta all'occhio e una no.
        camera.normal(0f, 0f, -1f)
        val camera0z = camera.nvz
        val frontToViewer = camera0z < 0f
        val zCap = if (frontToViewer) -half else half
        val zBevel = if (frontToViewer) -half + bevel else half - bevel
        val zBack = -zCap
        val bevelZ = if (frontToViewer) -DIAGONAL else DIAGONAL

        camera.normal(0f, 0f, if (frontToViewer) -1f else 1f)
        val capLambert = camera.lambert(light)

        val visibleCount = classify(part, camera, light, bevelZ)

        // Dal piu' lontano al piu' vicino, parete e smusso di uno spigolo per
        // volta.
        //
        // Prima gli spigoli uscivano nell'ordine in cui stanno scritti, cioe'
        // contorno per contorno, e i vuoti del carattere sono contorni come gli
        // altri: le pareti interne dell'8 finivano quindi *sopra* il pieno che
        // avrebbero dovuto avere davanti. Di faccia non si notava, ma girando
        // l'8 verso il taglio i due occhielli venivano avanti come due cilindri
        // appoggiati sulla cifra - e non erano un artefatto dell'estrusione, era
        // l'ordine di disegno. Ordinando per profondita' il problema sparisce
        // alla radice, e con lui sparisce anche la regola "prima tutte le
        // pareti, poi tutti gli smussi": lo smusso di uno spigolo lontano deve
        // stare sotto la parete di uno vicino, non sopra, ed e' esattamente
        // quello che l'ordine dice adesso.
        var n = 0
        for (k in visibleCount - 1 downTo 0) {
            val i = (order[k] and 0xFFFFFFFFL).toInt()
            n = emitSide(part, camera, i, zBevel, zBack, wallLambert, ink, bevel = false, at = n)
            if (bevel > 0f) {
                n = emitSide(part, camera, i, zCap, zBevel, bevelLambert, ink, bevel = true, at = n)
            }
        }

        // La base per omografia: i quattro angoli del riquadro del carattere
        // dicono da soli dove va a finire tutto il resto.
        cornersLocal[0] = part.left; cornersLocal[1] = part.top
        cornersLocal[2] = part.right; cornersLocal[3] = part.top
        cornersLocal[4] = part.right; cornersLocal[5] = part.bottom
        cornersLocal[6] = part.left; cornersLocal[7] = part.bottom
        for (k in 0 until 4) {
            camera.place(cornersLocal[k * 2], cornersLocal[k * 2 + 1], zCap)
            cornersScreen[k * 2] = camera.sx
            cornersScreen[k * 2 + 1] = camera.sy
        }
        // Esattamente di taglio i quattro angoli finiscono in fila e la matrice
        // non esiste: la si dichiara assente invece di lasciare quella del
        // fotogramma prima, che disegnerebbe la base dove non c'e' piu'.
        val mapped = matrix.setPolyToPoly(cornersLocal, 0, cornersScreen, 0, 4)

        surfaces.verts = verts
        surfaces.colours = colours
        surfaces.vertexValues = n
        surfaces.outline = part.outline
        surfaces.matrix = matrix
        surfaces.capVisible = mapped && abs(camera0z) > MIN_CAP_OPENNESS
        surfaces.faceLambert = capLambert
        return surfaces
    }

    /**
     * Per ogni spigolo: si vede. E per ogni vertice: quanta luce prende.
     *
     * L'esposizione si calcola sul vertice e non sullo spigolo perche' e' li'
     * che serve. Mediando le normali dei due spigoli che si incontrano, una
     * curva liscia riceve una sfumatura liscia; prendendo il valore dello
     * spigolo, ogni faccia resterebbe di tinta piatta e la curva si leggerebbe
     * come una scalinata.
     */
    private fun classify(part: Part, camera: Camera, light: Light, bevelZ: Float): Int {
        var count = 0
        for (i in 0 until part.edgeCount) {
            camera.normal(part.normals[i * 2], part.normals[i * 2 + 1], 0f)
            camera.place(
                (part.edges[i * 4] + part.edges[i * 4 + 2]) / 2f,
                (part.edges[i * 4 + 1] + part.edges[i * 4 + 3]) / 2f,
                0f,
            )
            // Prova di visibilita': le pareti che guardano dall'altra parte non
            // vanno disegnate, altrimenti il retro dell'oggetto verrebbe
            // dipinto sopra il davanti. Quelle di spalle non entrano nemmeno
            // nella fila: chi non si vede non costa nemmeno un posto.
            if (camera.facesViewer()) {
                // La profondita' del punto di mezzo dello spigolo, sul piano
                // mediano: e' il posto giusto da cui misurare una parete, che
                // sta a cavallo dei due piani in parti uguali.
                order[count] = (sortable(camera.vz) shl 32) or i.toLong()
                count++
            }
        }
        if (count > 1) java.util.Arrays.sort(order, 0, count)

        for (c in 0 until part.contourStart.size - 1) {
            val from = part.contourStart[c]
            val until = part.contourStart[c + 1]
            if (until <= from) continue
            for (k in from until until) {
                val previous = if (k == from) until - 1 else k - 1
                var nx = part.normals[k * 2] + part.normals[previous * 2]
                var ny = part.normals[k * 2 + 1] + part.normals[previous * 2 + 1]
                val len = hypot(nx, ny).takeIf { it > 1e-4f } ?: 1f
                nx /= len
                ny /= len

                camera.normal(nx, ny, 0f)
                wallLambert[k] = camera.lambert(light)
                // Lo smusso e' la prima fetta dell'estrusione, con normale a
                // meta' strada fra la base e la parete. Rientrare la base per
                // ricavarlo sembrava piu' fedele, ma sulle curve strette le
                // normali di punti vicini divergono e il poligono rientrato si
                // ripiega su se stesso: da li' le tacche che sfiguravano le
                // cifre.
                camera.normal(nx * DIAGONAL, ny * DIAGONAL, bevelZ)
                bevelLambert[k] = camera.lambert(light)
            }
        }
        return count
    }

    /** Il quadrilatero di uno spigolo fra due piani, spezzato in due triangoli. */
    private fun emitSide(
        part: Part,
        camera: Camera,
        i: Int,
        zNear: Float,
        zFar: Float,
        lambert: FloatArray,
        ink: Ink,
        bevel: Boolean,
        at: Int,
    ): Int {
        var n = at
        val next = part.next[i]

        val startColour = colourOf(lambert[i], ink, bevel)
        val endColour = colourOf(lambert[next], ink, bevel)

        camera.place(part.edges[i * 4], part.edges[i * 4 + 1], zNear)
        val ax = camera.sx
        val ay = camera.sy
        camera.place(part.edges[i * 4 + 2], part.edges[i * 4 + 3], zNear)
        val bx = camera.sx
        val by = camera.sy
        camera.place(part.edges[i * 4 + 2], part.edges[i * 4 + 3], zFar)
        val cx = camera.sx
        val cy = camera.sy
        camera.place(part.edges[i * 4], part.edges[i * 4 + 1], zFar)
        val dx = camera.sx
        val dy = camera.sy

        // a-b-c e a-c-d: il quadrilatero spezzato lungo una diagonale.
        n = vertex(n, ax, ay, startColour)
        n = vertex(n, bx, by, endColour)
        n = vertex(n, cx, cy, endColour)
        n = vertex(n, ax, ay, startColour)
        n = vertex(n, cx, cy, endColour)
        n = vertex(n, dx, dy, startColour)
        return n
    }

    private fun vertex(at: Int, x: Float, y: Float, colour: Int): Int {
        verts[at] = x
        verts[at + 1] = y
        colours[at / 2] = colour
        return at + 2
    }

    /**
     * Il tono di un vertice, iridescenza compresa.
     *
     * L'iridescenza non e' piu' una sfumatura stesa sopra: e' una tinta che i
     * vertici dello smusso si portano addosso dove la luce li sfiora. Restano
     * la fascia di incidenza radente e il dieci-quindici per cento di
     * superficie della specifica, ma senza costare un riempimento in piu'.
     */
    private fun colourOf(lambert: Float, ink: Ink, bevel: Boolean): Int {
        if (!bevel) {
            return blend(ink.wallFar, ink.wallNear, ink.ambient + (1f - ink.ambient) * lambert)
        }
        val base = blend(ink.bevelDark, ink.bevelLight, lambert)
        val grazing = grazing(lambert)
        if (grazing <= 0.01f || ink.iridescence.isEmpty()) return base
        val tint = ink.iridescence[
            (lambert * (ink.iridescence.size - 1)).toInt().coerceIn(0, ink.iridescence.size - 1),
        ]
        return blend(base, tint, grazing * ink.iridescenceAlpha)
    }

    /** Campana centrata sull'incidenza radente. */
    private fun grazing(lambert: Float): Float {
        val d = (lambert - GRAZING_CENTRE) / GRAZING_WIDTH
        return (1f - d * d).coerceAtLeast(0f)
    }

    companion object {
        /** Componenti di una normale a quarantacinque gradi. */
        private const val DIAGONAL = 0.7071f

        private const val GRAZING_CENTRE = 0.44f
        private const val GRAZING_WIDTH = 0.30f

        /**
         * Sotto questa apertura la base non si disegna piu'.
         *
         * Poco meno di due gradi dal taglio netto. Li' la faccia e' larga una
         * decina di pixel e non manca a nessuno, mentre la matrice che ce la
         * porterebbe e' fatta di numeri che divergono: sono le strisce lunghe
         * mezzo schermo che si vedevano spuntare da sotto la cifra ogni volta
         * che passava di profilo.
         */
        private const val MIN_CAP_OPENNESS = 0.035f

        /** Lo stacco fra le cifre e il simbolo, in frazione del corpo. */
        private const val SYMBOL_GAP = 0.015f

        /**
         * Quanto e' piccolo il simbolo in coda rispetto alle cifre.
         *
         * Uno solo per la costruzione e per la misura: due valori uguali per
         * caso si scollano al primo ritocco, e la cifra si troverebbe misurata
         * con proporzioni diverse da quelle con cui viene poi costruita.
         */
        const val SMALL_SCALE = 0.44f

        /**
         * Un `Float` reinterpretato in un intero che si ordina allo stesso modo.
         *
         * I bit di un numero in virgola mobile positivo crescono con lui; quelli
         * di un negativo calano. Ribaltando i secondi si ottiene una chiave che
         * il confronto fra interi con segno ordina come ordinerebbe i numeri, e
         * da li' basta un `sort` di primitivi: niente comparatori, niente
         * scatole, niente allocazioni a ogni fotogramma.
         */
        private fun sortable(value: Float): Long {
            val bits = java.lang.Float.floatToRawIntBits(value)
            return (bits xor ((bits shr 31) and 0x7FFFFFFF)).toLong()
        }

        /** Interpolazione fra due colori interi, canale per canale. */
        fun blend(from: Int, to: Int, amount: Float): Int {
            val t = amount.coerceIn(0f, 1f)
            val fa = from ushr 24 and 0xFF
            val fr = from ushr 16 and 0xFF
            val fg = from ushr 8 and 0xFF
            val fb = from and 0xFF
            val a = (fa + ((to ushr 24 and 0xFF) - fa) * t).toInt()
            val r = (fr + ((to ushr 16 and 0xFF) - fr) * t).toInt()
            val g = (fg + ((to ushr 8 and 0xFF) - fg) * t).toInt()
            val b = (fb + ((to and 0xFF) - fb) * t).toInt()
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        /**
         * Estrae il contorno del testo, carattere per carattere, e lo riduce a
         * spigoli con normale.
         *
         * @param step distanza fra due campioni lungo il contorno. Le pareti
         *   sono strette e il loro bordo esterno si vede poco: campionare fitto
         *   costa facce e non aggiunge fedelta', perche' la sagoma che conta e'
         *   quella della base, che resta il tracciato vero del font.
         */
        fun of(
            text: String,
            typeface: Typeface,
            sizePx: Float,
            letterSpacingEm: Float = 0f,
            step: Float = 5f,
            /**
             * Quanti caratteri finali vanno in corpo ridotto e allineati in alto.
             *
             * E' il grado della temperatura: appartiene alla cifra - viene
             * estruso, illuminato e girato con lei - ma non e' una cifra, e in
             * corpo pieno se ne prenderebbe un quarto della larghezza rubandola
             * a quello che si deve leggere da lontano.
             */
            smallTail: Int = 0,
            smallScale: Float = SMALL_SCALE,
        ): TextPrism? {
            val laid = layout(text, typeface, sizePx, letterSpacingEm, smallTail, smallScale)
                ?: return null
            val outlines = laid.outlines
            val boxes = laid.boxes
            val reduced = laid.reduced
            val union = laid.union

            // Centrato sull'origine: la rotazione avviene attorno all'asse
            // verticale dell'intera scritta, non attorno a un angolo qualsiasi.
            val shiftX = -union.centerX()
            val shiftY = -union.centerY()

            // Il verso di percorrenza dei contorni dipende dal font, quindi non
            // si assume. Si deduce una volta sola sul contorno piu' grande di
            // tutta la scritta: dentro un font e' una convenzione unica, e
            // dedurla per carattere darebbe solo piu' occasioni di sbagliare su
            // un contorno piccolo e storto.
            var largest: FloatArray? = null
            var largestArea = 0f
            val sampledByChar = ArrayList<List<FloatArray>>(outlines.size)
            for (path in outlines) {
                path.offset(shiftX, shiftY)
                val sampled = sample(path, step)
                sampledByChar += sampled
                sampled.forEach { points ->
                    val area = abs(signedArea(points))
                    if (area > largestArea) {
                        largestArea = area
                        largest = points
                    }
                }
            }
            val reference = largest ?: return null
            val flip = pointsInward(reference)

            val parts = ArrayList<Part>(outlines.size)
            for (index in outlines.indices) {
                val allEdges = ArrayList<Float>()
                val allNormals = ArrayList<Float>()
                val starts = ArrayList<Int>()
                for (points in sampledByChar[index]) {
                    starts += allNormals.size / 2
                    val count = points.size / 2
                    val positive = signedArea(points) > 0f
                    for (k in 0 until count) {
                        val n = (k + 1) % count
                        val px = points[k * 2]
                        val py = points[k * 2 + 1]
                        val qx = points[n * 2]
                        val qy = points[n * 2 + 1]
                        val ex = qx - px
                        val ey = qy - py
                        val len = hypot(ex, ey)
                        if (len < 1e-4f) continue

                        var nx = ey / len
                        var ny = -ex / len
                        if (positive == flip) {
                            nx = -nx
                            ny = -ny
                        }

                        allEdges += px; allEdges += py; allEdges += qx; allEdges += qy
                        allNormals += nx; allNormals += ny
                    }
                }
                if (allNormals.isEmpty()) continue
                starts += allNormals.size / 2

                // Il successore di ogni spigolo, richiuso sul proprio contorno.
                // Precalcolato una volta sola perche' in disegno gli spigoli si
                // percorrono in ordine di profondita', e da li' non si puo' piu'
                // sapere dove finisce il contorno a cui si appartiene.
                val startArray = starts.toIntArray()
                val nextArray = IntArray(allNormals.size / 2)
                for (c in 0 until startArray.size - 1) {
                    val from = startArray[c]
                    val until = startArray[c + 1]
                    for (k in from until until) {
                        nextArray[k] = if (k + 1 == until) from else k + 1
                    }
                }

                val box = boxes[index]
                val edgeArray = allEdges.toFloatArray()
                val pointArray = FloatArray(edgeArray.size / 2)
                for (k in 0 until pointArray.size / 2) {
                    pointArray[k * 2] = edgeArray[k * 4]
                    pointArray[k * 2 + 1] = edgeArray[k * 4 + 1]
                }
                parts += Part(
                    edges = edgeArray,
                    normals = allNormals.toFloatArray(),
                    contourStart = startArray,
                    next = nextArray,
                    edgeCount = allNormals.size / 2,
                    points = pointArray,
                    outline = outlines[index],
                    depthScale = if (reduced[index]) smallScale else 1f,
                    left = box.left + shiftX,
                    top = box.top + shiftY,
                    right = box.right + shiftX,
                    bottom = box.bottom + shiftY,
                )
            }
            if (parts.isEmpty()) return null

            return TextPrism(
                parts = parts.sortedBy { it.centreX },
                width = union.width(),
                height = union.height(),
            )
        }

        /** I caratteri al loro posto lungo la riga, prima che diventino volume. */
        private class Layout(
            val outlines: List<android.graphics.Path>,
            val boxes: List<RectF>,
            val reduced: List<Boolean>,
            val union: RectF,
        )

        /**
         * Dove finisce ogni carattere, senza costruirne la geometria.
         *
         * E' la meta' a buon mercato del lavoro: `getTextPath` e i suoi
         * riquadri. Quella cara viene dopo - campionare i contorni e ricavarne
         * spigoli e normali - e chi vuole solo sapere quanto sara' larga la
         * scritta non ha motivo di pagarla.
         */
        private fun layout(
            text: String,
            typeface: Typeface,
            sizePx: Float,
            letterSpacingEm: Float,
            smallTail: Int,
            smallScale: Float,
        ): Layout? {
            if (text.isEmpty() || sizePx <= 0f) return null

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = sizePx
                letterSpacing = letterSpacingEm
            }

            // Ogni carattere al proprio posto lungo la riga. L'avanzamento si
            // accumula carattere per carattere e non si rimisura dal prefisso:
            // il prefisso e la coda possono avere corpi diversi, e una misura
            // sola non saprebbe di quale dei due parlare.
            val bigUntil = text.length - smallTail.coerceIn(0, text.length)
            val outlines = ArrayList<android.graphics.Path>(text.length)
            val boxes = ArrayList<RectF>(text.length)
            val reduced = ArrayList<Boolean>(text.length)
            val big = RectF()
            var pen = 0f
            for (i in text.indices) {
                val small = i >= bigUntil
                paint.textSize = if (small) sizePx * smallScale else sizePx
                if (small && i == bigUntil) pen += sizePx * SYMBOL_GAP
                val path = android.graphics.Path()
                paint.getTextPath(text, i, i + 1, pen, 0f, path)
                pen += paint.measureText(text, i, i + 1)
                val box = RectF()
                path.computeBounds(box, true)
                if (box.width() <= 0f || box.height() <= 0f) continue
                outlines += path
                boxes += box
                reduced += small
                if (!small) {
                    if (big.isEmpty) big.set(box) else big.union(box)
                }
            }
            if (outlines.isEmpty()) return null

            // Il simbolo sale a filo della cima delle cifre. Sulla linea di base
            // resterebbe a mezza altezza, dove non si legge come esponente ma
            // come un carattere rimpicciolito per sbaglio. Alzandolo, il
            // riquadro complessivo resta quello delle cifre e la centratura
            // verticale non si accorge di lui.
            if (!big.isEmpty) {
                for (k in outlines.indices) {
                    if (!reduced[k]) continue
                    val lift = big.top - boxes[k].top
                    outlines[k].offset(0f, lift)
                    boxes[k].offset(0f, lift)
                }
            }

            val union = RectF()
            for (box in boxes) {
                if (union.isEmpty) union.set(box) else union.union(box)
            }
            if (union.width() <= 0f || union.height() <= 0f) return null

            return Layout(outlines, boxes, reduced, union)
        }

        /**
         * Quanto sara' larga la scritta, senza costruirla.
         *
         * Serve a decidere il corpo **prima** di estrarre la geometria. Chi
         * doveva rimpicciolire una cifra troppo larga la costruiva intera, la
         * misurava, e poi **la ricostruiva da capo**: due campionamenti di tutti
         * i contorni per sapere un numero che si poteva avere con tre chiamate a
         * `getTextPath`. Il valore e' esattamente quello che tornerebbe
         * `TextPrism.of(...).width`, perche' e' lo stesso conto.
         */
        fun widthOf(
            text: String,
            typeface: Typeface,
            sizePx: Float,
            letterSpacingEm: Float = 0f,
            smallTail: Int = 0,
            smallScale: Float = SMALL_SCALE,
        ): Float = layout(text, typeface, sizePx, letterSpacingEm, smallTail, smallScale)
            ?.union?.width() ?: 0f

        private fun sample(path: android.graphics.Path, step: Float): List<FloatArray> {
            val sampled = ArrayList<FloatArray>()
            val measure = PathMeasure(path, true)
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
            return sampled
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
