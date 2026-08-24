package com.forli.meteo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.forli.meteo.ui.render.CanvasRenderer
import com.forli.meteo.ui.render.NumberPalette
import com.forli.meteo.ui.render.TemperatureRenderer

val LocalTemperatureRenderer = staticCompositionLocalOf<TemperatureRenderer> { CanvasRenderer() }

fun MeteoColors.toNumberPalette(): NumberPalette = NumberPalette(
    face = numberFace,
    sideNear = numberSideNear,
    sideFar = numberSideFar,
    chamfer = numberChamfer,
    iridescence = IridescenceStops,
    iridescenceAlpha = if (isDark) 0.42f else 0.30f,
    dropShadow = numberDropShadow,
)

/** Monospace maiuscola spaziata per etichette e valori. */
object MeteoType {
    val label = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.16.em,
    )
    val value = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.10.em,
    )
    val caption = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.20.em,
    )
}

@Composable
fun MeteoTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    val renderer = remember { CanvasRenderer() }
    CompositionLocalProvider(
        LocalMeteoColors provides colors,
        LocalTemperatureRenderer provides renderer,
        content = content,
    )
}
