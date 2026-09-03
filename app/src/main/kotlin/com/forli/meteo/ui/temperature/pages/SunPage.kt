package com.forli.meteo.ui.temperature.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asHoursMinutes
import com.forli.meteo.ui.asIndex
import com.forli.meteo.ui.asPercent
import com.forli.meteo.ui.common.MeteoLayout
import com.forli.meteo.ui.common.MeteoMetric
import com.forli.meteo.ui.common.MeteoMetricCard
import com.forli.meteo.ui.secondsAsHoursMinutes
import com.forli.meteo.ui.temperature.ChartBounds
import com.forli.meteo.ui.temperature.MeteoChart
import com.forli.meteo.ui.theme.LocalMeteoAccents
import kotlin.math.roundToInt

/**
 * Il sole.
 *
 * Due correzioni di sostanza rispetto a prima.
 *
 * **"Luce solare" non erano ore di sole.** Era la distanza fra alba e tramonto,
 * che sotto un cielo coperto e' esattamente la stessa di una giornata limpida.
 * Ora la cifra e' `sunshine_duration`, cioe' il sole che si vede davvero, e le
 * ore di luce restano accanto come misura astronomica.
 *
 * **Il grafico non e' piu' un'onda quadra.** In modalita' GIORNO disegnava
 * `isDay` come uno o zero: una scaletta che dice soltanto quando sorge e quando
 * tramonta, cioe' due numeri che stanno gia' scritti nella tabella sopra. Qui
 * disegna l'indice UV ora per ora, che e' la ragione per cui uno guarda il sole
 * in un'app meteo.
 */
@Composable
internal fun SunPage(
    state: UiState,
    layout: MeteoLayout,
    onToggleWeek: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMeteoAccents.current
    val day = state.pageDay
    val hour = state.pageHour
    val hours = state.pageHours
    val week = state.weekMode

    // L'ora in cui l'UV tocca il massimo: "UV 6" senza il quando non dice a
    // nessuno a che ora conviene stare all'ombra.
    val peak = hours.filter { it.uvIndex != null }.maxByOrNull { it.uvIndex ?: 0.0 }

    PageColumn(layout = layout, modifier = modifier) {
        MeteoMetricCard(
            rows = listOfNotNull(
                MeteoMetric(
                    "SOLE EFFETTIVO",
                    day?.sunshineSeconds.secondsAsHoursMinutes(),
                    emphasis = true,
                ),
                MeteoMetric("ORE DI LUCE", daylightHours(state).asHoursMinutes()),
                MeteoMetric("ALBA", day?.sunrise?.format(CLOCK) ?: "--"),
                MeteoMetric("TRAMONTO", day?.sunset?.format(CLOCK) ?: "--"),
                MeteoMetric("UV ORA", hour?.uvIndex.asIndex(), accent = accents.sun),
                MeteoMetric(
                    "UV MASSIMO",
                    day?.uvMax.asIndex(),
                    accent = accents.sun,
                ),
                peak?.let { MeteoMetric("PICCO UV ALLE", it.time.format(CLOCK)) },
                MeteoMetric("COPERTURA NUVOLOSA", hour?.cloudCover.asPercent()),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        val values = if (week) {
            state.forecast?.days?.map { d ->
                d.sunshineSeconds?.let { (it / 3600.0).toFloat() }
            }.orEmpty()
        } else {
            hours.map { it.uvIndex?.toFloat() }
        }

        ChartPanel(
            title = if (week) "ORE DI SOLE, GIORNO PER GIORNO" else "INDICE UV NELLA GIORNATA",
            weekMode = week,
            onToggleWeek = onToggleWeek,
            legend = listOf(
                LegendEntry(
                    accents.sun,
                    if (week) {
                        "Sole effettivo, non ore di luce: un cielo coperto le azzera"
                    } else {
                        "Indice UV: sopra 6 conviene coprirsi"
                    },
                ),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MeteoChart(
                values = values,
                xLabels = if (week) state.weekLabels else state.hourLabels,
                daylight = if (week) emptyList() else hours.map { it.isDay },
                accent = accents.sun,
                bounds = ChartBounds.NonNegative,
                formatValue = { if (week) "${it.roundToInt()}h" else "${it.roundToInt()}" },
                description = if (week) "Ore di sole della settimana" else "Indice UV della giornata",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.chartHeight)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * Le ore di luce fra alba e tramonto, in ore decimali.
 *
 * Restano accanto al sole effettivo perche' rispondono a un'altra domanda:
 * quanto dura il giorno, non quanto sole ci sara' dentro.
 */
internal fun daylightHours(state: UiState): Double? {
    val day = state.pageDay ?: return null
    val sunrise = day.sunrise ?: return null
    val sunset = day.sunset ?: return null
    return runCatching {
        java.time.Duration.between(sunrise, sunset).toMinutes() / 60.0
    }.getOrNull()?.takeIf { it > 0 }
}
