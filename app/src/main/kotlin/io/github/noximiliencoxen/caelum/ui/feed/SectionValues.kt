package io.github.noximiliencoxen.caelum.ui.feed

import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.isWet
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

/**
 * Le ore del giorno mostrato: quelle vere, non quelle di oggi.
 *
 * Adesso e' `UiState.shownHours` e sta accanto a `detailHour` e `detailDay`,
 * perche' e' la stessa domanda e perche' **la fa anche la prima schermata**.
 * Qui resta il nome con cui le schede la chiamano.
 */
internal val UiState.pageHours: List<HourForecast>
    get() = shownHours

/**
 * Se all'ora mostrata piove davvero.
 *
 * **Il codice per primo, i millimetri poi**, che e' la trappola #14: un
 * temporale previsto all'ottanta per cento puo' avere zero millimetri in
 * quell'ora esatta, e sotto la scritta TEMPORALE deve comunque piovere. I
 * millimetri dicono quanto forte, non se.
 *
 * Il codice imposto viene prima di quello vero: e' l'aggancio con cui gli
 * scatti mettono in scena un tempo che quel giorno non fa.
 */
internal val UiState.shownHourIsWet: Boolean
    get() = Wmo.family(forcedWeatherCode ?: pageHour?.weatherCode).isWet()

/**
 * L'ora vera, se il giorno mostrato e' oggi; **nulla** se e' un altro giorno.
 *
 * Segnare "adesso" su mercoledi' sarebbe un punto senza significato: adesso non
 * cade dentro mercoledi'. E l'ora e' quella della localita' mostrata, non quella
 * del telefono - da quando il posto lo sceglie chi usa l'app, i due possono
 * distare mezza giornata.
 */
internal val UiState.nowHourOnShownDay: Int?
    get() {
        val current = forecast ?: return null
        val now = current.nowThere()
        return if (pageDay?.date == now.toLocalDate()) now.hour else null
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

        // La pioggia non ha piu' una cifra da estrudere, come la luna: il suo
        // eroe e' la finestra sull'ora scelta, e i millimetri stanno fra i tre
        // numeri in fondo, dove sono un numero come gli altri. Chi disegna la
        // finestra fa il proprio controllo sui dati, perche' gli serve l'ora
        // intera e non una stringa.
        FeedSection.PRECIPITAZIONI -> null

        FeedSection.ARIA -> state.air?.index?.toString()

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
 * L'unita' scritta sotto la cifra, che per l'aria **non e' fissa**.
 *
 * L'indice dell'aria arriva su due scale diverse secondo dove si e' - quella
 * europea dentro il suo dominio, quella statunitense fuori - e i due numeri non
 * si confrontano: cinquanta sull'una e' aria mediocre, cinquanta sull'altra e'
 * aria buona. Scrivere "INDICE EUROPEO" sotto un numero americano sarebbe la
 * bugia peggiore delle due, perche' e' quella che sembra un'informazione.
 *
 * Le altre sezioni hanno un'unita' sola e se la tengono scritta nell'enum.
 */
internal fun unitLabelFor(section: FeedSection, state: UiState): String =
    if (section == FeedSection.ARIA) {
        state.air?.scale?.label ?: section.unitLabel
    } else {
        section.unitLabel
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
