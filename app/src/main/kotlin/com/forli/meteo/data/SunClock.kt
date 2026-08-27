package com.forli.meteo.data

import java.time.LocalDateTime
import kotlin.math.PI
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
) {
    companion object {
        fun of(altitude: Float): SkyState = SkyState(
            altitude = altitude,
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

    private fun minutesOfDay(moment: LocalDateTime): Float =
        moment.hour * 60f + moment.minute + moment.second / 60f

    /** Transizione morbida: senza, i colori cambierebbero a scatti. */
    fun smoothstep(from: Float, to: Float, value: Float): Float {
        if (to <= from) return if (value >= to) 1f else 0f
        val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
