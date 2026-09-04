package io.github.noximiliencoxen.caelum.widget.paint

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Il riquadro su cui si disegna: quanto e' grande davvero, e con che taglio.
 *
 * @param scale pixel per dp. Dentro il disegno tutto si misura in dp, e questa
 *   e' la densita' che li converte: un solo numero in pixel scritto a mano
 *   romperebbe il disegno su un telefono con densita' diversa.
 */
internal class Frame(
    val widthPx: Int,
    val heightPx: Int,
    val scale: Float,
    val cut: Cut,
    val cornerDp: Float,
) {
    val widthDp: Float get() = widthPx / scale
    val heightDp: Float get() = heightPx / scale
}

/** Quanto sta in un widget: cambia cosa si disegna, non solo quanto grande. */
internal enum class Cut { PICCOLO, MEDIO, GRANDE }

/**
 * Misura il riquadro e prepara la tela.
 *
 * La dimensione vera la sa solo il sistema, e la tiene nelle opzioni del widget:
 * quella dichiarata nel provider e' solo un desiderio, e un widget allargato a
 * mano non le somiglia piu'.
 */
internal object WidgetCanvas {

    /**
     * Pixel per dp con cui si disegna.
     *
     * Non la densita' vera dello schermo: a tre pixel per dp un widget grande
     * diventa un'immagine da megabyte, e sopra i due il guadagno non si vede.
     * Sotto uno e mezzo, invece, le scritte piccole si sfaldano.
     */
    private const val SCALA_MIN = 1.5f
    private const val SCALA_MAX = 2.4f

    /**
     * Quanti pixel al massimo, in tutto.
     *
     * Le immagini dei widget passano per il sistema, che ne somma il peso e
     * rifiuta l'aggiornamento se si esagera: un widget fermo sull'immagine
     * vecchia e' il modo peggiore di fallire, perche' non se ne accorge nessuno.
     */
    private const val PIXEL_MAX = 700_000f

    /** Sotto questa larghezza il numero e la scritta non ci stanno affiancati. */
    private val LARGO_DA = 240.dp
    private val ALTO_DA = 240.dp

    fun plan(context: Context, appWidgetId: Int): Frame {
        val (wDp, hDp) = boxDp(context, appWidgetId)

        var scale = context.resources.displayMetrics.density
            .coerceIn(SCALA_MIN, SCALA_MAX)
        val pixels = wDp * scale * hDp * scale
        if (pixels > PIXEL_MAX) scale *= sqrt(PIXEL_MAX / pixels)

        val cut = when {
            wDp >= LARGO_DA.value && hDp >= ALTO_DA.value -> Cut.GRANDE
            wDp >= LARGO_DA.value -> Cut.MEDIO
            else -> Cut.PICCOLO
        }

        return Frame(
            widthPx = (wDp * scale).roundToInt().coerceAtLeast(1),
            heightPx = (hDp * scale).roundToInt().coerceAtLeast(1),
            scale = scale,
            cut = cut,
            cornerDp = cornerDp(context),
        )
    }

    /**
     * Disegna e restituisce l'immagine.
     *
     * Il fondo lo mette qui, arrotondato: sotto Android 12 nessuno ritaglia il
     * widget, e un rettangolo con gli spigoli vivi su una schermata piena di
     * angoli tondi si vede subito.
     */
    fun paint(frame: Frame, background: Int, body: DrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(
            frame.widthPx,
            frame.heightPx,
            Bitmap.Config.ARGB_8888,
        )
        CanvasDrawScope().draw(
            Density(frame.scale, 1f),
            LayoutDirection.Ltr,
            // L'involucro condivide i pixel con la bitmap: disegnare qui e'
            // disegnare la', non se ne fa una copia.
            Canvas(bitmap.asImageBitmap()),
            Size(frame.widthPx.toFloat(), frame.heightPx.toFloat()),
        ) {
            drawRoundRect(
                color = Color(background),
                size = size,
                cornerRadius = CornerRadius(frame.cornerDp.dp.toPx()),
            )
            body()
        }
        return bitmap
    }

    /** La misura del riquadro in dp, come la conosce il sistema. */
    private fun boxDp(context: Context, appWidgetId: Int): Pair<Float, Float> {
        val manager = AppWidgetManager.getInstance(context)
        val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
        val orizzontale =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // In verticale il sistema da' la larghezza minima e l'altezza massima,
        // in orizzontale il contrario: sono le due forme che il widget assume
        // ruotando il telefono, non due misure dello stesso riquadro.
        val w = options?.getInt(
            if (orizzontale) {
                AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            },
            0,
        ) ?: 0
        val h = options?.getInt(
            if (orizzontale) {
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            },
            0,
        ) ?: 0

        // Al primo posizionamento le opzioni sono ancora vuote: si ripiega su
        // quanto il provider aveva chiesto.
        if (w > 0 && h > 0) return w.toFloat() to h.toFloat()
        val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()
        val fallbackW = info?.minWidth?.takeIf { it > 0 }
            ?.let { it / context.resources.displayMetrics.density }
        val fallbackH = info?.minHeight?.takeIf { it > 0 }
            ?.let { it / context.resources.displayMetrics.density }
        return (fallbackW ?: 150f) to (fallbackH ?: 150f)
    }

    /** Lo stesso raggio che il sistema usa per i widget, quando lo dichiara. */
    private fun cornerDp(context: Context): Float = runCatching {
        val id = android.R.dimen.system_app_widget_background_radius
        context.resources.getDimension(id) / context.resources.displayMetrics.density
    }.getOrNull() ?: 16f
}
