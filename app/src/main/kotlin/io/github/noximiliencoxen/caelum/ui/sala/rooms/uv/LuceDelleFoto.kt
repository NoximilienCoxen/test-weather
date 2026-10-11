package io.github.noximiliencoxen.caelum.ui.sala.rooms.uv

import io.github.noximiliencoxen.caelum.data.HourForecast
import java.time.LocalDate
import java.time.LocalDateTime

/** Un tratto di tempo sull'orologio del posto. */
data class Fascia(val inizio: LocalDateTime, val fine: LocalDateTime)

/**
 * Le due luci che i fotografi aspettano, mattina e sera.
 *
 * **Ora d'oro**: il sole fra quattro gradi sotto e sei sopra l'orizzonte, la
 * luce bassa e calda con le ombre lunghe. **Ora blu**: fra sei e quattro gradi
 * sotto, il cielo ancora chiaro senza il sole. Sono le soglie piu' usate, e non
 * c'e' un ente che le fissi: una scelta, dichiarata qui.
 *
 * Al mattino viene prima la blu, la sera prima l'oro. Nulle quando non ci
 * sono: d'estate a Tromsø il sole non scende abbastanza, e un orario inventato
 * sarebbe peggio di un trattino.
 */
data class LuceDelGiorno(
    val bluMattina: Fascia?,
    val oroMattina: Fascia?,
    val oroSera: Fascia?,
    val bluSera: Fascia?,
)

const val ORO_SOTTO = -4.0
const val ORO_SOPRA = 6.0
const val BLU_SOTTO = -6.0

/**
 * Minuto per minuto, dall'altezza vera del sole (`OmbraSole.altezza`).
 *
 * Millequattrocentoquaranta conti di trigonometria: niente, rispetto a una
 * formula chiusa per ogni soglia che andrebbe poi difesa ai poli. La giornata
 * si divide al minuto in cui il sole e' piu' alto, cosi' "mattina" e "sera"
 * sono le due meta' vere e non le dodici dell'orologio.
 */
fun luceDelGiorno(data: LocalDate, latitudine: Double, longitudine: Double, scartoUtcSecondi: Int): LuceDelGiorno {
    val mezzanotte = data.atStartOfDay()
    val altezze = DoubleArray(MINUTI) { m ->
        OmbraSole.altezza(mezzanotte.plusMinutes(m.toLong()), latitudine, longitudine, scartoUtcSecondi)
    }
    val culmine = altezze.indices.maxBy { altezze[it] }

    fun fascia(da: Int, a: Int, dentro: (Double) -> Boolean): Fascia? {
        val minuti = (da until a).filter { dentro(altezze[it]) }
        if (minuti.isEmpty()) return null
        return Fascia(mezzanotte.plusMinutes(minuti.first().toLong()), mezzanotte.plusMinutes(minuti.last() + 1L))
    }
    val oro: (Double) -> Boolean = { it >= ORO_SOTTO && it < ORO_SOPRA }
    val blu: (Double) -> Boolean = { it >= BLU_SOTTO && it < ORO_SOTTO }
    return LuceDelGiorno(
        bluMattina = fascia(0, culmine, blu),
        oroMattina = fascia(0, culmine, oro),
        oroSera = fascia(culmine, MINUTI, oro),
        bluSera = fascia(culmine, MINUTI, blu),
    )
}

/** Che cielo ci sara' sopra quella luce: decide se vale la pena uscire. */
enum class CieloDellaLuce { LIBERO, SPARSO, COPERTO }

/**
 * La nuvolosita' media delle ore che la [fascia] tocca.
 *
 * Le soglie sono quelle di chi fotografa tramonti: poche nuvole e la luce passa
 * pulita; qualche nuvola e si accendono di colore, che e' il tramonto che si
 * ricorda; coperto e la luce arriva piatta, senza oro.
 */
fun cieloDellaLuce(fascia: Fascia?, ore: List<HourForecast>): CieloDellaLuce? {
    if (fascia == null) return null
    val prima = fascia.inizio.withMinute(0)
    val coperture = ore
        .filter { !it.time.isBefore(prima) && it.time.isBefore(fascia.fine) }
        .mapNotNull { it.cloudCover }
    if (coperture.isEmpty()) return null
    val media = coperture.average()
    return when {
        media < 25 -> CieloDellaLuce.LIBERO
        media < 75 -> CieloDellaLuce.SPARSO
        else -> CieloDellaLuce.COPERTO
    }
}

private const val MINUTI = 24 * 60
