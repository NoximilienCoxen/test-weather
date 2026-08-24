package com.forli.meteo.ui.render

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
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
     * renderer che lavora gia' a livello di android.graphics e' anche la
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
 * Disegna testo estruso passando dal TemperatureRenderer corrente.
 *
 * L'estrazione della geometria vive in un [remember] legato allo spec:
 * l'orientamento non entra nella chiave, quindi ruotare non riestrae nulla.
 */
@Composable
fun ExtrudedText(
    text: String,
    fontSize: Dp,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.15f,
    verticalBias: Float = -0.08f,
    motion: NumberMotion = NumberMotion.Fermo,
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

        val spec = remember(text, typeface, fontPx, depthPx, availableWidthPx, palette) {
            NumberSpec(
                text = text,
                typeface = typeface,
                fontSizePx = fontPx,
                palette = palette,
                depthPx = depthPx,
                maxWidthPx = availableWidthPx,
            )
        }

        val prepared = remember(spec, renderer) { renderer.prepare(spec) }

        Canvas(Modifier.fillMaxSize()) {
            val current = prepared ?: return@Canvas
            renderer.draw(
                scope = this,
                prepared = current,
                center = Offset(size.width / 2f, size.height * (0.5f + verticalBias)),
                motion = motion,
            )
        }
    }
}
