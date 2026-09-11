package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.badgeLabel
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.common.MinTouchTarget
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.sala.LocalAcquerello
import io.github.noximiliencoxen.caelum.ui.sala.SalaCondition
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaPhase
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.giroConLancio
import io.github.noximiliencoxen.caelum.ui.sala.label
import io.github.noximiliencoxen.caelum.ui.sala.rememberGiro
import io.github.noximiliencoxen.caelum.ui.sala.salaBody
import io.github.noximiliencoxen.caelum.ui.sala.salaConditionOf
import io.github.noximiliencoxen.caelum.ui.sala.salaPhaseOf
import io.github.noximiliencoxen.caelum.ui.sala.salaTitle
import io.github.noximiliencoxen.caelum.ui.sala.scultura
import java.time.LocalDate
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
    onMenuClick: () -> Unit,
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
        onMenuClick = onMenuClick,
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

            // Il ritorno al presente compare **solo quando serve**: se si sta
            // gia' guardando adesso, un tasto che riporta ad adesso e' un
            // comando che non fa niente, e un comando che non fa niente insegna
            // a non fidarsi degli altri. `backToNow` rimette a posto tutti e
            // due gli assi, ora e giorno.
            val lontanoDalPresente = state.selectedHour != state.nowIndex || state.selectedDay != 0
            if (lontanoDalPresente) {
                Text(
                    text = "Torna ad adesso",
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .height(MinTouchTarget)
                        .clickable(onClick = viewModel::backToNow)
                        .padding(top = 10.dp),
                )
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
    // La fase e' quella vera di stanotte, la stessa che calcola Sala IV: le due
    // stanze non possono raccontare due lune diverse nella stessa notte.
    val faseLunare = remember { MoonPhase.at(LocalDate.now()) }
    val acquerello = LocalAcquerello.current
    val notte = phase == SalaPhase.NOTTE

    val giroAnim = rememberGiro()

    // Il giro si legge **dentro il disegno**, non in composizione: e' un gesto
    // continuo che produce centinaia di gradi, e letto fuori ricomporrebbe
    // l'albero a ogni fotogramma del dito invece di ridipingere e basta.
    val giro = { giroAnim.value }

    Canvas(
        // Solo orizzontale: il verticale e' del carosello fra le sale.
        // Il gesto - verso, inerzia, ritorno - sta in `giroConLancio`, che lo
        // condivide con la luna di Sala IV.
        modifier = Modifier
            .size(280.dp, 240.dp)
            .giroConLancio(giroAnim),
    ) {
        scultura(
            acquerello = acquerello,
            condition = condition,
            palette = palette,
            notte = notte,
            giroDeg = giro(),
            fase = faseLunare,
        )
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
