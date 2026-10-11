package io.github.noximiliencoxen.caelum.wear

import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// Funzioni pure per le schermate dell'orologio: niente Compose e niente Android,
// cosi' le prove le leggono su una JVM.

/** La temperatura intera col grado, o un trattino quando il dato manca. */
fun temperatura(valore: Double?): String =
    valore?.let { "${it.roundToInt()}°" } ?: "–"

/** L'ora a due cifre ("07", "15"): sull'orologio non c'e' posto per i minuti. */
fun oraBreve(momento: LocalDateTime): String =
    String.format(Locale.ROOT, "%02d", momento.hour)

/** La probabilita' di pioggia, solo quando vale la pena dirla (dal 10 per cento). */
fun probabilitaPioggia(percento: Int?): String? =
    percento?.takeIf { it >= 10 }?.let { "$it%" }

/**
 * Le prossime [quante] ore, a partire da quella in corso.
 *
 * Parte dall'ora di [adesso], arrotondata per difetto: alle 15:40 la prima riga
 * e' quella delle 15.
 */
fun oreDaAdesso(previsione: Forecast, adesso: LocalDateTime, quante: Int = 12): List<HourForecast> {
    val inizio = adesso.truncatedTo(ChronoUnit.HOURS)
    return previsione.allHours.filter { !it.time.isBefore(inizio) }.take(quante)
}

/** La nuvolosita' dell'ora in corso, che decide fra sole e sole con nuvola. */
fun nuvolositaAdesso(previsione: Forecast, adesso: LocalDateTime): Int? {
    val inizio = adesso.truncatedTo(ChronoUnit.HOURS)
    return previsione.allHours.firstOrNull { it.time == inizio }?.cloudCover
}
