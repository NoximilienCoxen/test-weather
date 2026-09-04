package io.github.noximiliencoxen.caelum.widget.paint

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * La luna: il nome della fase, quanta se ne vede, e la fase disegnata davvero.
 *
 * Disegnata e non scelta fra otto sagome pronte: il terminatore e' una curva
 * continua, e a meta' fra due sagome fisse la luna mostrata sarebbe quella di
 * due giorni prima.
 */
internal fun DrawScope.moonArt(
    phase: Float,
    illuminated: Float,
    label: String,
    type: WidgetType,
    ink: WidgetInk,
) {
    val pad = 16.dp.toPx()
    val box = Rect(pad, pad, size.width - pad, size.height - pad)

    val name = type.brush(box.height * 0.095f, weight = 600, width = 78, letterSpacingEm = 0.10f)
    text(label, box.left, box.top, name, ink.secondary)

    val digits = type.brush(box.height * 0.20f, weight = 700, width = 74)
    val digitsTop = box.top + lineHeight(name) * 0.95f
    text("${(illuminated * 100).roundToInt()}%", box.left, digitsTop, digits, ink.primary)

    val artTop = digitsTop + lineHeight(digits) * 0.90f
    val side = minOf(box.width, box.bottom - artTop)
    if (side > 0f) {
        moonFace(
            Rect(box.right - side, box.bottom - side, box.right, box.bottom),
            phase,
            ink,
        )
    }
}
