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
    private val visible = BooleanArray(maxEdges)

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

        /** Falso quando l'oggetto e' esattamente di taglio e la base non ha superficie. */
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
     * La sola matrice della base di un carattere, senza costruirne le pareti.
     *
     * Serve a disegnare tutte le ombre prima di tutti i corpi. Disegnandole
     * insieme al proprio carattere, l'ombra di quello vicino finiva sulla faccia
     * di quello lontano e gliela ingrigiva: girando la scena di mezzo passo, una
     * cifra diventava sporca senza motivo apparente. L'ombra qui e' un disegno,
     * non un fenomeno: sta sul fondo, non sugli altri oggetti.
     *
     * Scrive in [shadowTransform]. Torna falso se la base e' di taglio.
     */
    fun capTransform(index: Int, camera: Camera, depth: Float): Boolean {
        val part = parts[index]
        camera.normal(0f, 0f, -1f)
        val zCap = if (camera.nvz < 0f) -depth / 2f else depth / 2f
        cornersLocal[0] = part.left; cornersLocal[1] = part.top
        cornersLocal[2] = part.right; cornersLocal[3] = part.top
        cornersLocal[4] = part.right; cornersLocal[5] = part.bottom
        cornersLocal[6] = part.left; cornersLocal[7] = part.bottom
        for (k in 0 until 4) {
            camera.place(cornersLocal[k * 2], cornersLocal[k * 2 + 1], zCap)
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
        val half = depth / 2f
        val bevel = chamfer.coerceIn(0f, depth * 0.4f)

        // Girando oltre il quarto di giro si finisce a guardare l'oggetto da
        // dietro, e allora la base che si vede e' l'altra. Disegnare sempre
        // quella davanti la stamperebbe sopra le pareti che dovrebbero
        // nasconderla, e la cifra sembrerebbe trasparente. Un prisma non ha un
        // davanti assoluto: ha una base rivolta all'occhio e una no.
        camera.normal(0f, 0f, -1f)
        val frontToViewer = camera.nvz < 0f
        val zCap = if (frontToViewer) -half else half
        val zBevel = if (frontToViewer) -half + bevel else half - bevel
        val zBack = -zCap
        val bevelZ = if (frontToViewer) -DIAGONAL else DIAGONAL

        camera.normal(0f, 0f, if (frontToViewer) -1f else 1f)
        val capLambert = camera.lambert(light)

        classify(part, camera, light, bevelZ)

        // ## Quattro passate, e nessuna parete buttata via
        //
        // Qui prima si disegnavano le sole pareti rivolte all'occhio, scartando
        // le altre. E' il gesto giusto quando c'e' un buffer di profondita', e
        // quello sbagliato quando non c'e': **su un solido non convesso le
        // pareti frontali piu' la base non coprono tutta la sagoma**. Dentro
        // l'occhiello del 9, nel ricciolo del 2, lungo ogni rientranza restano
        // regioni dove nessuna superficie rivolta all'occhio arriva, e li' si
        // vedeva il cielo attraverso la cifra - la "scatola bucata".
        //
        // Le pareti di spalle sono l'unica cosa che possa riempirle. Si
        // disegnano per prime, cosi' quelle davanti le coprono dove si
        // sovrappongono, e l'ordine fra corpo e smusso resta quello di sempre
        // dentro ciascuna passata: lo smusso e' piu' vicino all'occhio e deve
        // stare sopra il proprio corpo.
        var n = emitRing(part, camera, zBevel, zBack, wallLambert, ink, bevel = false, back = true, at = 0)
        if (bevel > 0f) {
            n = emitRing(part, camera, zCap, zBevel, bevelLambert, ink, bevel = true, back = true, at = n)
        }
        n = emitRing(part, camera, zBevel, zBack, wallLambert, ink, bevel = false, back = false, at = n)
        if (bevel > 0f) {
            n = emitRing(part, camera, zCap, zBevel, bevelLambert, ink, bevel = true, back = false, at = n)
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
        surfaces.capVisible = mapped
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
    private fun classify(part: Part, camera: Camera, light: Light, bevelZ: Float) {
        for (i in 0 until part.edgeCount) {
            camera.normal(part.normals[i * 2], part.normals[i * 2 + 1], 0f)
            camera.place(
                (part.edges[i * 4] + part.edges[i * 4 + 2]) / 2f,
                (part.edges[i * 4 + 1] + part.edges[i * 4 + 3]) / 2f,
                0f,
            )
            // Non piu' una prova per scartare, ma per **ordinare**: dice in
            // quale delle due passate va questo spigolo. Vedi la nota in
            // [shape] sul perche' nessuna parete si butti piu' via.
            visible[i] = camera.facesViewer()
        }

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
    }

    /** Una corona di quadrilateri fra due piani, spezzata in triangoli. */
    private fun emitRing(
        part: Part,
        camera: Camera,
        zNear: Float,
        zFar: Float,
        lambert: FloatArray,
        ink: Ink,
        bevel: Boolean,
        /** Vero per la passata delle pareti di spalle, falso per quelle davanti. */
        back: Boolean,
        at: Int,
    ): Int {
        var n = at
        for (c in 0 until part.contourStart.size - 1) {
            val from = part.contourStart[c]
            val until = part.contourStart[c + 1]
            for (i in from until until) {
                if (visible[i] == back) continue
                val next = if (i + 1 == until) from else i + 1

                // Una parete di spalle che si vede attraverso una rientranza va
                // illuminata come se fosse rivolta a noi, perche' li' e' il
                // materiale a essere rivolto a noi: e' l'interno della
                // concavita', non il retro del guscio. Con l'esposizione vera
                // resterebbe quasi nera e la rientranza si leggerebbe come un
                // buco, che e' esattamente il difetto da togliere.
                //
                // Ribaltare la normale e' gratis: il Lambert dimezzato vale
                // 0,5 + 0,5·(n·luce), quindi quello della normale opposta e'
                // il suo complemento a uno. Nessun secondo vettore da tenere.
                val startLambert = if (back) 1f - lambert[i] else lambert[i]
                val endLambert = if (back) 1f - lambert[next] else lambert[next]
                val startColour = colourOf(startLambert, ink, bevel)
                val endColour = colourOf(endLambert, ink, bevel)

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
            }
        }
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
        ): TextPrism? {
            if (text.isEmpty() || sizePx <= 0f) return null

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = sizePx
                letterSpacing = letterSpacingEm
            }

            // Ogni carattere al proprio posto lungo la riga, misurato dal
            // prefisso che lo precede: cosi' la spaziatura e' quella che il font
            // applicherebbe scrivendo la parola intera.
            val outlines = ArrayList<android.graphics.Path>(text.length)
            val boxes = ArrayList<RectF>(text.length)
            val union = RectF()
            for (i in text.indices) {
                val path = android.graphics.Path()
                paint.getTextPath(text, i, i + 1, paint.measureText(text, 0, i), 0f, path)
                val box = RectF()
                path.computeBounds(box, true)
                if (box.width() <= 0f || box.height() <= 0f) continue
                outlines += path
                boxes += box
                if (union.isEmpty) union.set(box) else union.union(box)
            }
            if (outlines.isEmpty() || union.width() <= 0f || union.height() <= 0f) return null

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
                    contourStart = starts.toIntArray(),
                    edgeCount = allNormals.size / 2,
                    points = pointArray,
                    outline = outlines[index],
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
