package com.forli.meteo.ui.render

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import com.forli.meteo.ui.render3d.SceneContact
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.LocalTemperatureRenderer
import com.forli.meteo.ui.theme.toNumberPalette

/**
 * Corpo del carattere della cifra. Archivo e' variabile su peso e larghezza,
 * quindi la proporzione si regola qui senza cambiare file di font.
 */
object NumberType {
    const val WEIGHT = 700
    const val WIDTH = 72

    /**
     * Typeface.Builder non accetta un identificativo di risorsa: vuole
     * l'AssetManager e un percorso. Il font sta quindi negli assets, che per un
     * disegno che lavora gia' a livello di android.graphics e' anche la
     * collocazione naturale.
     */
    const val PATH = "fonts/archivo_variable.ttf"
}

@Composable
fun rememberNumberTypeface(
    weight: Int = NumberType.WEIGHT,
    width: Int = NumberType.WIDTH,
): Typeface {
    val context = LocalContext.current
    return remember(weight, width) {
        runCatching {
            Typeface.Builder(context.assets, NumberType.PATH)
                .setFontVariationSettings("'wght' $weight, 'wdth' $width")
                .build()
        }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
}

/**
 * Disegna testo come solido, passando dal renderer corrente.
 *
 * L'orientamento arriva come funzione e non come valore: viene letto dentro il
 * disegno, quindi ruotare ridipinge e basta. Passandolo come parametro, ogni
 * grado di rotazione ricomporrebbe l'albero, e la rotazione e' un gesto
 * continuo che ne produce centinaia.
 */
@Composable
fun ExtrudedText(
    text: String,
    fontSize: Dp,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.17f,
    verticalBias: Float = 0f,
    /** Quanti caratteri finali sono un simbolo in corpo ridotto. */
    smallTail: Int = 0,
    motion: () -> NumberMotion = { NumberMotion.Fermo },
    /** Chi vuole sapere dove l'oggetto offre superficie. Di norma la pioggia. */
    contact: SceneContact? = null,
    typeface: Typeface = rememberNumberTypeface(),
) {
    val colors = LocalMeteoColors.current
    val renderer = LocalTemperatureRenderer.current
    val density = LocalDensity.current
    val palette = remember(colors) { colors.toNumberPalette() }

    BoxWithConstraints(modifier) {
        val fontPx = with(density) { fontSize.toPx() }
        val depthPx = with(density) { depth.toPx() }
        val availableWidthPx = with(density) { maxWidth.toPx() }

        val spec = remember(text, typeface, fontPx, depthPx, availableWidthPx, smallTail) {
            NumberSpec(
                text = text,
                typeface = typeface,
                fontSizePx = fontPx,
                depthPx = depthPx,
                maxWidthPx = availableWidthPx,
                smallTail = smallTail,
            )
        }

        val prepared = remember(spec, renderer) { renderer.prepare(spec) }


        Canvas(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    contact?.skyline?.origin = coordinates.positionInRoot()
                },
        ) {
            val current = prepared ?: return@Canvas
            renderer.draw(
                scope = this,
                prepared = current,
                center = Offset(size.width / 2f, size.height * (0.5f + verticalBias)),
                palette = palette,
                motion = motion(),
                silhouette = contact?.skyline,
            )
        }
    }
}
