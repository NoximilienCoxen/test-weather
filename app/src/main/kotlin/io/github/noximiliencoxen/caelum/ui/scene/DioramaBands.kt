package io.github.noximiliencoxen.caelum.ui.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * Il diorama per chi non ha AGSL: la scena in tre lastre di vetro.
 *
 * `RuntimeShader` esiste dalla 33 e il minimo di questa app e' la 26, quindi
 * sotto quella soglia non c'e' modo di spostare un pixel in funzione della sua
 * profondita'. Quello che si puo' fare e' spostare **lastre intere** a velocita'
 * diverse, che e' la parallasse come la facevano i cartoni animati con i piani
 * di vetro sotto la macchina da presa - e infatti si legge esattamente cosi'.
 *
 * **Non e' lo stesso disegno con meno qualita', e' un disegno diverso**, ed e'
 * onesto dirlo: fra una lastra e l'altra il salto c'e', e su un profilo di
 * collina che attraversa due fasce si vede un gradino quando la scena si muove.
 * Il bordo morbido con cui [SceneLoader] ritaglia le fasce lo attenua, non lo
 * toglie.
 *
 * **Anche qui il fondo si sostituisce per primo.** La dissolvenza sfalsata e'
 * l'idea, non un effetto dello shader: qui la si ottiene dando a ciascuna
 * lastra la propria opacita', con la fascia lontana che finisce prima. Su tre
 * gradini invece che su un continuo, ma nello stesso verso.
 */
internal class DioramaBands {

    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    private val matrix = Matrix()

    fun draw(
        canvas: Canvas,
        now: ScenePlates,
        next: ScenePlates?,
        progress: Float,
        tiltX: Float,
        tiltY: Float,
        drift: Float,
        near: Color,
        far: Color,
        tint: Float,
        width: Float,
        height: Float,
    ) {
        BAND_CENTRE.forEachIndexed { index, depth ->
            val distance = 1f - depth
            val dx = tiltX * distance + drift * distance * distance
            val dy = tiltY * distance
            val filter = tintFilter(near, far, depth, tint)

            // La soglia locale della fascia: identica a quella dello shader, per
            // non avere due leggi diverse sullo stesso movimento.
            val local = if (next == null) 0f else {
                val raw = (progress * (1f + STAGGER) - depth * STAGGER).coerceIn(0f, 1f)
                raw * raw * (3f - 2f * raw)
            }

            now.bands.getOrNull(index)?.let {
                plate(canvas, it, dx, dy, width, height, filter, 1f - local)
            }
            if (local > 0f) {
                next?.bands?.getOrNull(index)?.let {
                    plate(canvas, it, dx, dy, width, height, filter, local)
                }
            }
        }
    }

    private fun plate(
        canvas: Canvas,
        bitmap: Bitmap,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float,
        filter: ColorMatrixColorFilter,
        alpha: Float,
    ) {
        if (alpha <= 0.004f) return
        val scale = max(width / bitmap.width, height / bitmap.height)
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (width - bitmap.width * scale) / 2f + dx,
            (height - bitmap.height * scale) / 2f + dy,
        )
        paint.colorFilter = filter
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    /**
     * La tinta dell'ora come moltiplicazione per canale.
     *
     * Una `ColorMatrix` di sola scala fa esattamente quello che fa la
     * moltiplicazione dentro lo shader, e [tint] dosa quanto: a zero i fattori
     * valgono uno e il dipinto esce com'e'.
     */
    private fun tintFilter(near: Color, far: Color, depth: Float, tint: Float): ColorMatrixColorFilter {
        fun channel(a: Float, b: Float): Float {
            val mixed = a + (b - a) * depth
            return 1f + (mixed - 1f) * tint
        }
        val r = channel(far.red, near.red)
        val g = channel(far.green, near.green)
        val b = channel(far.blue, near.blue)
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    r, 0f, 0f, 0f, 0f,
                    0f, g, 0f, 0f, 0f,
                    0f, 0f, b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    private companion object {
        /** Il centro di ciascuna fascia, negli stessi termini di `SceneLoader`. */
        val BAND_CENTRE = floatArrayOf(0.18f, 0.52f, 0.86f)
        const val STAGGER = 0.60f
    }
}
