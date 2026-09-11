package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Una macchia d'acquerello: centro e raggio nel sistema di riferimento in cui
 * e' stata disegnata Sala v3 (411 x 914, che sono anche i punti Android di un
 * telefono di riferimento), e a quale delle tre tinte di [washColors] attinge.
 *
 * Il turbinio che nel prototipo deforma il bordo (il filtro SVG `feTurbulence`)
 * non e' stato riprodotto: qui la macchia resta un cerchio pulito che sfuma a
 * trasparente, che e' gia' il grosso dell'effetto acquerello. Chi vuole il
 * bordo irregolare puo' aggiungerlo con un `RenderEffect` (API 31+); non
 * sembrava valere la spesa per un bordo che sfuma comunque a zero.
 */
data class WashBlobSpec(val cx: Float, val cy: Float, val radius: Float, val colorIndex: Int)

/** Le tre macchie di ogni sala, nel sistema di riferimento 411 x 914. */
object SalaBlobs {
    const val REF_W = 411f
    const val REF_H = 914f

    val oggi = listOf(
        WashBlobSpec(200f, 140f, 310f, 0),
        WashBlobSpec(251f, 380f, 300f, 1),
        WashBlobSpec(190f, 680f, 290f, 2),
    )
    val settimana = listOf(
        WashBlobSpec(190f, 320f, 270f, 1),
        WashBlobSpec(271f, 680f, 270f, 2),
    )
    val pioggia = listOf(
        WashBlobSpec(140f, 100f, 285f, 0),
        WashBlobSpec(271f, 814f, 280f, 1),
    )
    val luna = listOf(
        WashBlobSpec(180f, 210f, 285f, 0),
        WashBlobSpec(271f, 660f, 270f, 2),
    )
    val aria = listOf(
        WashBlobSpec(150f, 90f, 290f, 2),
        WashBlobSpec(291f, 804f, 270f, 0),
    )
    val vento = listOf(
        WashBlobSpec(271f, 220f, 280f, 1),
        WashBlobSpec(140f, 680f, 270f, 2),
    )
    val uv = listOf(
        WashBlobSpec(180f, 190f, 285f, 0),
        WashBlobSpec(271f, 650f, 280f, 1),
    )
    val localita = listOf(
        WashBlobSpec(170f, 80f, 295f, 1),
        WashBlobSpec(301f, 814f, 270f, 2),
    )
    val impostazioni = listOf(
        WashBlobSpec(140f, 50f, 285f, 2),
    )
}

/**
 * Il fondo di una sala: carta, macchie d'acquerello, velo di leggibilita'.
 *
 * L'ordine e' quello del prototipo e non e' arbitrario: la carta sotto tutto,
 * le macchie sopra la carta, il velo sopra le macchie a spegnere i bordi
 * dov'e' cucito il contenuto — cosi' testo e comandi cadono sempre sulla
 * parte piu' piatta della pagina, mai in mezzo a una macchia piena.
 */
@Composable
fun SalaBackground(
    palette: SalaPalette,
    blobs: List<WashBlobSpec>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // La carta sta **sotto le macchie**, come su un foglio vero: la fibra e' la
    // pagina, non un filtro appiccicato sopra il disegno.
    val acquerello = LocalAcquerello.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.ground)
            .drawBehind { granaDiCarta(acquerello, forza = if (palette.dark) 0.14f else 0.09f) }
            .drawBehind {
                val sx = size.width / SalaBlobs.REF_W
                val sy = size.height / SalaBlobs.REF_H
                val scale = (sx + sy) / 2f
                blobs.forEach { blob ->
                    val color = palette.wash.getOrElse(blob.colorIndex) { Color.Transparent }
                    if (color.alpha <= 0f) return@forEach
                    val center = Offset(blob.cx * sx, blob.cy * sy)
                    val radius = blob.radius * scale
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to color,
                            0.67f to color.copy(alpha = 0f),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            .background(palette.veil),
        content = content,
    )
}
