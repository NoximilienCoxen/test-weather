package io.github.noximiliencoxen.caelum.wear

import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.lingua.tr
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// Funzioni pure per le schermate dell'orologio: niente Compose e niente Android,
// cosi' le prove le leggono su una JVM.

/**
 * La temperatura intera col grado, o un trattino quando il dato manca.
 * I dati arrivano in gradi Celsius; con [fahrenheit] si convertono.
 */
fun temperatura(valore: Double?, fahrenheit: Boolean = false): String =
    valore?.let { "${(if (fahrenheit) it * 9.0 / 5.0 + 32.0 else it).roundToInt()}°" } ?: "–"

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

/**
 * La condizione come si scrive in una frase: "Parz. nuvoloso", non
 * "PARZ. NUVOLOSO". Sul telefono il maiuscolo sta in etichette piccole; qui la
 * condizione e' la seconda riga per grandezza, e in maiuscolo urla.
 */
fun condizioneInFrase(codice: Int?): String =
    Wmo.condition(codice).lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

/** Cosa fara' il cielo nelle prossime ore, ridotto a una riga sola. */
sealed interface PioggiaInVista {
    /** Piove (o nevica) gia'. [finoAlle] e' l'ora in cui smette, o null se non smette nelle ore guardate. */
    data class Adesso(val finoAlle: LocalDateTime?, val neve: Boolean) : PioggiaInVista

    /** Comincia piu' tardi, all'ora [alle]. */
    data class Arriva(val alle: LocalDateTime, val probabilita: Int?, val neve: Boolean) : PioggiaInVista

    /** Asciutto per tutte le [ore] guardate. */
    data class Asciutto(val ore: Int) : PioggiaInVista
}

/**
 * Un'ora "bagnata": un decimo di millimetro previsto, o una probabilita' dal
 * cinquanta per cento. Sotto queste soglie dire "pioggia" su un polso sarebbe
 * un allarme a vuoto.
 */
fun bagnata(ora: HourForecast): Boolean =
    (ora.precipitation ?: 0.0) >= 0.1 || (ora.precipProbability ?: 0) >= 50

private fun nevosa(ora: HourForecast): Boolean =
    Wmo.family(ora.weatherCode) == Wmo.Family.NEVE || (ora.snowfall ?: 0.0) > 0.0

/** La pioggia nelle [ore] date, che partono da quella in corso (vedi [oreDaAdesso]). */
fun pioggiaInVista(ore: List<HourForecast>): PioggiaInVista {
    val prima = ore.firstOrNull() ?: return PioggiaInVista.Asciutto(0)
    if (bagnata(prima)) {
        val fine = ore.firstOrNull { !bagnata(it) }?.time
        return PioggiaInVista.Adesso(finoAlle = fine, neve = nevosa(prima))
    }
    val arriva = ore.firstOrNull(::bagnata) ?: return PioggiaInVista.Asciutto(ore.size)
    return PioggiaInVista.Arriva(arriva.time, arriva.precipProbability, nevosa(arriva))
}

/** La riga sotto la temperatura: "Pioggia alle 15 · 60%", "Piove fino alle 17"... */
fun testoPioggia(vista: PioggiaInVista): String = when (vista) {
    is PioggiaInVista.Adesso -> when {
        vista.finoAlle == null && vista.neve -> tr("Neve per ore", "Snow for hours")
        vista.finoAlle == null -> tr("Pioggia per ore", "Rain for hours")
        vista.neve -> tr("Nevica fino alle ${oraBreve(vista.finoAlle)}", "Snow until ${oraBreve(vista.finoAlle)}")
        else -> tr("Piove fino alle ${oraBreve(vista.finoAlle)}", "Rain until ${oraBreve(vista.finoAlle)}")
    }
    is PioggiaInVista.Arriva -> {
        val ora = oraBreve(vista.alle)
        val quanto = probabilitaPioggia(vista.probabilita)?.let { " · $it" }.orEmpty()
        if (vista.neve) tr("Neve alle $ora$quanto", "Snow at $ora$quanto")
        else tr("Pioggia alle $ora$quanto", "Rain at $ora$quanto")
    }
    is PioggiaInVista.Asciutto -> tr("Asciutto per ${vista.ore} ore", "Dry for ${vista.ore} hours")
}

/**
 * La minima e la massima di tutta la settimana: la scala comune su cui si
 * disegnano le barre dei giorni, cosi' una giornata fredda sta piu' a sinistra
 * di una calda invece di riempire la sua riga come tutte le altre.
 */
fun estremiSettimana(giorni: List<DayForecast>): ClosedFloatingPointRange<Double>? {
    val minima = giorni.mapNotNull { it.tempMin }.minOrNull() ?: return null
    val massima = giorni.mapNotNull { it.tempMax }.maxOrNull() ?: return null
    return if (massima > minima) minima..massima else null
}
