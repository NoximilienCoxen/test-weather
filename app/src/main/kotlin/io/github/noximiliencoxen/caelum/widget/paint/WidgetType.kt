package io.github.noximiliencoxen.caelum.widget.paint

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.annotation.VisibleForTesting
import androidx.core.content.res.ResourcesCompat
import io.github.noximiliencoxen.caelum.R

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
                fontVariationSettings = numberTypeAxes(weight, width)
                letterSpacing = letterSpacingEm
                isSubpixelText = true
            }
        }
    }

    fun widthOf(text: String, paint: Paint): Float = paint.measureText(text)
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
    writtenText?.invoke(value, x, x + paint.measureText(value))
    paint.color = color.toArgb()
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    drawContext.canvas.nativeCanvas.drawText(value, x, y - paint.fontMetrics.ascent, paint)
}

/**
 * Chi vuole sapere dove finisce ogni scritta: la usa solo `WidgetOverflowTest`,
 * per controllare che nessuna passi il bordo. Guardare i pixel non basta - il
 * bagliore della luna e gli angoli sfumati sono inchiostro anche loro - mentre
 * qui arriva l'estensione esatta di ogni riga. In produzione e' nulla.
 */
@VisibleForTesting
internal var writtenText: ((value: String, left: Float, right: Float) -> Unit)? = null

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
 * Scrive una riga dentro la larghezza che ha davvero: il nome di una localita',
 * la fase della luna, la condizione del tempo.
 *
 * Si chiamava `placeName`, e il nome era il difetto: sembrava roba da nomi di
 * citta', e le altre scritte in alto - "GIBBOSA CRESCENTE" sulla luna,
 * "TEMPORALE E GRANDINE" accanto alla temperatura - venivano scritte con un
 * `text()` nudo e uscivano dal bordo come prima uscivano i nomi lunghi.
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
internal fun DrawScope.fitText(
    value: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    sizePx: Float,
    type: WidgetType,
    color: Color,
    weight: Int = 600,
    letterSpacingEm: Float = 0.10f,
    /**
     * Fin dove si puo' rimpicciolire il corpo, dopo aver stretto e prima di
     * troncare. 1 = mai: un nome di citta' troncato si riconosce lo stesso, e
     * il corpo fisso tiene ferma l'impaginazione. Una fase della luna no -
     * "GIBBOSA CRES…" non dice quale delle due - e li' conviene scendere.
     */
    minScale: Float = 1f,
): Paint {
    var brush = type.brush(sizePx, weight, WIDTH_WIDEST, letterSpacingEm)
    var axis = WIDTH_WIDEST
    while (type.widthOf(value, brush) > maxWidth && axis > WIDTH_NARROWEST) {
        axis -= WIDTH_STEP
        brush = type.brush(sizePx, weight, axis, letterSpacingEm)
    }
    var size = sizePx
    while (type.widthOf(value, brush) > maxWidth && size * 0.95f >= sizePx * minScale) {
        size *= 0.95f
        brush = type.brush(size, weight, axis, letterSpacingEm)
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

/**
 * Spezza sulle parole, mai dentro una parola.
 *
 * Una parola piu' larga del riquadro finisce da sola sulla sua riga e sborda:
 * a quel punto e' il ciclo del chiamante a rimpicciolire il corpo, che e' il
 * rimedio giusto. Tagliarla qui vorrebbe dire consegnare una riga monca senza
 * che nessuno se ne accorga.
 */
internal fun wrap(
    text: String,
    width: Float,
    brush: android.graphics.Paint,
    type: WidgetType,
): List<String> {
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    text.split(' ').forEach { word ->
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (type.widthOf(candidate, brush) <= width || current.isEmpty()) {
            current = StringBuilder(candidate)
        } else {
            lines += current.toString()
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}

/**
 * Il pennello piu' grande, fino a [sizePx], con cui tutte [values] stanno in
 * [maxWidth]. Per le colonne: "OGGI" e' piu' larga di una colonna stretta, e
 * centrata ne usciva da entrambi i lati - la prima dal bordo del widget.
 */
internal fun fittingBrush(
    values: List<String>,
    maxWidth: Float,
    sizePx: Float,
    type: WidgetType,
    weight: Int,
    width: Int,
    letterSpacingEm: Float = 0f,
): Paint {
    var size = sizePx
    var brush = type.brush(size, weight, width, letterSpacingEm)
    while (values.any { type.widthOf(it, brush) > maxWidth } && size > 4f) {
        size *= 0.92f
        brush = type.brush(size, weight, width, letterSpacingEm)
    }
    return brush
}

private const val WIDTH_WIDEST = 78
private const val WIDTH_NARROWEST = 58
private const val WIDTH_STEP = 4

/**
 * Gli assi, nella forma che vuole `Paint.fontVariationSettings`.
 *
 * Portato qui da `ui/render/ExtrudedText.kt` (cancellato con la scultura 3D
 * dei numeri): il resto di quel file serviva solo al feed, ma questa riga
 * la usano ancora i widget.
 */
private fun numberTypeAxes(weight: Int, width: Int): String =
    "'wght' $weight, 'wdth' $width"
