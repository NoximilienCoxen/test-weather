package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.badgeLabel
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import io.github.noximiliencoxen.caelum.ui.sala.SalaCondition
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaPhase
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.label
import io.github.noximiliencoxen.caelum.ui.sala.salaBody
import io.github.noximiliencoxen.caelum.ui.sala.salaConditionOf
import io.github.noximiliencoxen.caelum.ui.sala.salaPhaseOf
import io.github.noximiliencoxen.caelum.ui.sala.salaTitle
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Sala I — Oggi: la stanza di sempre, sotto una carta nuova.
 *
 * La scultura del meteo (sole o luna, nuvole, pioggia o grandine) resta
 * girabile in orizzontale, come tutto il resto dell'app; la barra delle
 * ventiquattro ore sceglie l'ora — e con lei tutte le altre sale, perche' il
 * giorno e' un asse che attraversa la galleria intera.
 */
@Composable
fun SalaOggiScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    viewModel: WeatherViewModel,
    onPlaceClick: () -> Unit,
) {
    val sky = remember(state.skyAltitude, state.skyJourney, state.skyEvening) {
        SkyState.of(state.skyAltitude, state.skyJourney, state.skyEvening)
    }
    val phase = salaPhaseOf(sky)
    val condition = salaConditionOf(state.forcedWeatherCode ?: state.hour?.weatherCode)
    val hours = state.hours
    val hour = state.hour
    val activeAlerts = remember(state.shownAlerts, hour?.time) { state.shownAlerts.activeAt(hour?.time) }

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.OGGI,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
    ) { modifier ->
        Column(modifier = modifier) {
            AlertsBlock(activeAlerts, palette)

            Column(
                modifier = Modifier.weight(1f, fill = true).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Sculpture(condition = condition, phase = phase, palette = palette)
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = hour?.temperature?.let { state.unit.from(it).roundToInt().toString() } ?: "--",
                        style = SalaType.giant(96),
                        color = palette.ink,
                    )
                    Text(text = "°", style = SalaType.giant(32), color = palette.ink)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = salaTitle(condition, phase), style = SalaType.cardTitle, color = palette.ink)
                val timeLabel = hour?.time?.hour?.let { "%02d:00".format(it) } ?: "--:--"
                val apparent = hour?.apparent?.roundToInt()
                Text(
                    text = "$timeLabel · ${condition.label()} · ${phase.label()}" +
                        (apparent?.let { " · percepiti $it°" } ?: ""),
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                )
                Text(text = salaBody(condition), style = SalaType.body, color = palette.ink)
            }

            HourBar(
                hours = hours,
                selected = state.selectedHour,
                palette = palette,
                onSelect = viewModel::selectHour,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Le allerte in corso all'ora mostrata, la piu' grave per prima. */
private fun List<WeatherAlert>.activeAt(moment: java.time.LocalDateTime?): List<WeatherAlert> {
    if (moment == null) return this
    return filter { alert ->
        val afterOnset = alert.onset?.let { !moment.isBefore(it) } ?: true
        val beforeExpiry = alert.expires?.let { !moment.isAfter(it) } ?: true
        afterOnset && beforeExpiry
    }.sortedByDescending { it.level.weight }
}

@Composable
private fun AlertsBlock(alerts: List<WeatherAlert>, palette: SalaPalette) {
    Column(
        modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        if (alerts.isEmpty()) {
            Text(text = "Nessun avviso in corso", style = SalaType.sectionLabel, color = palette.inkSoft)
        } else {
            alerts.take(3).forEach { alert ->
                val tint = if (alert.level.weight >= 2) SalaTokens.accent2_700 else palette.inkAccent
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(if (alert.level.weight >= 2) 9.dp else 5.dp, 9.dp)
                            .background(tint),
                    )
                    Column {
                        Text(text = alert.badgeLabel, style = SalaType.sectionLabel, color = tint)
                        Text(
                            text = alert.headline,
                            style = SalaType.body,
                            color = palette.inkSoft,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Sculpture(condition: SalaCondition, phase: SalaPhase, palette: SalaPalette) {
    var rotationDeg by remember { mutableFloatStateOf(0f) }
    val night = phase == SalaPhase.NOTTE
    val heavy = condition == SalaCondition.TEMPORALE || condition == SalaCondition.TEMPORALE_GRANDINE
    val cloudy = condition != SalaCondition.SERENO
    val skewX = (0.42f + 0.58f * cos(Math.toRadians(rotationDeg.toDouble()))).toFloat()

    Box(
        modifier = Modifier
            .size(230.dp, 190.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    rotationDeg = (rotationDeg + dragAmount * 0.35f).coerceIn(-70f, 70f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(230.dp, 190.dp).graphicsLayer { scaleX = skewX }) {
            val sunFill = if (night) palette.ink.copy(alpha = 0.85f) else palette.wash.getOrElse(0) { palette.inkAccent }
            val cx = if (cloudy) size.width * 0.40f else size.width * 0.5f
            val cy = if (cloudy) size.height * 0.35f else size.height * 0.44f
            val r = if (cloudy) size.width * 0.16f else size.width * 0.23f

            val discAlpha = if (night) 0.9f else if (cloudy) 0.6f else 0.9f
            drawCircle(color = sunFill.copy(alpha = discAlpha), radius = r, center = Offset(cx, cy))

            if (cloudy) {
                val cloudColor = if (heavy) palette.ink.copy(alpha = 0.5f) else palette.inkSoft
                drawOval(color = cloudColor, topLeft = Offset(size.width * 0.28f, size.height * 0.46f), size = androidx.compose.ui.geometry.Size(size.width * 0.54f, size.height * 0.30f))
                drawOval(color = cloudColor.copy(alpha = cloudColor.alpha * 0.7f), topLeft = Offset(size.width * 0.5f, size.height * 0.40f), size = androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.24f))
            }

            if (condition == SalaCondition.PIOGGIA || heavy) {
                val rainColor = palette.inkAccent
                for (i in 0 until 7) {
                    val x = size.width * (0.28f + i * 0.075f)
                    val yTop = size.height * (0.72f + (i % 3) * 0.03f)
                    drawLine(rainColor, Offset(x, yTop), Offset(x - 4f, yTop + 26f), strokeWidth = if (i % 2 == 0) 3f else 2f)
                }
            }
            if (condition == SalaCondition.GRANDINE || condition == SalaCondition.TEMPORALE_GRANDINE) {
                for (i in 0 until 6) {
                    val x = size.width * (0.30f + i * 0.08f)
                    val y = size.height * (if (i % 2 == 0) 0.80f else 0.86f)
                    drawCircle(palette.inkAccent, radius = if (i % 2 == 0) 4.6f else 3.4f, center = Offset(x, y))
                }
            }
            if (heavy) {
                val bolt = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.54f)
                    lineTo(size.width * 0.52f, size.height * 0.76f)
                    lineTo(size.width * 0.59f, size.height * 0.76f)
                    lineTo(size.width * 0.53f, size.height * 0.92f)
                    lineTo(size.width * 0.70f, size.height * 0.70f)
                    lineTo(size.width * 0.61f, size.height * 0.70f)
                    close()
                }
                drawPath(bolt, color = SalaTokens.accent2, alpha = 0.85f)
            }
        }
    }
}

@Composable
private fun HourBar(
    hours: List<io.github.noximiliencoxen.caelum.data.HourForecast>,
    selected: Int,
    palette: SalaPalette,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .pointerInput(hours) {
                    detectHorizontalDragGestures { change, _ ->
                        val idx = (change.position.x / size.width * 23f).roundToInt().coerceIn(0, 23)
                        onSelect(idx)
                    }
                },
        ) {
            if (hours.isEmpty()) return@Canvas
            val temps = hours.map { it.temperature ?: 0.0 }
            val minT = temps.minOrNull() ?: 0.0
            val maxT = temps.maxOrNull() ?: 1.0
            val span = (maxT - minT).takeIf { it > 0.01 } ?: 1.0
            val points = hours.mapIndexed { i, h ->
                val x = i / 23f * size.width
                val t = h.temperature
                if (t == null) null else Offset(x, size.height * 0.92f - ((t - minT) / span).toFloat() * size.height * 0.85f)
            }
            val curve = buildLinePath(points)
            val area = Path().apply {
                addPath(curve)
                if (points.isNotEmpty()) {
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            }
            drawPath(area, color = palette.inkFaint)
            drawPath(curve, color = palette.ink.copy(alpha = 0.62f), style = Stroke(width = 3f))

            val x = selected / 23f * size.width
            drawLine(palette.inkAccent, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            points.getOrNull(selected)?.let {
                drawCircle(palette.inkAccent, radius = 5f, center = Offset(x, it.y))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(text = it, style = SalaType.hourLabel, color = palette.inkSoft)
            }
        }
    }
}
