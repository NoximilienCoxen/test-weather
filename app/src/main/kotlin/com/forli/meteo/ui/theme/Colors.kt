package com.forli.meteo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette dell'app. Due soli set, chiaro e scuro, definiti esattamente come da specifica.
 * Il fondo chiaro non e' bianco puro: un oggetto bianco su bianco sparirebbe.
 */
@Immutable
data class MeteoColors(
    val background: Color,
    val text: Color,
    val label: Color,
    val line: Color,
    /** Faccia frontale della cifra: satinata, piatta, senza gradiente colorato. */
    val numberFace: Color,
    /** Faccia laterale piu' vicina alla faccia frontale. */
    val numberSideNear: Color,
    /** Faccia laterale piu' lontana, in fondo all'estrusione. */
    val numberSideFar: Color,
    /** Smusso a 45 gradi rivolto verso la luce (alto a sinistra). */
    val numberChamfer: Color,
    /** Il tema chiaro guadagna definizione con un'ombra portata; lo scuro no. */
    val numberDropShadow: Boolean,
    val pillBackground: Color,
    val pillText: Color,
    val isDark: Boolean,
)

val DarkColors = MeteoColors(
    background = Color(0xFF000000),
    text = Color(0xFFFFFFFF),
    label = Color(0xFF8A8A8E),
    line = Color(0xFF3A3A3C),
    numberFace = Color(0xFFFFFFFF),
    numberSideNear = Color(0xFFE6E6EA),
    numberSideFar = Color(0xFF4A4A4E),
    // Lo smusso e' una faccia inclinata: prende meno luce di quella frontale,
    // quindi va sotto di essa. Averlo tenuto piu' chiaro della faccia era un
    // errore fisico, e faceva leggere la cifra come un contorno vuoto.
    numberChamfer = Color(0xFFE0E0E4),
    numberDropShadow = false,
    pillBackground = Color(0xFFFFFFFF),
    pillText = Color(0xFF000000),
    isDark = true,
)

val LightColors = MeteoColors(
    background = Color(0xFFEFEFF2),
    text = Color(0xFF000000),
    label = Color(0xFF6C6C70),
    line = Color(0xFFD0D0D5),
    numberFace = Color(0xFFFFFFFF),
    numberSideNear = Color(0xFFF4F4F7),
    numberSideFar = Color(0xFF9A9AA0),
    numberChamfer = Color(0xFFE8E8EC),
    numberDropShadow = true,
    pillBackground = Color(0xFF000000),
    pillText = Color(0xFFFFFFFF),
    isDark = false,
)

/**
 * Iridescenza: azzurro, rosa e giallo tenui, mai saturi. Le fasce trasparenti
 * interposte servono a spezzare il bordo, cosi' la rifrazione non gira uniforme
 * attorno a tutta la sagoma e resta entro il 10-15% di superficie.
 */
val IridescenceStops: List<Color> = listOf(
    Color(0x00FFFFFF),
    Color(0xFF9FD2E8),
    Color(0x00FFFFFF),
    Color(0xFFE3B9CE),
    Color(0x00FFFFFF),
    Color(0xFFEDE3B4),
    Color(0x00FFFFFF),
)

val LocalMeteoColors = staticCompositionLocalOf { DarkColors }
