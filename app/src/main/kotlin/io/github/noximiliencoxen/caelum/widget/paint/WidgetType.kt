package io.github.noximiliencoxen.caelum.widget.paint

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.ui.render.NumberType

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

/**
 * Scrive il nome di una localita' dentro la larghezza che ha davvero.
 *
 * **Serve perche' prima nessuno lo faceva.** Il nome veniva scritto con un
 * `text()` nudo, senza sapere quanto spazio c'era: con NOCETO e MILANO non si
 * vedeva niente, con "Aoraki / Monte Cook" la scritta usciva dal riquadro, si
 * infilava sotto il pallino della qualita' dell'aria e veniva tagliata a meta'
 * dal bordo dell'immagine. Era in tutti e tre i disegni, identico, e a
 * incontrarlo per primo e' stato quello dell'aria solo perche' quel widget ha
 * il pallino in alto a destra che rende la collisione evidente.
 *
 * Sta qui, e non in ciascun disegno, per la stessa ragione per cui sta qui
 * `text()`: un disegno nuovo che scrive il nome di un posto non deve
 * ricordarsi di rimpicciolirlo.
 *
 * **Si stringe prima di troncare, e si stringe di larghezza, non di corpo.** Il
 * carattere ha un asse variabile per la larghezza: stringendo quello l'altezza
 * della riga non cambia, e non cambia quindi nemmeno l'impaginazione di tutto
 * cio' che sta sotto - che e' calcolata proprio su `lineHeight`. Rimpicciolire
 * il corpo avrebbe fatto ballare il resto del widget a seconda di quanto e'
 * lungo il nome della citta'.
 *
 * Quando anche la larghezza minima non basta, si taglia con i puntini: un nome
 * illeggibile perche' compresso non e' meglio di un nome accorciato.
 *
 * Restituisce il pennello davvero usato, perche' chi impagina ha bisogno del
 * suo [lineHeight].
 */
internal fun DrawScope.placeName(
    value: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    sizePx: Float,
    type: WidgetType,
    color: Color,
    weight: Int = 600,
    letterSpacingEm: Float = 0.10f,
): Paint {
    var brush = type.brush(sizePx, weight, WIDTH_WIDEST, letterSpacingEm)
    var axis = WIDTH_WIDEST
    while (type.widthOf(value, brush) > maxWidth && axis > WIDTH_NARROWEST) {
        axis -= WIDTH_STEP
        brush = type.brush(sizePx, weight, axis, letterSpacingEm)
    }

    if (type.widthOf(value, brush) <= maxWidth) {
        text(value, x, y, brush, color)
        return brush
    }

    // Non ci sta nemmeno stretto: si taglia. Un carattere alla volta invece che
    // a stima, perche' le lettere non sono larghe uguali e una stima sbaglia
    // proprio sui nomi che hanno tante lettere strette.
    var cut = value.length
    while (cut > 1 && type.widthOf(value.take(cut) + "…", brush) > maxWidth) {
        cut--
    }
    text(value.take(cut).trimEnd() + "…", x, y, brush, color)
    return brush
}

private const val WIDTH_WIDEST = 78
private const val WIDTH_NARROWEST = 58
private const val WIDTH_STEP = 4
