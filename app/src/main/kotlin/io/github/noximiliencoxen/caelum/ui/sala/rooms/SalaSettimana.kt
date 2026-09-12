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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
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
    onMenuClick: () -> Unit,
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
        onMenuClick = onMenuClick,
    ) { modifier ->
        Column(modifier = modifier) {
            Text(
                text = "Otto giorni\nin successione",
                style = SalaType.weekHeadline,
                color = palette.ink,
                modifier = Modifier.padding(top = 15.dp),
            )

            WeekChart(days = days, selected = selected, unit = state.unit, palette = palette, modifier = Modifier.padding(top = 15.dp))

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
                        // Piu' grandi, e il giorno scelto piu' degli altri: in
                        // una striscia di otto la prima cosa che si cerca e'
                        // "che tempo fa", e il glifo e' l'unico a poterlo dire.
                        Canvas(modifier = Modifier.size(if (index == selected) 38.dp else 33.dp)) {
                            weatherGlyph(cond, palette.buio)
                        }
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
    unit: TempUnit,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val misure = SalaType.hourLabel
    val disegnatore = rememberTextMeasurer()
    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        if (days.isEmpty()) return@Canvas
        val maxes = days.map { it.tempMax }
        val mins = days.map { it.tempMin }
        val validi = (maxes + mins).filterNotNull()
        if (validi.isEmpty()) return@Canvas

        // La scala si prende dai dati veri, con un margine sopra e sotto per
        // non far toccare le etichette al bordo.
        val lo = validi.min() - 1.5
        val hi = validi.max() + 1.5
        val span = (hi - lo).takeIf { it > 0.01 } ?: 1.0

        val altoTesti = 22f
        val bassoTesti = 22f
        val utile = size.height - altoTesti - bassoTesti
        fun y(v: Double) = altoTesti + (1.0 - (v - lo) / span).toFloat() * utile
        fun x(i: Int) = (i + 0.5f) / days.size * size.width

        // ── La banda: e' lei il dato ─────────────────────────────────────────
        // Due linee nude dicevano soltanto "una sta sopra l'altra". Quello che
        // conta di un giorno e' **l'escursione**: quanto si passa dal minimo al
        // massimo. Riempita, la si legge senza doverla ricostruire con gli
        // occhi, e il giorno piu' sbalzato salta fuori da solo.
        days.indices.forEach { i ->
            val ma = maxes[i] ?: return@forEach
            val mi = mins[i] ?: return@forEach
            val larghezza = size.width / days.size * 0.30f
            drawRoundRect(
                color = palette.inkAccent.copy(alpha = if (i == selected) 0.34f else 0.16f),
                topLeft = Offset(x(i) - larghezza / 2f, y(ma)),
                size = Size(larghezza, (y(mi) - y(ma)).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(larghezza / 2f),
            )
        }

        // Le due linee restano, ma sopra la banda e piu' sottili: dicono
        // l'andamento della settimana, che la banda da sola non racconta.
        val puntiMax = days.indices.mapNotNull { i -> maxes[i]?.let { Offset(x(i), y(it)) } }
        val puntiMin = days.indices.mapNotNull { i -> mins[i]?.let { Offset(x(i), y(it)) } }
        if (puntiMax.size > 1) {
            drawPath(buildLinePath(puntiMax), color = SalaTokens.accent2_500, style = Stroke(width = 2.2f))
        }
        if (puntiMin.size > 1) {
            drawPath(buildLinePath(puntiMin), color = palette.inkAccent, style = Stroke(width = 2.2f))
        }

        // ── I numeri, che erano quello che mancava ───────────────────────────
        days.indices.forEach { i ->
            val ma = maxes[i]
            val mi = mins[i]
            val scelto = i == selected
            if (ma != null) {
                val testo = "${unit.from(ma).roundToInt()}°"
                val m = disegnatore.measure(testo, misure)
                drawText(
                    textLayoutResult = m,
                    color = if (scelto) palette.ink else palette.inkSoft,
                    topLeft = Offset(x(i) - m.size.width / 2f, y(ma) - m.size.height - 3f),
                )
            }
            if (mi != null) {
                val testo = "${unit.from(mi).roundToInt()}°"
                val m = disegnatore.measure(testo, misure)
                drawText(
                    textLayoutResult = m,
                    color = if (scelto) palette.ink else palette.inkSoft,
                    topLeft = Offset(x(i) - m.size.width / 2f, y(mi) + 3f),
                )
            }
            if (scelto) {
                // Il giorno scelto ha un filo verticale che lo lega alla
                // striscia sotto: senza, la selezione si vedeva solo da un
                // pallino piu' grosso, che non e' un legame.
                drawLine(
                    color = palette.inkAccent.copy(alpha = 0.45f),
                    start = Offset(x(i), altoTesti - 6f),
                    end = Offset(x(i), size.height - bassoTesti + 6f),
                    strokeWidth = 1.2f,
                )
            }
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
