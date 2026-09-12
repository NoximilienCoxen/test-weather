package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.common.buildLinePath
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import kotlin.math.roundToInt

private data class UvBand(val ceiling: Double, val label: String, val color: Color)

private fun uvBands(palette: SalaPalette) = listOf(
    UvBand(2.9, "Basso", SalaTokens.accent300),
    UvBand(5.9, "Moderato", SalaTokens.accent),
    UvBand(7.9, "Alto", SalaTokens.accent700),
    UvBand(10.9, "Molto alto", SalaTokens.accent2_500),
    UvBand(99.0, "Estremo", SalaTokens.accent2_700),
)

private fun bandFor(bands: List<UvBand>, value: Double): UvBand = bands.first { value <= it.ceiling }

/**
 * Sala VII — I raggi UV: la curva dell'indice sulle ventiquattro ore vere,
 * un cursore colorato secondo la banda, e la scala sotto per collocarlo. La
 * lettura dell'ora e' quella condivisa da tutta la galleria.
 */
@Composable
fun SalaUvScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    onPlaceClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSelectHour: (Int) -> Unit,
) {
    val hours = state.hours
    val values = hours.map { it.uvIndex ?: 0.0 }
    val selected = state.selectedHour
    val bands = uvBands(palette)
    val current = values.getOrElse(selected) { 0.0 }
    val band = bandFor(bands, current)

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.UV,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
        onMenuClick = onMenuClick,
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Text(text = String.format(java.util.Locale.ROOT, "%.1f", current).replace('.', ','), style = SalaType.giant(132), color = palette.ink)
                Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Indice UV", style = SalaType.sectionLabel, color = palette.ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(band.color, CircleShape),
                        )
                        Text(text = band.label, style = SalaType.value, color = palette.ink)
                    }
                }
            }

            UvCurve(values = values, selected = selected, band = band, palette = palette, onSelectHour = onSelectHour, modifier = Modifier.padding(top = 20.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp)) {
                bands.forEach { b ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(b.color.copy(alpha = if (b == band) 1f else 0.35f)),
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val title = when {
                    current < 3 -> "Sole innocuo a quest'ora"
                    current < 6 -> "Protezione consigliata"
                    current < 8 -> "Ombra nelle ore centrali"
                    else -> "Esposizione da evitare"
                }
                Text(text = title, style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = "%02d:00 · indice %s · %s".format(selected, String.format(java.util.Locale.ROOT, "%.1f", current).replace('.', ','), band.label.lowercase()),
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                )
                val body = when {
                    current < 3 -> "A quest'ora la radiazione è debole: la pelle chiara regge oltre un'ora senza protezione."
                    current < 6 -> "Servono crema e cappello se resti fuori più di mezz'ora."
                    current < 8 -> "Fra mezzogiorno e le due conviene stare all'ombra."
                    else -> "Radiazione molto forte: protezione alta, occhiali e maniche lunghe."
                }
                Text(text = body, style = SalaType.body, color = palette.ink)
            }
        }
    }
}

@Composable
private fun UvCurve(
    values: List<Double>,
    selected: Int,
    band: UvBand,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(values.size) {
                    if (values.isEmpty()) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        val idx = (change.position.x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                        onSelectHour(idx)
                    }
                },
        ) {
            if (values.isEmpty()) return@Canvas
            val maxV = 11.0
            fun y(v: Double) = size.height * 0.95f - (v / maxV).toFloat().coerceIn(0f, 1f) * size.height * 0.85f
            val points = values.indices.map { i -> Offset(i / (values.size - 1).coerceAtLeast(1).toFloat() * size.width, y(values[i])) }
            val curve = buildLinePath(points)
            drawLine(palette.inkFaint, Offset(0f, size.height * 0.95f), Offset(size.width, size.height * 0.95f), strokeWidth = 1.5f)
            drawPath(curve, color = palette.ink.copy(alpha = 0.55f), style = Stroke(width = 2.5f))
            val x = selected / (values.size - 1).coerceAtLeast(1).toFloat() * size.width
            drawLine(band.color, Offset(x, size.height * 0.05f), Offset(x, size.height * 0.95f), strokeWidth = 2.5f)
            drawCircle(band.color, radius = 6f, center = Offset(x, y(values.getOrElse(selected) { 0.0 })))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(text = it, style = SalaType.hourLabel, color = palette.inkSoft)
            }
        }
    }
}
