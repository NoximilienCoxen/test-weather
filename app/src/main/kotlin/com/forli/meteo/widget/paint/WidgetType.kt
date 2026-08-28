package com.forli.meteo.widget.paint

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.forli.meteo.R
import com.forli.meteo.ui.render.NumberType

/**
 * Le scritte dei widget, con lo stesso carattere dell'app.
 *
 * Archivo e' variabile su peso e larghezza: e' quello che permette alla cifra
 * grande di essere stretta e piena invece che larga e tonda come un grassetto
 * di sistema. Gli assi non stanno sul carattere ma sul pennello che lo usa.
 */
internal class WidgetType(context: Context) {

    private val typeface: Typeface =
        runCatching { ResourcesCompat.getFont(context, R.font.archivo_variable) }
            .getOrNull() ?: Typeface.DEFAULT_BOLD

    // Costruire un pennello con assi variabili costa: la prima volta il
    // sistema deriva un carattere nuovo. Con una manciata di stili ripetuti
    // decine di volte per disegno, tenerli da parte e' la sola cosa che conta.
    private val brushes = HashMap<Int, Paint>()

    private val bounds = Rect()

    fun brush(
        sizePx: Float,
        weight: Int = 600,
        width: Int = 100,
        letterSpacingEm: Float = 0f,
    ): Paint {
        val key = sizePx.toInt() * 31_000 + weight * 31 + width +
            (letterSpacingEm * 1000).toInt()
        return brushes.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = this@WidgetType.typeface
                textSize = sizePx
                fontVariationSettings = NumberType.axes(weight, width)
                letterSpacing = letterSpacingEm
                isSubpixelText = true
            }
        }
    }

    fun widthOf(text: String, paint: Paint): Float = paint.measureText(text)

    /** Quanto e' alta una maiuscola: serve a centrare, che la base non basta. */
    fun capHeight(paint: Paint): Float {
        paint.getTextBounds("H", 0, 1, bounds)
        return bounds.height().toFloat()
    }
}

/**
 * Scrive, con l'angolo in alto a sinistra dove dice `x`/`y`.
 *
 * In alto e non sulla base: chi impagina ragiona per riquadri che si
 * accatastano, e la base di un carattere e' un riferimento che si sposta col
 * corpo.
 */
internal fun DrawScope.text(
    value: String,
    x: Float,
    y: Float,
    paint: Paint,
    color: Color,
    alpha: Float = 1f,
) {
    paint.color = color.toArgb()
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    drawContext.canvas.nativeCanvas.drawText(value, x, y - paint.fontMetrics.ascent, paint)
}

/** Scrive centrato su `cx`, sempre a partire dall'alto. */
internal fun DrawScope.textCentered(
    value: String,
    cx: Float,
    y: Float,
    paint: Paint,
    color: Color,
    alpha: Float = 1f,
) {
    text(value, cx - paint.measureText(value) / 2f, y, paint, color, alpha)
}

/** Quanto spazio verticale occupa una riga con questo pennello. */
internal fun lineHeight(paint: Paint): Float =
    paint.fontMetrics.descent - paint.fontMetrics.ascent
