package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * L'iconcina del tempo a strati — sole, nuvola, pioggia, grandine, fulmine in
 * magenta — usata dalla striscia dei giorni di Sala II e dalla lista delle
 * localita' salvate. Un solo disegno per non finire con due glifi diversi che
 * raccontano lo stesso tempo.
 */
fun DrawScope.weatherGlyph(condition: SalaCondition, ink: Color) {
    val w = size.width
    val h = size.height
    val cloudy = condition != SalaCondition.SERENO
    val heavy = condition == SalaCondition.TEMPORALE || condition == SalaCondition.TEMPORALE_GRANDINE

    val sunAlpha = if (cloudy) 0.5f else 0.9f
    drawCircle(color = ink.copy(alpha = sunAlpha), radius = w * 0.26f, center = Offset(w * 0.42f, h * 0.32f))

    if (cloudy) {
        drawOval(
            color = ink.copy(alpha = if (heavy) 0.55f else 0.4f),
            topLeft = Offset(w * 0.2f, h * 0.4f),
            size = androidx.compose.ui.geometry.Size(w * 0.66f, h * 0.34f),
        )
    }

    if (condition == SalaCondition.PIOGGIA || heavy) {
        val y0 = h * 0.82f
        listOf(0.30f, 0.50f, 0.70f).forEach { fx ->
            drawLine(
                color = ink.copy(alpha = 0.85f),
                start = Offset(w * fx, y0),
                end = Offset(w * (fx - 0.05f), y0 + h * 0.16f),
                strokeWidth = w * 0.05f,
            )
        }
    }
    if (condition == SalaCondition.GRANDINE || condition == SalaCondition.TEMPORALE_GRANDINE) {
        listOf(0.34f to 0.84f, 0.58f to 0.90f).forEach { (fx, fy) ->
            drawCircle(color = ink.copy(alpha = 0.85f), radius = w * 0.06f, center = Offset(w * fx, h * fy))
        }
    }
    if (heavy) {
        val bolt = Path().apply {
            moveTo(w * 0.56f, h * 0.68f)
            lineTo(w * 0.42f, h * 0.92f)
            lineTo(w * 0.52f, h * 0.90f)
            lineTo(w * 0.46f, h * 1.0f)
            lineTo(w * 0.68f, h * 0.78f)
            lineTo(w * 0.58f, h * 0.80f)
            close()
        }
        drawPath(bolt, color = SalaTokens.accent2, alpha = 0.9f)
    }
}
