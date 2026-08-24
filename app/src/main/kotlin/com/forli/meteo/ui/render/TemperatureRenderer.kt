package com.forli.meteo.ui.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

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
    /** Ristampe minime lungo il vettore di estrusione. */
    val steps: Int = 26,
    /** Direzione dell'estrusione: giu' a destra, opposta alla luce. */
    val angleDeg: Float = 62f,
    /** Larghezza disponibile: oltre questa la cifra viene rimpicciolita. */
    val maxWidthPx: Float = Float.MAX_VALUE,
)

/** Un piano della cifra, con la sua posizione rispetto all'origine composita. */
@Immutable
data class NumberLayer(
    val image: ImageBitmap,
    val offset: Offset,
)

/**
 * La cifra gia' disegnata, divisa nei tre piani che la compongono.
 *
 * Tenerli separati e' cio' che permette al movimento di costare nulla: i piani
 * si disegnano una volta sola e poi scorrono l'uno rispetto all'altro. Fonderli
 * in un'unica immagine obbligherebbe a ridisegnare tutto a ogni fotogramma.
 */
@Immutable
data class BakedNumber(
    /** Estrusione e ombra portata: il piano che sta dietro. */
    val body: NumberLayer,
    /** Faccia frontale e smusso: l'ancora, resta ferma. */
    val face: NumberLayer,
    /** Iridescenza: il filo sugli smussi. */
    val sheen: NumberLayer,
    val width: Float,
    val height: Float,
    /** Ampiezza massima dello scorrimento fra i piani. */
    val parallaxPx: Float,
    val sheenAlpha: Float,
)

/**
 * Come l'oggetto reagisce. Sta fuori da [NumberSpec] di proposito: lo spec
 * descrive la geometria da cuocere, questo descrive il movimento, e il
 * movimento non deve mai far ricuocere nulla.
 */
@Immutable
data class NumberMotion(
    /** Inclinazione del dispositivo, da -1 a 1 sui due assi. */
    val tilt: Offset = Offset.Zero,
    /** Spinta del dito, in pixel. */
    val push: Offset = Offset.Zero,
    /** Scorrimento dell'iridescenza lungo gli smussi. */
    val sheenShift: Float = 0f,
) {
    companion object {
        val Fermo = NumberMotion()
    }
}

/**
 * Unico punto da cui passa il disegno della cifra gigante.
 *
 * Diviso in due tempi: [bake] fa il lavoro costoso una volta sola, [draw] si
 * limita a comporre i piani. Per sostituire il disegno su Canvas con un motore
 * 3D vero bastera' aggiungere un FilamentRenderer, senza toccare le schermate.
 */
interface TemperatureRenderer {

    fun bake(
        density: Density,
        layoutDirection: LayoutDirection,
        measurer: TextMeasurer,
        spec: NumberSpec,
    ): BakedNumber?

    fun draw(
        scope: DrawScope,
        baked: BakedNumber,
        center: Offset,
        motion: NumberMotion = NumberMotion.Fermo,
    )
}
