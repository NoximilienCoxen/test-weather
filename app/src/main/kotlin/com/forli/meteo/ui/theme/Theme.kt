package com.forli.meteo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.forli.meteo.ui.render.NumberPalette
import com.forli.meteo.ui.render.TemperatureRenderer
import com.forli.meteo.ui.render3d.PrismRenderer

val LocalTemperatureRenderer = staticCompositionLocalOf<TemperatureRenderer> { PrismRenderer() }

fun MeteoColors.toNumberPalette(): NumberPalette = NumberPalette(
    face = numberFace,
    sideNear = numberSideNear,
    sideFar = numberSideFar,
    chamfer = numberChamfer,
    iridescence = IridescenceStops,
    iridescenceAlpha = 0.55f,
    shadowAlpha = numberShadowAlpha,
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
    colors: MeteoColors,
    content: @Composable () -> Unit,
) {
    val renderer = remember { PrismRenderer() }
    CompositionLocalProvider(
        LocalMeteoColors provides colors,
        LocalTemperatureRenderer provides renderer,
        content = content,
    )
}
