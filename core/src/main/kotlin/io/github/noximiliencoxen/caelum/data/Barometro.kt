package io.github.noximiliencoxen.caelum.data

import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Da che parte va la pressione: e' l'unica cosa che un barometro dice davvero.
 *
 * Il numero da solo vale poco - 1012 hPa sono bel tempo a Palermo e pioggia a
 * Bergen - mentre **la variazione nelle tre ore** e' cio' che i marinai leggono
 * da due secoli. Le soglie sono quelle della tendenza barometrica nei
 * bollettini: sotto un ettopascal e' rumore, oltre tre e mezzo e' un fronte.
 */
enum class TendenzaPressione { SALE_IN_FRETTA, SALE, STABILE, SCENDE, SCENDE_IN_FRETTA }

data class Pressione(
    val hPa: Double,
    /** Quanto e' cambiata nelle tre ore prima; nullo se quelle ore non ci sono. */
    val scarto: Double?,
    val tendenza: TendenzaPressione?,
)

/**
 * La pressione all'[istante] e la sua tendenza, presa sulle tre ore **prima**.
 *
 * Prima e non dopo, perche' e' cosi' che si legge un barometro: guarda dove sei
 * arrivato, non dove arriverai. Le ore passate di oggi la previsione le ha
 * sempre, da mezzanotte; manca solo la mezzanotte stessa, e li' la tendenza
 * resta nulla invece di inventarne una.
 */
fun pressioneAlle(ore: List<HourForecast>, istante: LocalDateTime): Pressione? {
    val adesso = ore.firstOrNull { it.time == istante }?.pressure ?: return null
    val prima = ore.firstOrNull { it.time == istante.minusHours(3) }?.pressure
    val scarto = prima?.let { adesso - it }
    return Pressione(adesso, scarto, scarto?.let(::tendenzaDi))
}

fun tendenzaDi(scarto: Double): TendenzaPressione = when {
    abs(scarto) < 1.0 -> TendenzaPressione.STABILE
    scarto >= 3.5 -> TendenzaPressione.SALE_IN_FRETTA
    scarto > 0 -> TendenzaPressione.SALE
    scarto <= -3.5 -> TendenzaPressione.SCENDE_IN_FRETTA
    else -> TendenzaPressione.SCENDE
}

/**
 * Quanto si sente l'umidita', dal punto di rugiada.
 *
 * **Non dall'umidita' relativa**, che dipende dalla temperatura: 90 % a 5° e'
 * un mattino fresco, 60 % a 32° e' un forno. Il punto di rugiada dice quanta
 * acqua c'e' nell'aria e basta, ed e' lui che decide se il sudore evapora.
 */
enum class Afa { SECCA, GRADEVOLE, UMIDA, AFOSA, OPPRIMENTE }

fun afaDi(rugiadaC: Double): Afa = when {
    rugiadaC < 10.0 -> Afa.SECCA
    rugiadaC < 16.0 -> Afa.GRADEVOLE
    rugiadaC < 18.0 -> Afa.UMIDA
    rugiadaC < 21.0 -> Afa.AFOSA
    else -> Afa.OPPRIMENTE
}
