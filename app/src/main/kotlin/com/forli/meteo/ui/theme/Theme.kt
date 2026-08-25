package com.forli.meteo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.forli.meteo.R
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

/**
 * Un carattere solo per tutta l'app: Archivo, lo stesso della cifra gigante.
 *
 * Prima l'interfaccia usava il monospace di sistema e la cifra usava Archivo, e
 * le due meta' della schermata non si parlavano. Archivo e' **variabile**, quindi
 * la gerarchia si fa con gli assi invece che con file diversi: le etichette
 * strette e pesanti, i valori normali. Costo zero, perche' il file c'era gia'.
 *
 * **Il monospace resta dove serve la larghezza fissa**, e cioe' su un numero solo:
 * l'ora sotto la barra. Scorrendo, "09:00" e "14:00" devono restare incolonnate,
 * e con un carattere proporzionale l'etichetta ballerebbe da sinistra a destra a
 * ogni ora.
 */
private fun archivo(weight: Int, width: Float): FontFamily = FontFamily(
    Font(
        resId = R.font.archivo_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
        ),
    ),
)

object MeteoType {
    /** La domanda del benvenuto: e' l'unica riga grande che non sia una cifra. */
    val title = TextStyle(
        fontFamily = archivo(weight = 620, width = 88f),
        fontSize = 25.sp,
        letterSpacing = 0.02.em,
        lineHeight = 30.sp,
    )
    val label = TextStyle(
        fontFamily = archivo(weight = 600, width = 82f),
        fontSize = 14.sp,
        letterSpacing = 0.10.em,
    )
    val value = TextStyle(
        fontFamily = archivo(weight = 450, width = 100f),
        fontSize = 14.sp,
        letterSpacing = 0.04.em,
    )
    val caption = TextStyle(
        fontFamily = archivo(weight = 560, width = 78f),
        fontSize = 12.sp,
        letterSpacing = 0.13.em,
    )

    /** Solo per cio' che deve restare incolonnato mentre i valori cambiano. */
    val tabular = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.18.em,
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
