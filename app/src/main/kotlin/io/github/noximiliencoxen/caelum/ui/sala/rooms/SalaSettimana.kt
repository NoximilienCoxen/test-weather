package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.label
import io.github.noximiliencoxen.caelum.ui.sala.salaConditionOf
import io.github.noximiliencoxen.caelum.ui.sala.weatherGlyph
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Sala II — La settimana: otto giorni in fila. Si tocca una colonna e il
 * cartellino sotto si apre sul posto, senza cambiare schermata — la stessa
 * regola per cui il giorno e' un asse che l'intera galleria condivide, non
 * uno schermo a parte.
 */
@Composable
fun SalaSettimanaScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    viewModel: WeatherViewModel,
    onPlaceClick: () -> Unit,
) {
    val days = state.forecast?.days.orEmpty()
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = state.selectedDay.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val day = days.getOrNull(selected)

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.SETTIMANA,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
    ) { modifier ->
        Column(modifier = modifier) {
            Text(
                text = "Otto giorni\nin successione",
                style = SalaType.weekHeadline,
                color = palette.ink,
                modifier = Modifier.padding(top = 15.dp),
            )

            WeekChart(days = days, selected = selected, palette = palette, modifier = Modifier.padding(top = 15.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                days.forEachIndexed { index, d ->
                    val cond = salaConditionOf(d.weatherCode)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectDay(index) }
                            .background(
                                if (index == selected) palette.ink.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Canvas(modifier = Modifier.size(28.dp)) { weatherGlyph(cond, palette.inkAccent) }
                        Text(
                            text = d.label,
                            style = SalaType.hourLabel,
                            color = if (index == selected) palette.ink else palette.inkSoft,
                        )
                        Text(text = d.tempMax.asPlainDegrees(state.unit), style = SalaType.value, color = palette.ink)
                        Text(text = d.tempMin.asPlainDegrees(state.unit), style = SalaType.hourLabel, color = palette.inkSoft)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f))

            if (day != null) {
                DayCard(day = day, open = open, palette = palette, onToggle = { open = !open })
            }
        }
    }
}

@Composable
private fun WeekChart(
    days: List<DayForecast>,
    selected: Int,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(104.dp)) {
        if (days.isEmpty()) return@Canvas
        val maxes = days.map { it.tempMax ?: 0.0 }
        val mins = days.map { it.tempMin ?: 0.0 }
        val lo = (mins.minOrNull() ?: 0.0) - 2.0
        val hi = (maxes.maxOrNull() ?: 1.0) + 2.0
        val span = (hi - lo).takeIf { it > 0.01 } ?: 1.0
        fun y(v: Double) = (size.height - 18f - ((v - lo) / span).toFloat() * (size.height - 50f))
        fun x(i: Int) = i / (days.size - 1).coerceAtLeast(1).toFloat() * size.width

        val maxPoints = days.indices.map { i -> Offset(x(i), y(maxes[i])) }
        val minPoints = days.indices.map { i -> Offset(x(i), y(mins[i])) }
        drawPath(buildLinePath(maxPoints), color = io.github.noximiliencoxen.caelum.ui.sala.SalaTokens.accent2_400, style = Stroke(width = 3.5f))
        drawPath(buildLinePath(minPoints), color = palette.inkAccent, style = Stroke(width = 3.5f))
        days.indices.forEach { i ->
            val r = if (i == selected) 5f else 3f
            drawCircle(io.github.noximiliencoxen.caelum.ui.sala.SalaTokens.accent2_400, radius = r, center = maxPoints[i])
            drawCircle(palette.inkAccent, radius = r, center = minPoints[i])
        }
    }
}

@Composable
private fun DayCard(day: DayForecast, open: Boolean, palette: SalaPalette, onToggle: () -> Unit) {
    val cond = salaConditionOf(day.weatherCode)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .animateContentSize()
            .padding(bottom = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = day.label + " · " + Wmo.condition(day.weatherCode).lowercase(), style = SalaType.cardTitle, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = if (open) "︿" else "﹀", style = SalaType.cardTitle, color = palette.inkAccent)
        }
        Text(
            text = "Massima ${day.tempMax?.roundToInt() ?: "--"}° · minima ${day.tempMin?.roundToInt() ?: "--"}° · ${cond.label()}",
            style = SalaType.sectionLabel,
            color = palette.inkAccent,
        )
        if (open) {
            val rows = listOf(
                "PIOGGIA" to day.precipitationSum.asMillimetres(),
                "PROBABILITA'" to day.precipProbability.asPercent(),
                "VENTO" to (
                    day.windMax?.let {
                        val kmh = io.github.noximiliencoxen.caelum.prefs.SalaWindUnit.KMH.from(it).roundToInt()
                        "$kmh km/h ${Wmo.windDirection(day.windDirection)}"
                    } ?: "--"
                    ),
                "ALBA · TRAMONTO" to (
                    "${day.sunrise?.format(HourMinute) ?: "--"} · ${day.sunset?.format(HourMinute) ?: "--"}"
                    ),
                "UV MASSIMO" to (day.uvMax?.roundToInt()?.toString() ?: "--"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = k, style = SalaType.hourLabel, color = palette.inkSoft, modifier = Modifier.width(104.dp))
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(palette.inkFaint))
                        Text(text = v, style = SalaType.value, color = palette.ink)
                    }
                }
            }
        }
    }
}
