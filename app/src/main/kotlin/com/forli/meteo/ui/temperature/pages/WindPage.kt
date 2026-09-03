package com.forli.meteo.ui.temperature.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asDistance
import com.forli.meteo.ui.asMetresPerSecond
import com.forli.meteo.ui.asPercent
import com.forli.meteo.ui.common.MeteoCard
import com.forli.meteo.ui.common.MeteoLayout
import com.forli.meteo.ui.common.MeteoMetric
import com.forli.meteo.ui.common.MeteoMetricCard
import com.forli.meteo.ui.temperature.ChartBounds
import com.forli.meteo.ui.temperature.MeteoChart
import com.forli.meteo.ui.theme.LocalMeteoAccents
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Il vento.
 *
 * E' la pagina che stava peggio di tutte: in modalita' GIORNO il grafico era
 * **vuoto**, e in silenzio, perche' la serie oraria del vento non esisteva -
 * all'API non veniva chiesta. La velocita' mostrata era quella di adesso sotto
 * un'intestazione che dichiarava un'altra ora, e in fondo alla tabella
 * compariva "UV MAX", che e' il sole, gia' scritto nella sua pagina.
 *
 * Ora i valori sono orari, le raffiche corrono dietro la velocita' come curva
 * di riferimento, e la direzione e' una freccia oltre che una sigla: "SO" dice
 * poco a chi non ha la rosa dei venti in testa.
 */
@Composable
internal fun WindPage(
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
    val week = state.weekMode

    val direction = hour?.windDirection ?: day?.windDirection
    val gustiest = hours.filter { it.windGusts != null }.maxByOrNull { it.windGusts ?: 0.0 }

    PageColumn(layout = layout, modifier = modifier, week = week) {
        MeteoMetricCard(
            rows = listOfNotNull(
                MeteoMetric("VELOCITA'", hour?.windSpeed.asMetresPerSecond(), emphasis = true),
                MeteoMetric("RAFFICHE", hour?.windGusts.asMetresPerSecond(), accent = accents.wind),
                MeteoMetric("MASSIMA DEL GIORNO", day?.windMax.asMetresPerSecond()),
                MeteoMetric("RAFFICA MASSIMA", day?.gustMax.asMetresPerSecond()),
                gustiest?.let { MeteoMetric("RAFFICA PIU' FORTE ALLE", it.time.format(CLOCK)) },
                MeteoMetric("COPERTURA NUVOLOSA", hour?.cloudCover.asPercent()),
                MeteoMetric("VISIBILITA'", hour?.visibility.asDistance()),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        DirectionCard(degrees = direction, modifier = Modifier.fillMaxWidth())

        val values = if (week) {
            state.forecast?.days?.map { it.windMax?.toFloat() }.orEmpty()
        } else {
            hours.map { it.windSpeed?.toFloat() }
        }
        val gusts = if (week) {
            state.forecast?.days?.map { it.gustMax?.toFloat() }.orEmpty()
        } else {
            hours.map { it.windGusts?.toFloat() }
        }

        ChartPanel(
            title = if (week) "LA SETTIMANA" else "LA GIORNATA",
            weekMode = week,
            onToggleWeek = onToggleWeek,
            legend = listOf(
                LegendEntry(accents.wind, "Velocita' del vento a dieci metri"),
                LegendEntry(accents.ghost, "Raffiche: i picchi, non la media"),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MeteoChart(
                values = values,
                ghost = gusts,
                xLabels = if (week) state.weekLabels else state.hourLabels,
                daylight = if (week) emptyList() else hours.map { it.isDay },
                accent = accents.wind,
                bounds = ChartBounds.NonNegative,
                formatValue = { "${it.roundToInt()}" },
                description = "Velocita' del vento in metri al secondo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.chartHeight)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * Da dove tira, disegnato.
 *
 * La sigla della rosa dei venti da sola presuppone che chi legge sappia dov'e'
 * il sudovest rispetto a se': una freccia lo dice senza chiederlo. I gradi
 * dell'API sono la direzione **di provenienza**, quindi la freccia punta al
 * verso opposto - da dove viene verso dove va.
 */
@Composable
private fun DirectionCard(degrees: Double?, modifier: Modifier = Modifier) {
    val accents = LocalMeteoAccents.current
    val sector = Wmo.windDirection(degrees)
    MeteoCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WindArrow(
                degrees = degrees,
                color = accents.wind,
                dim = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(56.dp),
            )
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "DIREZIONE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (degrees == null) "--" else "$sector  ·  ${degrees.roundToInt()}°",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Da dove soffia il vento",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WindArrow(
    degrees: Double?,
    color: Color,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val spoken = if (degrees == null) {
        "Direzione del vento non disponibile"
    } else {
        "Vento da ${Wmo.windDirection(degrees)}"
    }
    Canvas(modifier.semantics { contentDescription = spoken }) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // La rosa: quattro tacche, come riferimento fisso sotto la freccia.
        repeat(4) { i ->
            val angle = Math.PI.toFloat() / 2f * i
            drawLine(
                color = dim,
                start = Offset(
                    center.x + cos(angle) * radius * 0.78f,
                    center.y + sin(angle) * radius * 0.78f,
                ),
                end = Offset(
                    center.x + cos(angle) * radius * 0.96f,
                    center.y + sin(angle) * radius * 0.96f,
                ),
                strokeWidth = radius * 0.07f,
                cap = StrokeCap.Round,
            )
        }
        if (degrees == null) return@Canvas
        // Zero gradi e' il nord, e sulla tela il nord e' in alto: da li' il
        // meno novanta. Il piu' centottanta e' il verso: l'API dice da dove
        // viene, la freccia mostra dove va.
        val heading = Math.toRadians(degrees + 180.0 - 90.0).toFloat()
        val tip = Offset(
            center.x + cos(heading) * radius * 0.62f,
            center.y + sin(heading) * radius * 0.62f,
        )
        val tail = Offset(
            center.x - cos(heading) * radius * 0.62f,
            center.y - sin(heading) * radius * 0.62f,
        )
        drawLine(color, tail, tip, radius * 0.12f, StrokeCap.Round)
        val wing = radius * 0.26f
        listOf(0.72f, -0.72f).forEach { spread ->
            val a = heading + Math.PI.toFloat() * spread
            drawLine(
                color = color,
                start = tip,
                end = Offset(tip.x + cos(a) * wing, tip.y + sin(a) * wing),
                strokeWidth = radius * 0.12f,
                cap = StrokeCap.Round,
            )
        }
    }
}
