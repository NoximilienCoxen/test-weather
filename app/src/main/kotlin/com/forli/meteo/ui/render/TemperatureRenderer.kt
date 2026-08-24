package com.forli.meteo.ui.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer

/**
 * Materiale della cifra: plastica bianca opaca fresata, spigoli smussati netti.
 * Nessun bagliore, nessun alone, nessun riflesso speculare.
 */
@Immutable
data class NumberPalette(
    val face: Color,
    val sideNear: Color,
    val sideFar: Color,
    val chamfer: Color,
    val iridescence: List<Color>,
    val iridescenceAlpha: Float,
    val dropShadow: Boolean,
)

@Immutable
data class NumberSpec(
    val text: String,
    val fontSizePx: Float,
    val palette: NumberPalette,
    /** Profondita' dell'estrusione, in pixel. */
    val depthPx: Float,
    /** Numero di ristampe lungo il vettore di estrusione. */
    val steps: Int = 26,
    /** Direzione dell'estrusione: giu' a destra, opposta alla luce. */
    val angleDeg: Float = 62f,
)

/**
 * Unico punto da cui passa il disegno della cifra gigante.
 *
 * Tutto il resto dell'app parla solo con questa interfaccia: per sostituire il
 * disegno su Canvas con un motore 3D vero bastera' aggiungere un
 * FilamentRenderer e cambiare l'istanza fornita, senza toccare le schermate.
 */
interface TemperatureRenderer {
    fun draw(scope: DrawScope, measurer: TextMeasurer, spec: NumberSpec, center: Offset)
}
