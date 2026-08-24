package com.forli.meteo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import kotlin.math.roundToInt

data class ChartSeries(
    val values: List<Double?>,
    val color: Color,
    val filled: Boolean = false,
    val strokeWidthDp: Float = 2f,
)

/**
 * Curve spline Catmull-Rom con tre tacche numeriche sul lato destro.
 * I punti mancanti spezzano la curva invece di essere interpolati a zero.
 */
@Composable
fun SplineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    tickCount: Int = 3,
    tickSuffix: String = "",
) {
    val colors = LocalMeteoColors.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val allValues = series.flatMap { it.values }.filterNotNull()
    if (allValues.isEmpty()) {
        Box(modifier.fillMaxWidth())
        return
    }

    var lo = allValues.min()
    var hi = allValues.max()
    if (hi - lo < 1e-3) { hi += 1.0; lo -= 1.0 }

    val tickStyle = MeteoType.caption.copy(fontSize = 10.sp, color = colors.label)
    val gutterPx = with(density) { 34.dp.toPx() }

    Canvas(modifier) {
        val plotWidth = size.width - gutterPx
        val padY = size.height * 0.12f
        val plotHeight = size.height - padY * 2f

        fun pointsOf(values: List<Double?>): List<List<Offset>> {
            val runs = mutableListOf<List<Offset>>()
            var run = mutableListOf<Offset>()
            values.forEachIndexed { i, v ->
                if (v == null) {
                    if (run.size > 1) runs += run.toList()
                    run = mutableListOf()
                } else {
                    val x = if (values.size == 1) plotWidth / 2f
                    else plotWidth * i / (values.size - 1).toFloat()
                    val t = ((v - lo) / (hi - lo)).toFloat()
                    run += Offset(x, padY + plotHeight * (1f - t))
                }
            }
            if (run.size > 1) runs += run.toList()
            return runs
        }

        series.forEach { s ->
            pointsOf(s.values).forEach { pts ->
                val path = catmullRomPath(pts)
                if (s.filled) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(pts.last().x, size.height - padY * 0.2f)
                        lineTo(pts.first().x, size.height - padY * 0.2f)
                        close()
                    }
                    drawPath(area, s.color.copy(alpha = 0.55f))
                }
                drawPath(
                    path = path,
                    color = s.color,
                    style = Stroke(width = s.strokeWidthDp.dp.toPx()),
                )
            }
        }

        // tacche numeriche a destra
        repeat(tickCount) { i ->
            val frac = if (tickCount == 1) 0.5f else i / (tickCount - 1).toFloat()
            val value = hi - (hi - lo) * frac
            val y = padY + plotHeight * frac
            val layout = measurer.measure("${value.roundToInt()}$tickSuffix", tickStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = size.width - layout.size.width,
                    y = y - layout.size.height / 2f,
                ),
            )
        }
    }
}

/**
 * Catmull-Rom convertita in cubiche di Bezier: le tangenti in ogni punto
 * derivano dai due punti adiacenti, cosi' la curva passa per tutti i punti
 * senza oscillare.
 */
internal fun catmullRomPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path

    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}
