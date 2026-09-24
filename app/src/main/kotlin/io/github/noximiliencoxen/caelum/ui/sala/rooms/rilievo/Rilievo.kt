package io.github.noximiliencoxen.caelum.ui.sala.rooms.rilievo

import io.github.noximiliencoxen.caelum.data.HourForecast
import java.time.LocalDate

/**
 * La settimana come una superficie: un giorno per riga, un'ora per colonna,
 * l'altezza e' la temperatura.
 *
 * Qui solo i numeri - la griglia, i suoi estremi, e quale punto sta sotto un
 * dito - perche' si provano sulla JVM (`RilievoTest`). Il disegno sta in
 * [RilievoSettimana].
 *
 * @property temperature una riga per giorno, ventiquattro valori per riga; un
 *   buco nella previsione e' `NaN`, e il disegno lo salta.
 */
internal class Rilievo(
    val giorni: List<LocalDate>,
    val temperature: Array<FloatArray>,
) {
    val righe: Int get() = giorni.size
    val colonne: Int get() = ORE

    val minima: Float = temperature.minOf { r -> r.filter { it.isFinite() }.minOrNull() ?: Float.MAX_VALUE }
    val massima: Float = temperature.maxOf { r -> r.filter { it.isFinite() }.maxOrNull() ?: -Float.MAX_VALUE }

    /** La temperatura riportata fra 0 (la minima della settimana) e 1 (la massima). */
    fun quota(giorno: Int, ora: Int): Float {
        val t = temperature[giorno][ora]
        if (!t.isFinite()) return 0f
        val salto = (massima - minima).takeIf { it > 0.01f } ?: return 0.5f
        return (t - minima) / salto
    }

    companion object {
        const val ORE = 24

        /**
         * Dalle ore della previsione: i primi [giorniMassimi] giorni completi o
         * quasi. Nullo se non ce n'e' nemmeno due, perche' una superficie di una
         * riga sola e' una linea e si legge meglio come tale.
         */
        fun da(ore: List<HourForecast>, giorniMassimi: Int = 7): Rilievo? {
            val perGiorno = ore.groupBy { it.time.toLocalDate() }.toSortedMap()
            val giorni = perGiorno.keys.take(giorniMassimi)
            if (giorni.size < 2) return null
            val temperature = Array(giorni.size) { r ->
                val riga = FloatArray(ORE) { Float.NaN }
                perGiorno.getValue(giorni[r]).forEach { h ->
                    h.temperature?.let { riga[h.time.hour] = it.toFloat() }
                }
                riga
            }
            val rilievo = Rilievo(giorni, temperature)
            return if (rilievo.minima <= rilievo.massima) rilievo else null
        }

        /**
         * L'indice del punto proiettato piu' vicino a ([x], [y]), fra quelli
         * visibili, o -1 se il piu' vicino e' oltre [raggio]. [punti] alterna x e
         * y, un punto per nodo della griglia; [visibile] dice quali contano.
         */
        fun piuVicino(punti: FloatArray, visibile: BooleanArray, x: Float, y: Float, raggio: Float): Int {
            var migliore = -1
            var distanza = raggio * raggio
            for (i in visibile.indices) {
                if (!visibile[i]) continue
                val dx = punti[i * 2] - x
                val dy = punti[i * 2 + 1] - y
                val d = dx * dx + dy * dy
                if (d <= distanza) {
                    distanza = d
                    migliore = i
                }
            }
            return migliore
        }
    }
}
