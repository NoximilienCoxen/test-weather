package com.forli.meteo.ui.temperature.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.common.MeteoLayout
import com.forli.meteo.ui.common.MeteoMetric
import com.forli.meteo.ui.common.MeteoMetricCard
import com.forli.meteo.ui.home.MoonPhase
import com.forli.meteo.ui.home.MoonSegment
import com.forli.meteo.ui.temperature.ChartBounds
import com.forli.meteo.ui.temperature.MeteoChart
import com.forli.meteo.ui.theme.LocalMeteoAccents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * La luna.
 *
 * **Non chiede niente alla rete.** Open-Meteo la fase non la da', e non serve:
 * il mese sinodico medio a partire da un novilunio noto la ricava in due righe,
 * e l'errore resta ben sotto il giorno. E' anche il motivo per cui questa e'
 * l'unica pagina che ha qualcosa da dire pure quando la previsione non arriva -
 * la luna non dipende dal meteo.
 *
 * La sfera girabile non sta qui ma nell'eroe, sopra il carosello, insieme a
 * tutte le altre: e' lo stesso corpo che la scultura della schermata principale
 * disegna gia', con la stessa luce e gli stessi mari.
 */
@Composable
internal fun MoonPage(
    state: UiState,
    layout: MeteoLayout,
    modifier: Modifier = Modifier,
    /** La settimana, che chiude ogni pagina. */
    week: @Composable () -> Unit = {},
) {
    val accents = LocalMeteoAccents.current
    val date = state.pageDay?.date ?: LocalDate.now()
    val phase = MoonPhase.at(date)
    val illumination = MoonPhase.illumination(phase)

    val age = MoonPhase.ageDays(phase)

    PageColumn(layout = layout, modifier = modifier, week = week) {
        MeteoMetricCard(
            rows = listOf(
                MeteoMetric(
                    "FASE",
                    MoonSegment.of(phase).label,
                    emphasis = true,
                    accent = accents.moon,
                ),
                MeteoMetric("ILLUMINATA", "${(illumination * 100f).roundToInt()}%"),
                MeteoMetric("ETA' DELLA LUNA", "${age.roundToInt()} giorni"),
                MeteoMetric(
                    // Crescente o calante e' l'unica cosa che il disegno da solo
                    // non dice: una falce a destra e una a sinistra si
                    // somigliano abbastanza da non distinguerle a memoria.
                    "ANDAMENTO",
                    if (MoonPhase.waxing(phase)) "CRESCE" else "CALA",
                ),
                MeteoMetric(
                    "PROSSIMO PLENILUNIO",
                    MoonPhase.nextDate(date, 0.5f).format(DAY_MONTH),
                ),
                MeteoMetric(
                    "PROSSIMO NOVILUNIO",
                    MoonPhase.nextDate(date, 0f).format(DAY_MONTH),
                ),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Niente selettore GIORNO/SETTIMANA: la fase e' del giorno, e un
        // grafico orario della luna sarebbe una riga piatta lunga
        // ventiquattro ore. La settimana invece dice qualcosa - se si sta
        // andando verso il pieno o verso il buio - ed e' l'unico taglio che
        // questa grandezza ha.
        val days = state.forecast?.days.orEmpty()
        if (days.isNotEmpty()) {
            ChartPanel(
                title = "LA LUNA NELLA SETTIMANA",
                legend = listOf(
                    LegendEntry(accents.moon, "Percentuale di disco illuminato"),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MeteoChart(
                    values = days.map { MoonPhase.illumination(MoonPhase.at(it.date)) * 100f },
                    xLabels = state.weekLabels,
                    daylight = emptyList(),
                    accent = accents.moon,
                    bounds = ChartBounds.Percent,
                    formatValue = { "${it.roundToInt()}%" },
                    description = "Illuminazione della luna nella settimana",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.chartHeight)
                        .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                )
            }
        }
    }
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
