package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.asMillimetres
import io.github.noximiliencoxen.caelum.ui.asPercent
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import kotlin.math.roundToInt

/**
 * Sala III — La pioggia: ventiquattro colonne di millimetri, la colonna
 * dell'ora scelta piena, le altre attenuate; sopra, la linea sottile della
 * probabilita'. Le colonne rispondono alla stessa ora di [SalaOggiScreen],
 * perche' il giorno e' un solo asse per tutta la galleria.
 */
@Composable
fun SalaPioggiaScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    onPlaceClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSelectHour: (Int) -> Unit,
) {
    val hours = state.hours
    val days = state.forecast?.days.orEmpty()
    val selected = state.selectedHour
    val rains = hours.map { it.precipitation ?: 0.0 }
    val total = rains.sum()
    val wetIndices = rains.indices.filter { rains[it] > 0.0 }
    val fromHour = wetIndices.firstOrNull()
    val toHour = wetIndices.lastOrNull()

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.PIOGGIA,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
        onMenuClick = onMenuClick,
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
            // **Quando non piove, la domanda cambia.** Un "0 MM" grande come
            // una casa e una fila di colonne vuote sono una risposta esatta a
            // una domanda che nessuno ha fatto: chi apre la sala della pioggia
            // sotto il sereno vuole sapere **quando torna**. Il dato c'e' gia'
            // nella previsione, bastava guardarlo.
            val prossima = remember(days) { prossimaPioggia(days) }
            if (total > 0.0) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(text = total.asMillimetres(), style = SalaType.giant(88), color = palette.ink)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(text = "MM OGGI", style = SalaType.sectionLabel, color = palette.inkSoft)
                        val nowMm = hours.getOrNull(selected)?.precipitation ?: 0.0
                        val nowProb = hours.getOrNull(selected)?.precipProbability
                        Text(text = "ora: ${nowMm.asMillimetres()} · ${nowProb.asPercent()}", style = SalaType.footnote, color = palette.inkSoft)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        text = prossima?.giorni?.toString() ?: "—",
                        style = SalaType.giant(88),
                        color = palette.ink,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(
                            text = if (prossima == null) "SENZA PIOGGIA" else
                                if (prossima.giorni == 1) "GIORNO ASCIUTTO" else "GIORNI ASCIUTTI",
                            style = SalaType.sectionLabel,
                            color = palette.inkSoft,
                        )
                        Text(
                            text = prossima?.let { "poi ${it.quanta.asMillimetres()}" }
                                ?: "in tutta la previsione",
                            style = SalaType.footnote,
                            color = palette.inkSoft,
                        )
                    }
                }
            }

            RainColumns(
                rains = rains,
                probabilities = hours.map { it.precipProbability ?: 0 },
                selected = selected,
                palette = palette,
                onSelectHour = onSelectHour,
                modifier = Modifier.padding(top = 30.dp),
            )

            Column(modifier = Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val title = when {
                    total > 0.0 -> "Pioggia in arrivo"
                    prossima != null -> "Torna a piovere ${prossima.quando}"
                    else -> "Asciutto per tutta la previsione"
                }
                Text(text = title, style = SalaType.cardTitle, color = palette.ink)
                val meta = when {
                    fromHour != null && toHour != null ->
                        "Dalle %02d:00 alle %02d:00 · %s".format(fromHour, toHour + 1, total.asMillimetres())
                    prossima != null ->
                        "${prossima.quanta.asMillimetres()} attesi · ${prossima.probabilita.asPercent()} di probabilità"
                    else -> "Otto giorni senza una goccia"
                }
                Text(text = meta, style = SalaType.sectionLabel, color = palette.inkAccent)
                Text(
                    text = if (total > 0.0) {
                        "Le colonne piene sono i millimetri, la linea sottile la probabilità ora per ora."
                    } else {
                        "Le colonne restano vuote finché non arriva niente: è la stessa scala di quando piove, non una schermata diversa."
                    },
                    style = SalaType.body,
                    color = palette.ink,
                )
            }
        }
    }
}

@Composable
private fun RainColumns(
    rains: List<Double>,
    probabilities: List<Int>,
    selected: Int,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(rains.size) {
                    if (rains.isEmpty()) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        val idx = (change.position.x / size.width * (rains.size - 1))
                            .roundToInt()
                            .coerceIn(0, rains.size - 1)
                        onSelectHour(idx)
                    }
                },
        ) {
            if (rains.isEmpty()) return@Canvas
            val maxRain = (rains.maxOrNull() ?: 0.0).coerceAtLeast(0.01)
            val colWidth = size.width / rains.size
            rains.forEachIndexed { i, v ->
                val frac = (v / maxRain).toFloat()
                val h = if (v > 0.0) (frac * size.height).coerceAtLeast(8f) else 4f
                val alpha = if (i == selected) 1f else if (v > 0.0) 0.72f else 0.3f
                drawRect(
                    color = (if (v > 0.0) palette.inkAccent else palette.inkFaint).copy(alpha = alpha),
                    topLeft = Offset(i * colWidth + colWidth * 0.12f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(colWidth * 0.76f, h),
                )
            }
            val probPoints = probabilities.mapIndexed { i, p ->
                Offset((i + 0.5f) * colWidth, size.height * 0.36f - (p / 100f) * size.height * 0.32f)
            }
            drawPath(buildLinePath(probPoints), color = palette.ink, style = Stroke(width = 2.5f), alpha = 0.45f)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(text = it, style = SalaType.hourLabel, color = palette.inkSoft)
            }
        }
    }
}

/** Quando torna a piovere, secondo la previsione giornaliera. */
private class ProssimaPioggia(
    val giorni: Int,
    val quando: String,
    val quanta: Double,
    val probabilita: Int?,
)

/**
 * Il primo giorno con pioggia dopo oggi.
 *
 * Si guarda `precipitationSum` e non la probabilità: una probabilità alta senza
 * millimetri è un cielo che minaccia, non una pioggia, e qui si sta rispondendo
 * a "quando torna a piovere".
 */
private fun prossimaPioggia(days: List<io.github.noximiliencoxen.caelum.data.DayForecast>): ProssimaPioggia? {
    val oggi = days.firstOrNull()?.date ?: return null
    days.forEachIndexed { i, d ->
        if (i == 0) return@forEachIndexed
        val mm = d.precipitationSum ?: 0.0
        if (mm > 0.2) {
            val giorni = java.time.temporal.ChronoUnit.DAYS.between(oggi, d.date).toInt()
            val quando = when (giorni) {
                1 -> "domani"
                else -> d.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d"))
            }
            return ProssimaPioggia(giorni, quando, mm, d.precipProbability)
        }
    }
    return null
}
