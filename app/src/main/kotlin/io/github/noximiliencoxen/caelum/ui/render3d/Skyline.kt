package io.github.noximiliencoxen.caelum.ui.render3d

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset

/**
 * La sagoma di un oggetto vista da sopra: per ogni colonna verticale dello
 * schermo, il punto piu' alto che occupa.
 *
 * Serve a far cadere la pioggia **sopra** la cifra invece che dietro. Un
 * confronto col riquadro non basterebbe: fra una cifra e l'altra, e dentro i
 * vuoti dello zero, non c'e' niente da bagnare, e una goccia che si ferma a
 * mezz'aria si vede subito. Colonna per colonna, invece, la sagoma vera decide
 * da sola dove c'e' superficie e dove si passa.
 *
 * Non e' uno stato osservabile e non deve esserlo: viene riscritta e riletta
 * dentro il disegno, sullo stesso filo. Chi la legge ridisegna comunque a ogni
 * fotogramma, quindi non c'e' niente da invalidare.
 */
class Skyline {

    private var top = FloatArray(0)
    private var width = 0f

    /** Vero se in questo fotogramma qualcuno l'ha riempita. */
    var ready: Boolean = false
        private set

    /** Dove sta il riquadro di chi l'ha scritta, rispetto alla radice. */
    var origin: Offset = Offset.Zero

    /**
     * Il punto piu' basso toccato dalla sagoma: la base della cifra.
     *
     * La cima serve a sapere dove la pioggia si ferma; questa a sapere dove
     * **arriva** quando non incontra niente. Ne basta uno per tutta la scritta e
     * non uno per colonna: e' un piano d'appoggio, e un piano d'appoggio a
     * gradini non e' un piano.
     */
    var floor: Float = Float.NaN
        private set

    fun reset(canvasWidth: Float) {
        val needed = ((canvasWidth / COLUMN_PX).toInt() + 2).coerceAtLeast(2)
        if (top.size != needed) top = FloatArray(needed)
        java.util.Arrays.fill(top, Float.MAX_VALUE)
        width = canvasWidth
        floor = Float.NaN
        ready = false
    }

    /**
     * Aggiunge un tratto del contorno, gia' proiettato.
     *
     * Un tratto e non un punto, e la differenza e' tutto. Segnando i soli punti
     * campionati, fra l'uno e l'altro restavano colonne vuote - il contorno e'
     * campionato ogni dodici o quindici pixel e le colonne ne sono larghe sei -
     * e in quelle colonne la pioggia passava attraverso la cifra come se non ci
     * fosse. Percorrendo il tratto si riempie ogni colonna che attraversa.
     */
    fun addSpan(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (top.isEmpty()) return
        val from = minOf(x0, x1)
        val to = maxOf(x0, x1)
        var column = (from / COLUMN_PX).toInt()
        val last = (to / COLUMN_PX).toInt()
        val run = x1 - x0

        while (column <= last) {
            if (column in top.indices) {
                val y = if (kotlin.math.abs(run) < 1e-3f) {
                    minOf(y0, y1)
                } else {
                    // Il tratto e' dritto: il punto piu' alto dentro la colonna
                    // sta a uno dei due bordi, non serve cercarlo in mezzo.
                    val head = (((column * COLUMN_PX) - x0) / run).coerceIn(0f, 1f)
                    val tail = ((((column + 1) * COLUMN_PX) - x0) / run).coerceIn(0f, 1f)
                    minOf(y0 + (y1 - y0) * head, y0 + (y1 - y0) * tail)
                }
                if (y < top[column]) top[column] = y
            }
            column++
        }
        val lowest = maxOf(y0, y1)
        if (floor.isNaN() || lowest > floor) floor = lowest
        ready = true
    }

    /**
     * Il punto piu' alto occupato a una certa ascissa, o [Float.NaN] se in
     * quella colonna non c'e' nulla.
     */
    fun topAt(x: Float): Float {
        if (!ready || top.isEmpty()) return Float.NaN
        val column = (x / COLUMN_PX).toInt()
        if (column !in top.indices) return Float.NaN
        val value = top[column]
        return if (value == Float.MAX_VALUE) Float.NaN else value
    }

    private companion object {
        /**
         * Larghezza di una colonna. Sei pixel: piu' fine non si distingue a
         * occhio, piu' grosso e le gocce cominciano a fermarsi sul vuoto accanto
         * alla cifra.
         */
        const val COLUMN_PX = 6f
    }
}

/**
 * Quello che due disegni separati devono sapere l'uno dell'altro.
 *
 * La scultura e la cifra stanno in due tele distinte perche' il loro spazio nel
 * riquadro lo decide la disposizione, non il disegno. Ma sono la stessa scena, e
 * la pioggia che esce dalla nuvola deve sapere dove comincia la cifra: qui
 * passano la sagoma e le due origini, che bastano a tradurre l'una nell'altra.
 */
@Stable
class SceneContact {
    val skyline = Skyline()

    /** Origine del riquadro della pioggia rispetto alla radice. */
    var rainOrigin: Offset = Offset.Zero

    /** Lo scostamento da sommare a una coordinata della cifra per leggerla nella pioggia. */
    val numberToRain: Offset get() = skyline.origin - rainOrigin

    /**
     * Quanto illumina il lampo in questo istante, da 0 a 1.
     *
     * Scritto dalla scultura a ogni fotogramma e letto dalla cifra, che sta in
     * una tela separata ma disegna subito dopo nello stesso frame: stessa
     * regola di [rainOrigin], stato non osservabile perche' cambia a ogni
     * fotogramma comunque.
     */
    var glare: Float = 0f
}
