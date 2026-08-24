package com.forli.meteo.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.LocalTemperatureRenderer
import com.forli.meteo.ui.theme.toNumberPalette

/**
 * Disegna testo estruso passando dal TemperatureRenderer corrente.
 * Il Canvas viene lasciato piu' alto del glifo perche' l'estrusione
 * e l'ombra escono dal riquadro del testo.
 */
@Composable
fun ExtrudedText(
    text: String,
    fontSize: Dp,
    modifier: Modifier = Modifier,
    depth: Dp = fontSize * 0.26f,
    verticalBias: Float = -0.10f,
) {
    val colors = LocalMeteoColors.current
    val renderer = LocalTemperatureRenderer.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val fontPx = with(density) { fontSize.toPx() }
    val depthPx = with(density) { depth.toPx() }
    val palette = remember(colors) { colors.toNumberPalette() }
    val spec = remember(text, fontPx, depthPx, palette) {
        NumberSpec(
            text = text,
            fontSizePx = fontPx,
            palette = palette,
            depthPx = depthPx,
        )
    }

    Canvas(modifier) {
        renderer.draw(
            scope = this,
            measurer = measurer,
            spec = spec,
            center = Offset(size.width / 2f, size.height * (0.5f + verticalBias)),
        )
    }
}
