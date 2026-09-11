package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoomScaffold
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DirectionNames = listOf(
    "nord", "nord-nord-est", "nord-est", "est-nord-est", "est", "est-sud-est",
    "sud-est", "sud-sud-est", "sud", "sud-sud-ovest", "sud-ovest", "ovest-sud-ovest",
    "ovest", "ovest-nord-ovest", "nord-ovest", "nord-nord-ovest",
)

private fun directionName(bearing: Double): String {
    val idx = (((bearing % 360.0) + 360.0) % 360.0 / 22.5).roundToInt() % 16
    return DirectionNames[idx]
}

/**
 * Sala VI — Il vento, come rosa dei venti: quarantotto tacche, lettere
 * cardinali, e una lancetta che punta la direzione oraria vera e oscilla
 * piu' larga quanto piu' forte soffia.
 */
@Composable
fun SalaVentoScreen(
    state: UiState,
    palette: SalaPalette,
    position: () -> Float,
    onPlaceClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val hours = state.hours
    val idx = state.selectedHour
    val speedsMs = hours.map { it.windSpeed ?: 0.0 }
    val maxMs = (speedsMs.maxOrNull() ?: 1.0).coerceAtLeast(0.1)
    val speedMs = speedsMs.getOrElse(idx) { 0.0 }
    val bearing = hours.getOrNull(idx)?.windDirection ?: 0.0
    val unit = state.windUnit
    val speedShown = unit.from(speedMs).roundToInt()

    SalaRoomScaffold(
        palette = palette,
        room = SalaRoom.VENTO,
        placeName = state.place.name,
        position = position,
        onPlaceClick = onPlaceClick,
        onMenuClick = onMenuClick,
    ) { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(300.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outer = size.minDimension / 2f - 6f

                    drawCircle(palette.inkFaint, radius = outer, center = Offset(cx, cy), style = Stroke(1.5f))
                    drawCircle(palette.inkFaint, radius = outer * 0.84f, center = Offset(cx, cy), style = Stroke(1.5f), alpha = 0.55f)

                    for (a in 0 until 360 step 8) {
                        val card = a % 90 == 0
                        val half = a % 45 == 0
                        val r0 = outer * if (card) 0.84f else if (half) 0.89f else 0.93f
                        val rad = Math.toRadians((a - 90).toDouble())
                        val x0 = cx + (cos(rad) * r0).toFloat()
                        val y0 = cy + (sin(rad) * r0).toFloat()
                        val x1 = cx + (cos(rad) * outer).toFloat()
                        val y1 = cy + (sin(rad) * outer).toFloat()
                        drawLine(
                            color = palette.inkSoft.copy(alpha = if (card) 0.7f else if (half) 0.42f else 0.24f),
                            start = Offset(x0, y0),
                            end = Offset(x1, y1),
                            strokeWidth = if (card) 3f else 1.6f,
                        )
                    }

                    val rad = Math.toRadians(bearing - 90.0)
                    val headLen = outer * 0.80f
                    val tailLen = outer * 0.55f
                    val headTip = Offset(cx + (cos(rad) * headLen).toFloat(), cy + (sin(rad) * headLen).toFloat())
                    val tailTip = Offset(cx - (cos(rad) * tailLen).toFloat(), cy - (sin(rad) * tailLen).toFloat())
                    drawLine(palette.inkAccent, Offset(cx, cy), headTip, strokeWidth = 8f)
                    drawLine(palette.inkSoft.copy(alpha = 0.5f), Offset(cx, cy), tailTip, strokeWidth = 6f)
                    drawCircle(palette.ground, radius = 6f, center = Offset(cx, cy), style = Stroke(3f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = speedShown.toString(), style = SalaType.giant(64), color = palette.ink)
                    Text(text = unit.label.uppercase(), style = SalaType.hourLabel, color = palette.inkSoft)
                }
                CompassLabel("N", Alignment.TopCenter, palette)
                CompassLabel("S", Alignment.BottomCenter, palette)
                CompassLabel("E", Alignment.CenterEnd, palette)
                CompassLabel("O", Alignment.CenterStart, palette)
            }

            WindBars(speedsMs = speedsMs, maxMs = maxMs, selected = idx, palette = palette, modifier = Modifier.padding(top = 20.dp))

            Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Brezza da ${directionName(bearing)},\nnessuna raffica", style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = "${"%02d".format(idx)}:00 · $speedShown ${unit.label} · massimo ${unit.from(maxMs).roundToInt()} ${unit.label}",
                    style = SalaType.sectionLabel,
                    color = palette.inkAccent,
                )
                Text(
                    text = "La lancetta indica la direzione reale del vento all'ora scelta e si allunga verso la testa quando soffia più forte.",
                    style = SalaType.body,
                    color = palette.ink,
                )
            }
        }
    }
}

@Composable
private fun CompassLabel(text: String, alignment: Alignment, palette: SalaPalette) {
    Box(modifier = Modifier.fillMaxWidth().height(300.dp).padding(10.dp), contentAlignment = alignment) {
        Text(text = text, style = SalaType.hourLabel, color = palette.inkSoft)
    }
}

@Composable
private fun WindBars(speedsMs: List<Double>, maxMs: Double, selected: Int, palette: SalaPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            if (speedsMs.isEmpty()) return@Canvas
            val colWidth = size.width / speedsMs.size
            speedsMs.forEachIndexed { i, v ->
                val h = ((v / maxMs).toFloat() * size.height).coerceAtLeast(3f)
                drawRect(
                    color = if (i == selected) palette.ink else palette.inkAccent.copy(alpha = 0.45f),
                    topLeft = Offset(i * colWidth + colWidth * 0.12f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(colWidth * 0.76f, h),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "12", "23").forEach { Text(text = it, style = SalaType.hourLabel, color = palette.inkSoft) }
        }
    }
}
