package io.github.noximiliencoxen.caelum.ui.temperature.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asDegrees
import io.github.noximiliencoxen.caelum.ui.asHectopascal
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.common.MeteoLayout
import io.github.noximiliencoxen.caelum.ui.common.MeteoMetric
import io.github.noximiliencoxen.caelum.ui.common.MeteoMetricCard
import io.github.noximiliencoxen.caelum.ui.common.MeteoSplitPills
import io.github.noximiliencoxen.caelum.ui.temperature.ChartReference
import io.github.noximiliencoxen.caelum.ui.temperature.MeteoChart
import io.github.noximiliencoxen.caelum.ui.temperature.WeatherGlyph
import io.github.noximiliencoxen.caelum.ui.temperature.temperatureRamp
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoAccents

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
    onFeelsLike: (Boolean) -> Unit,
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
    val feelsLike = state.feelsLike

    PageColumn(layout = layout, modifier = modifier, week = week) {
        // **Le due meta' della giornata, che vivevano nel dossier del giorno.**
        // Erano l'unica cosa di quella schermata che nessuna pagina diceva: la
        // massima e' un numero, "di giorno sereno a 32, di notte coperto a 19"
        // e' una giornata. Adesso stanno qui, e valgono per il giorno scelto
        // nella striscia in cima invece che per uno solo.
        if (day != null) {
            // Il selettore sta **sopra** cio' che comanda, e comanda tre cose:
            // quale cifra e' grande nelle due meta', e quale delle due curve
            // del grafico e' quella piena.
            MeteoSplitPills(
                labels = listOf("EFFETTIVA", "PERCEPITA"),
                selectedIndex = if (feelsLike) 1 else 0,
                onSelect = { onFeelsLike(it == 1) },
            )
            Text(
                text = if (feelsLike) {
                    "Quanto alta sembra la temperatura, tenendo conto di umidita', vento e sole."
                } else {
                    "La temperatura misurata all'ombra."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HalfDayHeads(day = day, hours = hours, unit = unit, feelsLike = feelsLike)
        }

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
            hours.map { (if (feelsLike) it.apparent else it.temperature)?.toFloat() }
        }
        val ghost = if (weekMode) {
            state.forecast?.days?.map { it.tempMin?.toFloat() }.orEmpty()
        } else {
            hours.map { (if (feelsLike) it.temperature else it.apparent)?.toFloat() }
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
                    when {
                        weekMode -> "Massima del giorno"
                        feelsLike -> "Percepita: umidita', vento e sole insieme"
                        else -> "Temperatura misurata all'ombra"
                    },
                ),
                LegendEntry(
                    accents.ghost,
                    when {
                        weekMode -> "Minima del giorno"
                        feelsLike -> "Effettiva, per confronto"
                        else -> "Percepita, per confronto"
                    },
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

/**
 * Le due meta' della giornata, ciascuna col suo tempo.
 *
 * Veniva dal dossier del giorno, che era una schermata a parte: e' l'unico
 * pezzo di quella schermata che nessuna delle sei pagine sapeva dire. La
 * massima e la minima stanno gia' nella tabella qui sotto; qui c'e' quello che
 * la tabella non dice - **com'era il cielo** nelle due meta', e quanto stacca
 * la percepita da ciascuna.
 */
@Composable
private fun HalfDayHeads(
    day: DayForecast,
    hours: List<HourForecast>,
    unit: TempUnit,
    feelsLike: Boolean,
) {
    // Due scansioni delle ventiquattro ore per un conteggio che cambia solo col
    // giorno: dietro una chiave, e non a ogni ricomposizione della pagina.
    val diurno = remember(hours) { dominantCode(hours.filter { it.isDay }) }
    val notturno = remember(hours) { dominantCode(hours.filter { !it.isDay }) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HalfDay(
            title = "DI GIORNO",
            value = if (feelsLike) day.apparentMax else day.tempMax,
            aside = if (feelsLike) day.tempMax else day.apparentMax,
            asideLabel = if (feelsLike) "EFFETTIVA" else "PERCEPITA",
            code = diurno ?: day.weatherCode,
            isDay = true,
            unit = unit,
            modifier = Modifier.weight(1f),
        )
        HalfDay(
            title = "DI NOTTE",
            value = if (feelsLike) day.apparentMin else day.tempMin,
            aside = if (feelsLike) day.tempMin else day.apparentMin,
            asideLabel = if (feelsLike) "EFFETTIVA" else "PERCEPITA",
            code = notturno ?: day.weatherCode,
            isDay = false,
            unit = unit,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HalfDay(
    title: String,
    value: Double?,
    aside: Double?,
    asideLabel: String,
    code: Int?,
    isDay: Boolean,
    unit: TempUnit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.asPlainDegrees(unit),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            // La riga c'e' sempre, anche vuota: comparendo e sparendo
            // sposterebbe in su e in giu' tutto quello che ha sotto.
            text = aside?.let { "$asideLabel ${it.asPlainDegrees(unit)}" }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherGlyph(weatherCode = code, isDay = isDay, modifier = Modifier.size(32.dp))
            Text(
                text = Wmo.condition(code),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Il tempo che ha caratterizzato mezza giornata.
 *
 * Il piu' frequente, e a parita' il piu' coperto: prendere semplicemente il
 * primo darebbe la condizione dell'alba a un pomeriggio di temporali, e
 * prendere il piu' severo trasformerebbe una giornata serena in un temporale
 * per via di un'ora sola.
 */
private fun dominantCode(hours: List<HourForecast>): Int? {
    if (hours.isEmpty()) return null
    return hours.mapNotNull { it.weatherCode }
        .groupingBy { it }
        .eachCount()
        .entries
        .maxWithOrNull(
            compareBy<Map.Entry<Int, Int>> { it.value }
                .thenBy { Wmo.cloudiness(it.key) },
        )
        ?.key
}
