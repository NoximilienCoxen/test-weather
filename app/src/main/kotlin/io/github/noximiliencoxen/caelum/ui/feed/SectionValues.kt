package io.github.noximiliencoxen.caelum.ui.feed

import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asBigDegrees
import kotlin.math.roundToInt

/**
 * Cosa legge una scheda del feed, e la cifra che ci mette in cima.
 *
 * Viene da `pages/PageParts.kt`, che era la parte in comune fra le sei pagine
 * del foglio di dettaglio. Le pagine non ci sono piu'; queste letture si', e
 * sono le stesse: **il giorno e l'ora li sceglie la prima scheda**, e tutte le
 * altre raccontano quel momento li'. Scorrere la barra delle ore e poi scendere
 * di una scheda vuol dire chiedere "e la pioggia, a quell'ora?".
 */

/** Il giorno che la scheda sta raccontando. */
internal val UiState.pageDay: DayForecast?
    get() = forecast?.days?.getOrNull(selectedDay)

/** L'ora che la scheda sta raccontando, sul giorno giusto. */
internal val UiState.pageHour: HourForecast?
    get() = detailHour

/** Le ore del giorno mostrato: quelle vere, non quelle di oggi. */
internal val UiState.pageHours: List<HourForecast>
    get() {
        val date = pageDay?.date ?: return emptyList()
        return forecast?.hoursOf(date).orEmpty()
    }

/**
 * Il numero che la scheda mette in mezzo, o **nulla** se non c'e'.
 *
 * Nulla e non "--", e la differenza conta: la prima scheda ha una regola
 * esplicita per cui finche' non c'e' un numero non si disegna niente, perche'
 * un "--" alto mezzo schermo con tanto di spessore e ombra non dice "sto
 * aspettando", dice che l'app e' rotta. Vale per tutte.
 */
internal fun heroValue(section: FeedSection, state: UiState): String? {
    val day = state.pageDay
    val hour = state.pageHour
    return when (section) {
        // `asBigDegrees` e non il grado scritto a mano: il simbolo sta in
        // Format.kt come sequenza di escape, apposta - un carattere fuori
        // dall'ASCII in mezzo al codice e' l'unico pezzo che ne' .gitattributes
        // ne' i controlli proteggono, e un transito storto lo trasforma in un
        // punto interrogativo alto mezzo schermo.
        FeedSection.TEMPERATURA -> hour?.temperature?.let { it.asBigDegrees(state.unit) }

        FeedSection.PRECIPITAZIONI -> day?.precipitationSum?.roundToInt()?.toString()

        FeedSection.ARIA -> state.air?.europeanAqi?.toString()

        FeedSection.VENTO -> hour?.windSpeed?.roundToInt()?.toString()

        FeedSection.SOLE -> day?.sunshineSeconds?.let { (it / 3600.0).roundToInt().toString() }

        // La luna non ha una cifra da estrudere: il suo eroe e' il corpo, non
        // un numero. La percentuale illuminata sta fra i numeri sotto, dove
        // puo' stare accanto al nome della fase invece che sotto un'unita'
        // scritta a parte.
        FeedSection.LUNA -> null
    }
}

/**
 * Quanti caratteri finali vanno in corpo ridotto.
 *
 * Solo il grado della temperatura: e' l'unico simbolo che il prisma estrude
 * insieme alle cifre, e va a filo della loro cima. Le altre unita' stanno
 * scritte sotto la cifra, in caratteri normali - il prisma sa fare le cifre,
 * non "M/S".
 */
internal fun heroSmallTail(section: FeedSection): Int =
    if (section == FeedSection.TEMPERATURA) 1 else 0

/** Perche' non c'e' un numero da mostrare: la risposta cambia il messaggio. */
internal fun heroMissingReason(section: FeedSection, state: UiState): Pair<String, String> = when {
    state.loading -> "IN ATTESA DEI DATI" to "La previsione sta arrivando."
    state.error != null -> "DATI NON RAGGIUNGIBILI" to state.error
    section == FeedSection.ARIA && state.airUnavailable ->
        "ARIA NON DISPONIBILE" to
            "La misura arriva da un servizio diverso da quello delle previsioni."
    state.forecast == null -> "NESSUN DATO" to "Tira giu' la prima scheda per riprovare."
    else -> "NON DISPONIBILE" to
        "Il modello scelto non fornisce questa grandezza per il giorno mostrato."
}
