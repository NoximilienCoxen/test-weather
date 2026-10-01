package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import io.github.noximiliencoxen.caelum.data.MoonPhase

/**
 * La luna vista come un disco: **una sola, per tre posti**.
 *
 * Erano tre disegni (CONTESTO §27.8): quello del cielo di Sala, l'iconetta del
 * cursore in Sala IV e quello del widget Luna, che passava da un motore 3D
 * tutto suo. Tre ombre diverse, tre gradienti, due serie di mari in posti
 * diversi: la stessa sera, la luna del widget e quella dell'app non si
 * somigliavano. Il riferimento e' la luna del cielo, che e' quella che si vede
 * di piu'.
 *
 * Fuori resta la grande luna di Sala IV (`luna3d`): e' una sfera con la sua
 * carta, che si gira col dito, e non un disco.
 *
 * **Le tinte sono fisse e non seguono il tema**: un corpo celeste non ha il
 * colore dell'inchiostro della pagina che lo mostra. Era gia' la regola in
 * Sala; il widget usava l'inchiostro del widget, e di giorno la sua luna era
 * grigia.
 *
 * La sagoma della parte illuminata viene da [MoonPhase]: semicerchio dal lato
 * illuminato piu' la mediana, che rientra quando e' falce e sporge quando e'
 * gibbosa.
 *
 * @param fase 0 novilunio, 0,5 plenilunio.
 */
internal fun DrawScope.discoLunare(centro: Offset, r: Float, fase: Float, alpha: Float = 1f) {
    if (r <= 0.5f || alpha <= 0.003f) return

    // **La luce cinerea.** La parte in ombra non e' nera: e' Terra che la
    // illumina, e a occhio nudo si vede - e' quel disco fantasma dentro la
    // falce. Quasi nera, al novilunio la luna spariva come spenta.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF2A3446).copy(alpha = 0.92f * alpha),
            1f to Color(0xFF141C28).copy(alpha = 0.80f * alpha),
            center = Offset(centro.x - r * 0.2f, centro.y - r * 0.2f),
            radius = r * 1.3f,
        ),
        radius = r,
        center = centro,
    )

    val crescente = MoonPhase.waxing(fase)
    val terminatore = MoonPhase.terminator(fase)
    val gibbosa = MoonPhase.illumination(fase) > 0.5f
    val disco = Rect(centro.x - r, centro.y - r, centro.x + r, centro.y + r)
    val mediana = Rect(centro.x - r * terminatore, centro.y - r, centro.x + r * terminatore, centro.y + r)
    val illuminata = Path().apply {
        arcTo(disco, if (crescente) -90f else 90f, 180f, true)
        arcTo(mediana, if (crescente) 90f else -90f, if (gibbosa) 180f else -180f, false)
        close()
    }

    clipPath(illuminata) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFFDF6),
                0.48f to SalaTokens.lunaLuce,
                1f to SalaTokens.lunaMezzo,
                center = Offset(centro.x - r * 0.32f, centro.y - r * 0.40f),
                radius = r * 1.5f,
            ),
            radius = r,
            center = centro,
            alpha = alpha,
        )
        // I mari restano dentro la parte illuminata: un mare che si vedesse
        // sull'ombra sarebbe una macchia.
        MariDellaLuna.forEach { (mx, my, md) ->
            drawOval(
                color = Color(0xFF6E6152).copy(alpha = 0.20f * alpha),
                topLeft = Offset(centro.x + mx * r - md * r, centro.y + my * r - md * r * 0.78f),
                size = Size(md * 2f * r, md * 1.56f * r),
            )
        }
    }

    // Un filo di contorno, perche' la sfera si stacchi anche da un fondo chiaro.
    drawCircle(
        color = SalaTokens.lunaBordo.copy(alpha = 0.30f * alpha),
        radius = r,
        center = centro,
        style = Stroke(width = r * 0.04f),
    )
}

/**
 * I mari della luna: scostamento in x, in y e diametro, in frazioni di raggio,
 * alle quote del prototipo.
 */
private val MariDellaLuna = listOf(
    Triple(-0.07f, -0.06f, 0.20f),
    Triple(0.24f, -0.23f, 0.14f),
    Triple(0.07f, 0.27f, 0.24f),
)
