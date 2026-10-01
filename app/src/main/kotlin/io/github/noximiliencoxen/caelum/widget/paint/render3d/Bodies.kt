package io.github.noximiliencoxen.caelum.widget.paint.render3d
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Il bagliore dietro la luna del widget Luna.
 *
 * **Qui c'erano anche il sole a sfera, la sua corona di raggi e le masse della
 * nuvola**, sostituiti dalle figurette a colori dell'app (`disegnaGlifo`), e
 * poi la luna stessa, con mari e ombra suoi: adesso il widget disegna la luna
 * dell'app (`discoLunare`), e qui resta solo l'alone.
 */

/**
 * Un bagliore proprio: un alone che sfuma a trasparente, dietro al corpo.
 *
 * Non e' la sfumatura della sfera - quella racconta come la luce esterna
 * colpisce una superficie opaca. Questo e' l'opposto: il corpo che emette
 * luce sua, indipendente da dove sta la lampada della scena. Va disegnato
 * *prima* del disco, cosi' il disco gli sta sopra e l'alone resta un contorno
 * intorno, non una macchia che lo attraversa.
 */
fun DrawScope.glow(
    camera: Camera,
    x: Float,
    y: Float,
    z: Float,
    radius: Float,
    color: Color,
    alpha: Float,
    spread: Float = 2.4f,
) {
    if (alpha <= 0.003f) return
    camera.place(x, y, z)
    val r = radius * camera.scale
    if (r <= 0.5f) return
    val centre = Offset(camera.sx, camera.sy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = centre,
            radius = r * spread,
        ),
        radius = r * spread,
        center = centre,
    )
}
