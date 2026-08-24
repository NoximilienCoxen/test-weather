package com.forli.meteo.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.LocalTemperatureRenderer
import com.forli.meteo.ui.theme.toNumberPalette

/**
 * Disegna testo estruso passando dal TemperatureRenderer corrente.
 *
 * La cottura vive in un [remember] legato allo spec: il ciclo di vita dei tre
 * piani segue la composizione, invece di una cache nascosta dentro il renderer
 * con una sua politica di sfratto. Il [motion] non entra nella chiave, quindi
 * muovere l'oggetto non ricuoce mai nulla.
 */
@Composable
fun ExtrudedText(
    text: String,
    fontSize: Dp,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.26f,
    verticalBias: Float = -0.10f,
    motion: NumberMotion = NumberMotion.Fermo,
) {
    val colors = LocalMeteoColors.current
    val renderer = LocalTemperatureRenderer.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fontFamilyResolver = LocalFontFamilyResolver.current

    // Misuratore senza cache, deliberatamente. La chiave della cache predefinita
    // si basa sugli attributi che influenzano il layout, e colore e pennello non
    // lo influenzano: due misurazioni che differiscono solo per la pittura
    // possono restituire lo stesso oggetto, e vince lo stile della prima. E'
    // cosi' che faccia e smusso sparivano lasciando solo il filo iridescente.
    // Qui non perdiamo nulla: il disegno e' gia' in cache come bitmap.
    val measurer = remember(fontFamilyResolver, density, layoutDirection) {
        TextMeasurer(
            defaultFontFamilyResolver = fontFamilyResolver,
            defaultDensity = density,
            defaultLayoutDirection = layoutDirection,
            cacheSize = 0,
        )
    }
    val palette = remember(colors) { colors.toNumberPalette() }

    BoxWithConstraints(modifier) {
        val fontPx = with(density) { fontSize.toPx() }
        val depthPx = with(density) { depth.toPx() }
        val availableWidthPx = with(density) { maxWidth.toPx() }

        val spec = remember(text, fontPx, depthPx, availableWidthPx, palette) {
            NumberSpec(
                text = text,
                fontSizePx = fontPx,
                palette = palette,
                depthPx = depthPx,
                maxWidthPx = availableWidthPx,
            )
        }

        val baked = remember(spec, layoutDirection, renderer) {
            renderer.bake(density, layoutDirection, measurer, spec)
        }

        Canvas(Modifier.fillMaxSize()) {
            val current = baked ?: return@Canvas
            renderer.draw(
                scope = this,
                baked = current,
                center = Offset(size.width / 2f, size.height * (0.5f + verticalBias)),
                motion = motion,
            )
        }
    }
}
