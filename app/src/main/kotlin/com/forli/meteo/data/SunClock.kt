package com.forli.meteo.data

import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Dove sta il sole a una certa ora, e cosa ne consegue per quello che si vede.
 *
 * Tutto discende da un solo numero, [altitude], e non e' un vezzo: e' l'unico
 * modo di far scorrere il cielo con continuita'. Un'unica quantita' si puo'
 * animare fra due ore, e da quella si ricavano insieme il colore del fondo, il
 * rosso del sole e la comparsa della luna, sempre d'accordo fra loro. Animando
 * invece i cinque valori separatamente si arriverebbe a istanti in cui il sole
 * e' gia' sparito ma il cielo e' ancora di giorno.
 */
data class SkyState(
    /**
     * Altezza del sole, da -1 (notte piena) a 1 (mezzogiorno).
     * Non e' l'elevazione in gradi: e' una misura comoda che vale zero
     * all'orizzonte, che e' l'unico punto in cui deve essere esatta.
     */
    val altitude: Float,
    /** Quanto e' giorno: governa il fondo e il colore dei testi. */
    val dayness: Float,
    /** Quanto si vede il sole. */
    val sunPresence: Float,
    /** Quanto si vede la luna. Si sovrappone al sole nel crepuscolo, come al vero. */
    val moonPresence: Float,
    /** 1 quando il sole e' all'orizzonte, 0 quando e' alto. Rosso contro giallo. */
    val redness: Float,
    /**
     * Quanto si e' dentro l'ora dorata, da 0 a 1.
     *
     * Non si ricava dall'altezza: [redness] vale uno per tutto il tempo in cui
     * il sole sta basso, che d'inverno alle latitudini alte e' mezza giornata,
     * e a mezzogiorno di dicembre a Tromso il cielo non e' arancione. Questo
     * invece misura i minuti che mancano davvero all'alba o al tramonto, che e'
     * l'unica cosa che decide se la luce e' calda.
     */
    val golden: Float = 0f,
) {
    companion object {
        fun of(altitude: Float, golden: Float = 0f): SkyState = SkyState(
            altitude = altitude,
            golden = golden.coerceIn(0f, 1f),
            // Il cielo si schiarisce molto prima che il sole spunti: mezz'ora
            // prima dell'alba fuori ci si vede benissimo. Facendo coincidere il
            // buio con il sole sotto l'orizzonte si otteneva una notte piena
            // alle sei del mattino, che nessuno riconoscerebbe come tale.
            dayness = SunClock.smoothstep(-0.62f, 0.22f, altitude),
            sunPresence = SunClock.smoothstep(-0.14f, 0.05f, altitude),
            moonPresence = 1f - SunClock.smoothstep(-0.42f, -0.08f, altitude),
            redness = 1f - SunClock.smoothstep(0.02f, 0.34f, altitude),
        )

        val Giorno = of(0.8f)
    }
}

object SunClock {

    /**
     * Quanto dura il crepuscolo, in minuti: il tempo che il cielo impiega a
     * finire di scurirsi dopo che il sole e' sceso sotto l'orizzonte.
     */
    private const val TWILIGHT_MINUTES = 110f

    /**
     * @param sunrise e [sunset] possono mancare: alle latitudini alte esistono
     *   giorni in cui il sole non sorge o non tramonta affatto, e l'API in quel
     *   caso non ha un'ora da dare. Li' l'unico dato disponibile e' il `is_day`
     *   dell'ora stessa, e basta.
     */
    fun altitude(
        moment: LocalDateTime,
        sunrise: LocalDateTime?,
        sunset: LocalDateTime?,
        fallbackIsDay: Boolean,
    ): Float {
        if (sunrise == null || sunset == null) return if (fallbackIsDay) 0.62f else -0.85f

        val t = minutesOfDay(moment)
        val rise = minutesOfDay(sunrise)
        val set = minutesOfDay(sunset)
        if (set <= rise) return if (fallbackIsDay) 0.62f else -0.85f

        return when {
            t < rise -> -((rise - t) / TWILIGHT_MINUTES).coerceAtMost(1f)
            t > set -> -((t - set) / TWILIGHT_MINUTES).coerceAtMost(1f)
            // Una campana fra alba e tramonto. Non e' l'elevazione vera, ma ha
            // gli zeri nei punti giusti e il massimo a meta' giornata, che e'
            // tutto quello che serve per decidere un colore.
            else -> sin(PI * (t - rise) / (set - rise)).toFloat()
        }
    }

    /**
     * Quanto si e' vicini all'alba o al tramonto, da 0 a 1.
     *
     * Uno esatto sull'evento, zero a [GOLDEN_MINUTES] di distanza. Serve a
     * tingere di caldo la luce della scena e il fondo prima che il tema scivoli
     * nel giorno o nella notte: e' la mezz'ora in cui tutto e' arancione, e
     * finora l'app la attraversava senza accorgersene.
     */
    fun goldenness(
        moment: LocalDateTime,
        sunrise: LocalDateTime?,
        sunset: LocalDateTime?,
    ): Float {
        val events = listOfNotNull(sunrise, sunset)
        if (events.isEmpty()) return 0f
        val t = minutesOfDay(moment)
        // Il giro di mezzanotte conta: alle latitudini alte l'alba puo' cadere a
        // ridosso delle ventiquattro, e senza la differenza ciclica un'ora
        // distante dieci minuti ne risulterebbe distante milletrecento.
        val nearest = events.minOf { event ->
            val gap = abs(t - minutesOfDay(event))
            minOf(gap, MINUTES_IN_DAY - gap)
        }
        return 1f - smoothstep(0f, GOLDEN_MINUTES, nearest)
    }

    /** L'ampiezza della finestra dorata, in minuti, da una parte e dall'altra. */
    const val GOLDEN_MINUTES = 45f

    private const val MINUTES_IN_DAY = 1440f

    private fun minutesOfDay(moment: LocalDateTime): Float =
        moment.hour * 60f + moment.minute + moment.second / 60f

    /** Transizione morbida: senza, i colori cambierebbero a scatti. */
    fun smoothstep(from: Float, to: Float, value: Float): Float {
        if (to <= from) return if (value >= to) 1f else 0f
        val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
