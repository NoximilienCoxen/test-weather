package com.forli.meteo.ui.render

import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

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
    val typeface: Typeface,
    val fontSizePx: Float,
    val palette: NumberPalette,
    /** Profondita' dell'estrusione, in pixel. */
    val depthPx: Float,
    /** Larghezza disponibile: oltre questa la cifra viene rimpicciolita. */
    val maxWidthPx: Float = Float.MAX_VALUE,
    val letterSpacingEm: Float = -0.02f,
)

/**
 * Come l'oggetto e' orientato.
 *
 * Non e' piu' uno spostamento: [orientationDeg] ruota la luce e, in misura
 * minore, la direzione dell'estrusione. Con facce che conoscono la propria
 * normale questo si legge come una rotazione vera, perche' ogni superficie
 * cambia tono per conto proprio invece di scorrere insieme alle altre.
 */
@Immutable
data class NumberMotion(
    val orientationDeg: Float = REST_ORIENTATION,
) {
    companion object {
        /** Luce da sinistra in alto, come da specifica. */
        const val REST_ORIENTATION = 225f
        val Fermo = NumberMotion()
    }
}

/** Geometria gia' estratta, pronta a essere illuminata in qualunque direzione. */
interface PreparedNumber {
    val width: Float
    val height: Float
}

/**
 * Unico punto da cui passa il disegno della cifra gigante.
 *
 * [prepare] estrae la geometria, che dipende solo dal testo e dal corpo.
 * [draw] la illumina, che dipende dall'orientamento. Separarli permette di
 * ruotare senza rifare il lavoro di estrazione.
 */
interface TemperatureRenderer {
    fun prepare(spec: NumberSpec): PreparedNumber?
    fun draw(
        scope: DrawScope,
        prepared: PreparedNumber,
        center: Offset,
        motion: NumberMotion = NumberMotion.Fermo,
    )
}
