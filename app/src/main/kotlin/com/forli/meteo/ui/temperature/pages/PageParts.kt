package com.forli.meteo.ui.temperature.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.DayForecast
import com.forli.meteo.data.HourForecast
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asBigDegrees
import com.forli.meteo.ui.common.MeteoCard
import com.forli.meteo.ui.common.MeteoLayout
import com.forli.meteo.ui.common.MeteoPill
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Le parti in comune fra le cinque pagine del dettaglio.
 *
 * Prima ogni grandezza viveva sparsa in tre `when` paralleli in fondo alla
 * schermata - uno per la cifra, uno per la tabella, uno per la serie del
 * grafico - e stare al passo fra i tre era responsabilita' di chi leggeva. E'
 * cosi' che la pagina del vento e' finita con un grafico vuoto e quella del
 * sole con un'onda quadra: un ramo dei tre non corrispondeva agli altri due, e
 * niente lo diceva.
 *
 * Ora ogni pagina e' un file, e cifra, metriche e grafico stanno vicini.
 */

internal val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Il giorno che la pagina sta raccontando. */
internal val UiState.pageDay: DayForecast?
    get() = forecast?.days?.getOrNull(selectedDay)

/** L'ora che la pagina sta raccontando, sul giorno giusto. */
internal val UiState.pageHour: HourForecast?
    get() = detailHour

/** Le ore del giorno mostrato: quelle vere, non quelle di oggi. */
internal val UiState.pageHours: List<HourForecast>
    get() {
        val date = pageDay?.date ?: return emptyList()
        return forecast?.hoursOf(date).orEmpty()
    }

/** Le etichette orarie del giorno mostrato. */
internal val UiState.hourLabels: List<String>
    get() = pageHours.map { "%02d".format(it.time.hour) }

/** Le etichette della settimana. */
internal val UiState.weekLabels: List<String>
    get() = forecast?.days?.map { it.label }.orEmpty()

/**
 * Il corpo di una pagina: scorre per conto suo.
 *
 * Ogni pagina ha il suo scorrimento indipendente, cosi' si puo' scendere a
 * leggere le metriche di una grandezza senza perdere la posizione delle altre.
 */
@Composable
internal fun PageColumn(
    layout: MeteoLayout,
    modifier: Modifier = Modifier,
    /** La settimana in coda alla pagina. Nulla per le pagine che non la vogliono. */
    week: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = layout.gutter),
        verticalArrangement = Arrangement.spacedBy(layout.gap),
    ) {
        content()
        // La settimana chiude **ogni** pagina, e scorre col resto.
        //
        // Non appesa sotto il carosello, come si era fatto in un primo momento:
        // li' e' un'altezza fissa che il carosello non puo' contendere, e in
        // orizzontale la somma di barra, pillole, cifra e settimana supera lo
        // schermo - il carosello si riduce a zero e la pagina sparisce. Dentro
        // lo scorrimento non toglie spazio a nessuno.
        //
        // Resta comunque su tutte e cinque le grandezze, che era il punto:
        // prima stava nella sola pagina della temperatura.
        week?.invoke()
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * La scheda che contiene un grafico: intestazione, selettore, e la tela.
 *
 * Il selettore GIORNO/SETTIMANA sta **sopra** cio' che comanda. Nel dettaglio di
 * un giorno il suo gemello stava sotto il grafico, e si scopriva di poterlo
 * cambiare solo dopo aver gia' guardato.
 */
@Composable
internal fun ChartPanel(
    title: String,
    modifier: Modifier = Modifier,
    weekMode: Boolean? = null,
    onToggleWeek: ((Boolean) -> Unit)? = null,
    legend: List<LegendEntry> = emptyList(),
    chart: @Composable () -> Unit,
) {
    MeteoCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Cede spazio alle due pillole invece di spingerle fuori: su
                // uno schermo da 360 punti "LA GIORNATA" piu' GIORNO piu'
                // SETTIMANA non ci stanno, e in una riga a spazio distribuito
                // e' il testo senza peso a vincere.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (weekMode != null && onToggleWeek != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(false to "GIORNO", true to "SETTIMANA").forEach { (week, label) ->
                        MeteoPill(
                            label = label,
                            selected = week == weekMode,
                            onClick = { onToggleWeek(week) },
                        )
                    }
                }
            }
        }
        chart()
        if (legend.isNotEmpty()) Legend(legend)
        Spacer(Modifier.height(10.dp))
    }
}

/** Una voce di legenda: un pallino del suo colore e cosa significa. */
internal data class LegendEntry(val color: Color, val label: String)

/**
 * La legenda.
 *
 * Il grafico del dettaglio di un giorno disegnava tre tracciati diversi - la
 * percepita, l'effettiva dietro, e la media storica - e nessuno diceva quale
 * fosse quale: la linea tratteggiata portava la parola "Norma" in nove punti e
 * basta, che non e' una spiegazione, e' un'etichetta.
 */
@Composable
private fun Legend(entries: List<LegendEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(entry.color),
                )
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// La cifra gigante
// ---------------------------------------------------------------------------

/**
 * Il numero che la pagina mette in cima, o **nulla** se non c'e'.
 *
 * Nulla e non "--", e la differenza conta: la schermata principale ha una
 * regola esplicita per cui finche' non c'e' un numero non si disegna niente,
 * perche' un "--" alto mezzo schermo con tanto di spessore e ombra non dice
 * "sto aspettando", dice che l'app e' rotta. Il dettaglio quella regola non
 * l'aveva, e mostrava esattamente quel trattino gigante.
 */
internal fun heroValue(
    mode: com.forli.meteo.ui.temperature.DetailMode,
    state: UiState,
): String? {
    val day = state.pageDay
    val hour = state.pageHour
    return when (mode) {
        // `asBigDegrees` e non il grado scritto a mano: il simbolo sta in
        // Format.kt come sequenza di escape, apposta - un carattere fuori
        // dall'ASCII in mezzo al codice e' l'unico pezzo che ne' .gitattributes
        // ne' i controlli proteggono, e un transito storto lo trasforma in un
        // punto interrogativo alto mezzo schermo.
        com.forli.meteo.ui.temperature.DetailMode.TEMPERATURA ->
            hour?.temperature?.let { it.asBigDegrees(state.unit) }

        com.forli.meteo.ui.temperature.DetailMode.SOLE ->
            day?.sunshineSeconds?.let { (it / 3600.0).roundToInt().toString() }

        com.forli.meteo.ui.temperature.DetailMode.PRECIPITAZIONI ->
            day?.precipitationSum?.roundToInt()?.toString()

        com.forli.meteo.ui.temperature.DetailMode.VENTO ->
            hour?.windSpeed?.roundToInt()?.toString()

        com.forli.meteo.ui.temperature.DetailMode.ARIA ->
            state.air?.europeanAqi?.toString()
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
internal fun heroSmallTail(mode: com.forli.meteo.ui.temperature.DetailMode): Int =
    if (mode == com.forli.meteo.ui.temperature.DetailMode.TEMPERATURA) 1 else 0

/** Perche' non c'e' un numero da mostrare: la risposta cambia il messaggio. */
internal fun heroMissingReason(
    mode: com.forli.meteo.ui.temperature.DetailMode,
    state: UiState,
): Pair<String, String> = when {
    state.loading -> "IN ATTESA DEI DATI" to "La previsione sta arrivando."
    state.error != null -> "DATI NON RAGGIUNGIBILI" to state.error
    mode == com.forli.meteo.ui.temperature.DetailMode.ARIA && state.airUnavailable ->
        "ARIA NON DISPONIBILE" to
            "La misura arriva da un servizio diverso da quello delle previsioni."
    state.forecast == null -> "NESSUN DATO" to "Tira giu' la schermata principale per riprovare."
    else -> "NON DISPONIBILE" to
        "Il modello scelto non fornisce questa grandezza per il giorno mostrato."
}
