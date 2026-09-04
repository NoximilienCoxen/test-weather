package io.github.noximiliencoxen.caelum.ui.home

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos

/**
 * Fase lunare calcolata in locale: Open-Meteo non la fornisce, e per disegnare
 * una mediana basta il mese sinodico medio a partire da un novilunio noto.
 * L'errore accumulato resta ben sotto il giorno per gli anni che ci
 * interessano, cioe' invisibile a questa dimensione.
 */
object MoonPhase {

    private val KNOWN_NEW_MOON: LocalDate = LocalDate.of(2000, 1, 6)
    private const val SYNODIC_DAYS = 29.530588853

    /** 0 = novilunio, 0.5 = plenilunio, 1 = novilunio successivo. */
    fun at(date: LocalDate): Float {
        val days = ChronoUnit.DAYS.between(KNOWN_NEW_MOON, date).toDouble()
        val cycles = days / SYNODIC_DAYS
        return ((cycles - kotlin.math.floor(cycles)).toFloat()).coerceIn(0f, 1f)
    }

    /** Frazione illuminata, da 0 a 1. */
    fun illumination(phase: Float): Float =
        ((1f - cos(2.0 * Math.PI * phase).toFloat()) / 2f).coerceIn(0f, 1f)

    /**
     * Semiasse orizzontale del terminatore, in frazione del raggio.
     * Zero al quarto, uno ai due estremi del ciclo.
     */
    fun terminator(phase: Float): Float = abs(cos(2.0 * Math.PI * phase).toFloat())

    /** Vero da novilunio a plenilunio: la parte illuminata sta a destra. */
    fun waxing(phase: Float): Boolean = phase < 0.5f

    /**
     * L'eta' della luna in giorni: quanto e' avanti nel suo mese.
     *
     * E' il modo in cui la luna si nomina in astronomia, e dice a colpo d'occhio
     * se si e' all'inizio o alla fine del ciclo. Sta qui e non presso chi la
     * mostra perche' il mese sinodico e' un numero solo, e chi lo ricopia se ne
     * prende una seconda copia da tenere allineata.
     */
    fun ageDays(phase: Float): Float = phase * SYNODIC_DAYS.toFloat()

    /**
     * Fra quanti giorni la luna sara' a [target] della sua fase.
     *
     * Serve a dire quando cade il prossimo plenilunio o il prossimo novilunio.
     * Il conto e' sul ciclo, non sul calendario: la distanza si prende sempre in
     * avanti, quindi chiedere il plenilunio il giorno dopo il plenilunio
     * risponde "fra ventinove giorni" e non "meno uno".
     */
    fun daysUntil(from: LocalDate, target: Float): Int {
        val ahead = (target - at(from) + 1f) % 1f
        return kotlin.math.ceil(ahead * SYNODIC_DAYS).toInt()
    }

    /** La data in cui la luna sara' a [target] della sua fase. */
    fun nextDate(from: LocalDate, target: Float): LocalDate =
        from.plusDays(daysUntil(from, target).toLong())
}

/**
 * Gli otto nomi con cui si chiama la luna.
 *
 * Restano i nomi, non le sagome: la fase viene tracciata per intero, e scegliere
 * fra otto disegni fissi mostrava la luna di due giorni prima.
 *
 * **Stanno qui e non piu' nel widget** perche' adesso li chiedono in due - il
 * widget e la pagina della luna nel dettaglio - e la domanda "come si chiama
 * questa fase" e' della luna, non di chi la disegna.
 */
enum class MoonSegment(val label: String) {
    NOVILUNIO("NOVILUNIO"),
    CRESCENTE("LUNA CRESCENTE"),
    PRIMO_QUARTO("PRIMO QUARTO"),
    GIBBOSA_CRESCENTE("GIBBOSA CRESCENTE"),
    PLENILUNIO("PLENILUNIO"),
    GIBBOSA_CALANTE("GIBBOSA CALANTE"),
    ULTIMO_QUARTO("ULTIMO QUARTO"),
    CALANTE("LUNA CALANTE"),
    ;

    companion object {
        /** Il ciclo diviso in otto, con i nomi centrati sul loro istante esatto. */
        fun of(phase: Float): MoonSegment {
            val eighth = ((phase * 8f) + 0.5f).toInt() % 8
            return entries[eighth]
        }
    }
}
