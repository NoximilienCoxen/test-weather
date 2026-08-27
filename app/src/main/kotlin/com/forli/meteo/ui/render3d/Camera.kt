package com.forli.meteo.ui.render3d

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Camera prospettica: porta un punto dal sistema dell'oggetto a quello dello
 * schermo.
 *
 * E' il pezzo che mancava. L'implementazione precedente estrudeva il contorno
 * lungo un vettore e ruotava la luce: cambiavano l'ombreggiatura e lo spessore,
 * ma la sagoma vista di fronte restava identica a se stessa. Ruotare un oggetto
 * vero accorcia la faccia frontale e scopre il fianco, e questo si ottiene solo
 * trasformando i vertici e dividendo per la profondita'.
 *
 * Il risultato viene scritto in campi invece di essere restituito: un fotogramma
 * proietta qualche migliaio di punti, e allocare un oggetto per ciascuno darebbe
 * al raccoglitore piu' lavoro del disegno stesso.
 */
class Camera(
    yawDeg: Float,
    pitchDeg: Float,
    /**
     * Distanza dell'occhio dal piano mediano dell'oggetto, in pixel.
     * Piu' e' corta, piu' la prospettiva e' marcata: sotto un paio di volte la
     * dimensione dell'oggetto le facce vicine esplodono e sembra un grandangolo.
     */
    val distance: Float,
    /** Dove finisce sullo schermo il centro dell'oggetto. */
    val origin: Offset,
) {

    private val cosYaw = cos(yawDeg * DEG)
    private val sinYaw = sin(yawDeg * DEG)
    private val cosPitch = cos(pitchDeg * DEG)
    private val sinPitch = sin(pitchDeg * DEG)

    /** Ultimo punto sistemato, in coordinate di vista. */
    var vx = 0f; private set
    var vy = 0f; private set
    var vz = 0f; private set

    /** Ultimo punto sistemato, in pixel di schermo. */
    var sx = 0f; private set
    var sy = 0f; private set

    /** Rimpicciolimento prospettico dell'ultimo punto. Vale 1 sul piano mediano. */
    var scale = 1f; private set

    /** Ultima normale orientata, in coordinate di vista. */
    var nvx = 0f; private set
    var nvy = 0f; private set
    var nvz = 0f; private set

    /** Ruota una direzione senza proiettarla: serve alle normali. */
    fun normal(x: Float, y: Float, z: Float) {
        val x1 = x * cosYaw + z * sinYaw
        val z1 = -x * sinYaw + z * cosYaw
        nvx = x1
        nvy = y * cosPitch - z1 * sinPitch
        nvz = y * sinPitch + z1 * cosPitch
    }

    /** Ruota un punto e lo proietta. */
    fun place(x: Float, y: Float, z: Float) {
        val x1 = x * cosYaw + z * sinYaw
        val z1 = -x * sinYaw + z * cosYaw
        vx = x1
        vy = y * cosPitch - z1 * sinPitch
        vz = y * sinPitch + z1 * cosPitch
        // Il minimo evita la divisione per zero se un vertice finisse
        // esattamente sull'occhio: a quel punto l'immagine e' persa comunque, ma
        // almeno non produce infiniti che sporcherebbero tutto il tracciato.
        scale = distance / (distance + vz).coerceAtLeast(distance * 0.2f)
        sx = origin.x + vx * scale
        sy = origin.y + vy * scale
    }

    /**
     * Vero se la superficie con l'ultima normale, nell'ultimo punto sistemato,
     * e' rivolta verso l'occhio. Con la prospettiva non basta guardare il segno
     * di z: la direzione di vista cambia da punto a punto.
     */
    fun facesViewer(): Boolean =
        nvx * vx + nvy * vy + nvz * (vz + distance) < 0f

    /**
     * Lambert dimezzato invece che troncato a zero.
     *
     * Il troncamento classico manda a zero tutto l'emisfero in ombra: le facce
     * di spalle diventano tutte esattamente dello stesso tono e il volume si
     * legge come una massa unica. Rimappando l'intero intervallo restano
     * distinguibili fra loro per quanto sono girate, che e' l'informazione che
     * racconta la forma.
     */
    fun lambert(light: Light): Float =
        (0.5f + 0.5f * (nvx * light.x + nvy * light.y + nvz * light.z)).coerceIn(0f, 1f)

    private companion object {
        const val DEG = (Math.PI / 180.0).toFloat()
    }
}

/**
 * Luce direzionale fissa rispetto allo schermo: da sinistra in alto e davanti.
 *
 * Fissa e non solidale all'oggetto: e' questo che la rende utile. Ruotando, una
 * faccia entra nella luce e l'altra ne esce, e lo scambio si legge come
 * rotazione ancora prima che la sagoma cambi.
 */
class Light(x: Float, y: Float, z: Float) {
    val x: Float
    val y: Float
    val z: Float

    init {
        val len = sqrt(x * x + y * y + z * z).takeIf { it > 1e-4f } ?: 1f
        this.x = x / len
        this.y = y / len
        this.z = z / len
    }

    companion object {
        /**
         * Marcatamente laterale. Con una luce quasi frontale la faccia della
         * cifra cambia tono di pochi punti percentuali fra un estremo e l'altro
         * della rotazione, cioe' non cambia affatto per chi guarda.
         */
        val Standard = Light(-0.58f, -0.55f, -0.60f)
    }
}
