package io.github.noximiliencoxen.caelum.wear

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import io.github.noximiliencoxen.caelum.ui.sala.GlifoMeteo
import io.github.noximiliencoxen.caelum.ui.sala.disegnaGlifo
import java.io.ByteArrayOutputStream

/**
 * Il fondo di Caelum sull'orologio: sfumatura, velo e colline.
 *
 * Lo disegnano sia l'app (in un `Canvas` di Compose) sia la Tile, che non ha un
 * disegno libero e lo riceve come immagine ([fondoPng]): lo stesso codice, cosi'
 * la Tile e la pagina Adesso hanno lo stesso cielo.
 */
fun DrawScope.disegnaCielo(cielo: CieloOrologio) {
    drawRect(cielo.brush)
    if (cielo.velo.alpha > 0f) drawRect(cielo.veloBrush(size.minDimension))
    colline(cielo)
}

/**
 * Le colline di Sala, rifatte per un quadrante: due dossi dietro che escono dai
 * lati, quello davanti tagliato dal bordo, e due alberi. Le tinte sono quelle
 * della palette, quindi di notte si spengono come sul telefono.
 */
private fun DrawScope.colline(cielo: CieloOrologio) {
    val p = cielo.palette
    val s = size.minDimension
    drawOval(p.collina1, topLeft = Offset(-0.22f * s, 0.78f * s), size = Size(0.80f * s, 0.56f * s))
    drawOval(p.collina2, topLeft = Offset(0.40f * s, 0.76f * s), size = Size(0.90f * s, 0.60f * s))

    fun albero(x: Float, piede: Float, alto: Float) {
        val tw = 0.022f * s
        drawRoundRect(
            p.tronco,
            topLeft = Offset(x - tw / 2f, piede - alto),
            size = Size(tw, alto),
            cornerRadius = CornerRadius(tw / 2f),
        )
        val d = alto * 1.15f
        drawOval(p.fronda, topLeft = Offset(x - d / 2f, piede - alto - d * 0.55f), size = Size(d, d))
    }
    // Ai lati della pastiglia della pioggia, non dietro: la piu' larga
    // ("Asciutto per 12 ore") arriva da 0,22 a 0,78 del quadrante.
    albero(0.15f * s, 0.82f * s, 0.065f * s)
    albero(0.85f * s, 0.80f * s, 0.055f * s)

    clipRect {
        drawOval(p.collina3, topLeft = Offset(-0.12f * s, 0.88f * s), size = Size(1.24f * s, 0.50f * s))
    }
}

/** Un disegno di Compose su una bitmap di [larghezza] x [altezza] pixel. */
fun bitmapDi(larghezza: Int, altezza: Int, disegno: DrawScope.() -> Unit): Bitmap {
    val bitmap = createBitmap(larghezza, altezza)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(bitmap.asImageBitmap()),
        Size(larghezza.toFloat(), altezza.toFloat()),
        disegno,
    )
    return bitmap
}

fun Bitmap.png(): ByteArray = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

/** Il fondo della Tile, quadrato di [lato] pixel. */
fun fondoPng(cielo: CieloOrologio, lato: Int): ByteArray = bitmapDi(lato, lato) { disegnaCielo(cielo) }.png()

/** La figurina del tempo, quella del telefono, come immagine di [lato] pixel. */
fun figurina(glifo: GlifoMeteo, notte: Boolean, lato: Int): Bitmap = bitmapDi(lato, lato) { disegnaGlifo(glifo, notte) }

/**
 * La figurina ridotta a sagoma bianca, per le complicazioni: il quadrante la
 * vuole di un colore solo e la tinge lui. Sole e nuvola diventano una forma
 * unica, che a dodici punti si legge meglio dei loro dettagli.
 */
fun sagoma(glifo: GlifoMeteo, notte: Boolean, lato: Int): Bitmap {
    val bitmap = figurina(glifo, notte, lato)
    android.graphics.Canvas(bitmap).drawPaint(
        Paint().apply {
            color = android.graphics.Color.WHITE
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        },
    )
    return bitmap
}
