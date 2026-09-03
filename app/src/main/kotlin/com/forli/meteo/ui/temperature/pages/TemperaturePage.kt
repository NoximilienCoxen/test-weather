package com.forli.meteo.ui.temperature.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asDegrees
import com.forli.meteo.ui.asHectopascal
import com.forli.meteo.ui.asPercent
import com.forli.meteo.ui.asPlainDegrees
import com.forli.meteo.ui.common.MeteoLayout
import com.forli.meteo.ui.common.MeteoMetric
import com.forli.meteo.ui.common.MeteoMetricCard
import com.forli.meteo.ui.temperature.ChartReference
import com.forli.meteo.ui.temperature.MeteoChart
import com.forli.meteo.ui.temperature.temperatureRamp
import com.forli.meteo.ui.theme.LocalMeteoAccents

/**
 * La temperatura.
 *
 * Tutte le metriche vengono dall'**ora mostrata**, non dal blocco `current`
 * dell'API. Prima umidita' e punto di rugiada erano quelli di adesso mentre
 * l'intestazione dichiarava, poniamo, le sei di sera: due numeri veri accostati
 * a dire una cosa falsa.
 */
@Composable
internal fun TemperaturePage(
    state: UiState,
    layout: MeteoLayout,
    onToggleWeek: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** La settimana, che chiude ogni pagina. */
    week: @Composable () -> Unit = {},
) {
    val accents = LocalMeteoAccents.current
    val hour = state.pageHour
    val day = state.pageDay
    val unit = state.unit
    val hours = state.pageHours
    val weekMode = state.weekMode

    PageColumn(layout = layout, modifier = modifier, week = week) {
        MeteoMetricCard(
            rows = listOfNotNull(
                MeteoMetric("TEMPERATURA", hour?.temperature.asDegrees(unit), emphasis = true),
                MeteoMetric("PERCEPITA", hour?.apparent.asDegrees(unit)),
                MeteoMetric("MASSIMA DEL GIORNO", day?.tempMax.asDegrees(unit)),
                MeteoMetric("MINIMA DEL GIORNO", day?.tempMin.asDegrees(unit)),
                MeteoMetric("UMIDITA'", (hour?.humidity ?: day?.humidityMean).asPercent()),
                MeteoMetric(
                    "PUNTO DI RUGIADA",
                    (hour?.dewPoint ?: day?.dewPointMean).asDegrees(unit),
                ),
                MeteoMetric("PRESSIONE", hour?.pressure.asHectopascal()),
                day?.normTemp?.let {
                    MeteoMetric("MEDIA DEL PERIODO", it.asDegrees(unit), accent = accents.norm)
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // I valori restano in Celsius fin qui: la scala di colore dei gradi e'
        // tarata in Celsius, e a convertire pensa l'etichetta.
        val values = if (weekMode) {
            state.forecast?.days?.map { (it.tempMax ?: it.tempMin)?.toFloat() }.orEmpty()
        } else {
            hours.map { it.temperature?.toFloat() }
        }
        val ghost = if (weekMode) {
            state.forecast?.days?.map { it.tempMin?.toFloat() }.orEmpty()
        } else {
            hours.map { it.apparent?.toFloat() }
        }

        // La rampa che il grafico usera' davvero, sull'intervallo dei dati
        // mostrati: la legenda deve dire il colore che si vede, non un colore
        // teorico. In Celsius, come tutta la scala.
        val onSurface = MaterialTheme.colorScheme.onSurface
        val known = (values + ghost).filterNotNull()
        val tempRamp = if (known.size < 2) {
            listOf(onSurface)
        } else {
            temperatureRamp(known.min(), known.max())
        }

        ChartPanel(
            title = if (weekMode) "LA SETTIMANA" else "LA GIORNATA",
            weekMode = weekMode,
            onToggleWeek = onToggleWeek,
            legend = listOfNotNull(
                // Il segno della curva principale e' la **scala dei gradi**,
                // non una tinta: e' quello che la curva disegna davvero, e un
                // pallino bianco accanto direbbe il contrario.
                LegendEntry(
                    tempRamp,
                    if (weekMode) "Massima del giorno" else "Temperatura misurata all'ombra",
                ),
                LegendEntry(
                    accents.ghost,
                    if (weekMode) "Minima del giorno" else "Percepita: umidita', vento e sole insieme",
                ),
                day?.normTemp?.takeIf { !weekMode }?.let {
                    LegendEntry(accents.norm, "Media degli ultimi dieci anni in questo mese")
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MeteoChart(
                values = values,
                ghost = ghost,
                xLabels = if (weekMode) state.weekLabels else state.hourLabels,
                daylight = if (weekMode) emptyList() else hours.map { it.isDay },
                reference = day?.normTemp
                    ?.takeIf { !weekMode }
                    ?.let { ChartReference(it.toFloat(), "MEDIA") },
                useTemperatureRamp = true,
                formatValue = { it.toDouble().asPlainDegrees(unit) },
                description = if (weekMode) {
                    "Temperatura della settimana"
                } else {
                    "Temperatura della giornata"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.chartHeight)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
        }
    }
}
