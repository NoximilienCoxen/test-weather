package io.github.noximiliencoxen.caelum.ui.temperature.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.PrecipKind
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asCentimetres
import io.github.noximiliencoxen.caelum.ui.asHours
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.common.MeteoLayout
import io.github.noximiliencoxen.caelum.ui.common.MeteoMetric
import io.github.noximiliencoxen.caelum.ui.common.MeteoMetricCard
import io.github.noximiliencoxen.caelum.ui.temperature.ChartBounds
import io.github.noximiliencoxen.caelum.ui.temperature.MeteoChart
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoAccents
import kotlin.math.roundToInt

/**
 * Le precipitazioni.
 *
 * **Millimetri e probabilita' sono due grandezze diverse** e la pagina lo dice
 * invece di mescolarle: la cifra grande era il totale del giorno in millimetri
 * e il grafico sotto la probabilita' in percentuale, senza che niente
 * segnalasse il cambio di unita'. Qui la curva e' la probabilita' e le colonne
 * sono i millimetri, sullo stesso asse dei tempi: si vede a colpo d'occhio se
 * l'ottanta per cento di probabilita' porta una spruzzata o un rovescio.
 *
 * **Il tipo c'era gia' e non lo usava nessuno.** `Wmo.precipKind` distingue
 * pioggia, neve, mista e grandine dal codice WMO ed era codice morto: sapere
 * che quei quattro millimetri sono neve cambia cosa metti ai piedi.
 */
@Composable
internal fun RainPage(
    state: UiState,
    layout: MeteoLayout,
    onToggleWeek: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** La settimana, che chiude ogni pagina. */
    week: @Composable () -> Unit = {},
) {
    val accents = LocalMeteoAccents.current
    val day = state.pageDay
    val hour = state.pageHour
    val hours = state.pageHours
    val weekMode = state.weekMode

    val kind = Wmo.precipKind(hour?.weatherCode ?: day?.weatherCode)
    val wettest = hours
        .filter { (it.precipProbability ?: 0) > 0 }
        .maxByOrNull { it.precipProbability ?: 0 }

    PageColumn(layout = layout, modifier = modifier, week = week) {
        MeteoMetricCard(
            rows = listOfNotNull(
                MeteoMetric(
                    "TOTALE DEL GIORNO",
                    day?.precipitationSum.asMillimetres(),
                    emphasis = true,
                ),
                MeteoMetric(
                    "TIPO",
                    if (kind == PrecipKind.NONE) "NESSUNA" else kind.label,
                    accent = accents.rain,
                ),
                MeteoMetric("PROBABILITA' ORA", hour?.precipProbability.asPercent()),
                MeteoMetric("PROBABILITA' MASSIMA", day?.precipProbability.asPercent()),
                MeteoMetric("CADUTA IN QUEST'ORA", hour?.precipitation.asMillimetres()),
                wettest?.let { MeteoMetric("PICCO ATTESO ALLE", it.time.format(CLOCK)) },
                MeteoMetric("ORE DI PIOGGIA", day?.precipHours.asHours()),
                // Pioggia e neve separate solo quando c'e' neve: sotto un cielo
                // sereno d'agosto una riga "NEVE 0.0 CM" e' soltanto rumore.
                day?.rainSum?.takeIf { (day.snowfallSum ?: 0.0) > 0.0 }?.let {
                    MeteoMetric("DI CUI PIOGGIA", it.asMillimetres())
                },
                day?.snowfallSum?.takeIf { it > 0.0 }?.let {
                    MeteoMetric("DI CUI NEVE", it.asCentimetres())
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        val values = if (weekMode) {
            state.forecast?.days?.map { it.precipProbability?.toFloat() }.orEmpty()
        } else {
            hours.map { it.precipProbability?.toFloat() }
        }
        val bars = if (weekMode) {
            state.forecast?.days?.map { it.precipitationSum?.toFloat() }.orEmpty()
        } else {
            hours.map { it.precipitation?.toFloat() }
        }

        ChartPanel(
            title = if (weekMode) "LA SETTIMANA" else "LA GIORNATA",
            weekMode = weekMode,
            onToggleWeek = onToggleWeek,
            legend = listOf(
                LegendEntry(accents.rain, "Curva: probabilita' che piova, in percentuale"),
                LegendEntry(accents.rain.copy(alpha = 0.45f), "Colonne: millimetri attesi"),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MeteoChart(
                values = values,
                bars = bars,
                xLabels = if (weekMode) state.weekLabels else state.hourLabels,
                daylight = if (weekMode) emptyList() else hours.map { it.isDay },
                accent = accents.rain,
                // Zero e cento, non un intervallo dedotto dai dati: su una
                // giornata asciutta la scala si allargava a valori inventati e
                // l'asse arrivava a dichiarare percentuali negative.
                bounds = ChartBounds.Percent,
                formatValue = { "${it.roundToInt()}%" },
                description = "Probabilita' di precipitazioni",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.chartHeight)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
        }
    }
}
