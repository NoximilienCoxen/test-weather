package com.forli.meteo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.forli.meteo.data.SkyState

/**
 * Un tema solo, che segue l'ora invece del sistema.
 *
 * I due temi separati erano il problema, non la soluzione. Col chiaro il sole
 * bianco spariva nel fondo bianco, e di notte non si capiva se quella palla
 * fosse il sole o la luna. Con un fondo grigio che si scurisce verso le ore
 * notturne, un sole giallo e una luna bianca si distinguono sempre, e lo
 * scorrimento della barra racconta anche il passare della luce.
 *
 * Chiaro e scuro non erano nemmeno una scelta sensata da offrire: sono la
 * stessa informazione che l'app gia' possiede, chiesta due volte.
 */
@Immutable
data class MeteoColors(
    val background: Color,
    val text: Color,
    val label: Color,
    val line: Color,
    /** Faccia frontale della cifra: satinata, piatta, senza gradiente colorato. */
    val numberFace: Color,
    /** Parete piu' vicina alla faccia frontale. */
    val numberSideNear: Color,
    /** Parete piu' lontana, in fondo allo spessore. */
    val numberSideFar: Color,
    /** Smusso rivolto verso la luce. */
    val numberChamfer: Color,
    /** Quanto stacca l'ombra portata: piu' il fondo e' chiaro, piu' serve. */
    val numberShadowAlpha: Float,
    val pillBackground: Color,
    val pillText: Color,
    /** Sole: nucleo e ombra, gia' mescolati fra il giallo del giorno e il rosso radente. */
    val sunCore: Color,
    val sunShade: Color,
    val moonCore: Color,
    val moonShade: Color,
    /** Nuvola asciutta. */
    val cloudCore: Color,
    val cloudShade: Color,
    /** Nuvola carica di pioggia: e' un altro oggetto, non la stessa piu' scura. */
    val rainCloudCore: Color,
    val rainCloudShade: Color,
    val rain: Color,
)

// I due estremi del fondo. Nero pieno no: una luna bianca ci starebbe sopra
// come un buco, e la cifra bianca non avrebbe un lato in ombra credibile.
private val NightBackground = Color(0xFF1D2026)
private val DayBackground = Color(0xFFAEB3BB)

/** Il grigio caldo del crepuscolo, mescolato al fondo quando il sole e' radente. */
private val TwilightBackground = Color(0xFF6B5057)

private val NightText = Color(0xFFF4F5F7)
private val DayText = Color(0xFF181B20)

private val SunYellowCore = Color(0xFFFFDE59)
private val SunYellowShade = Color(0xFFE39A0C)
private val SunRedCore = Color(0xFFFF8A4C)
private val SunRedShade = Color(0xFFC9331D)

/**
 * Colori del momento.
 *
 * Non c'e' cache e non serve: e' una manciata di interpolazioni, e viene
 * ricalcolata solo quando cambia l'ora scelta.
 */
fun skyColors(sky: SkyState): MeteoColors {
    val day = sky.dayness
    val warmth = sky.redness * sky.sunPresence

    val background = lerp(
        lerp(NightBackground, DayBackground, day),
        TwilightBackground,
        warmth * 0.5f,
    )
    val text = lerp(NightText, DayText, day)
    val label = lerp(text, background, 0.42f)
    val line = lerp(text, background, 0.72f)

    return MeteoColors(
        background = background,
        text = text,
        label = label,
        line = line,
        numberFace = Color(0xFFFFFFFF),
        numberSideNear = Color(0xFFE9EAEE),
        // Costante e scura: e' il lato in ombra, e deve staccare dal fondo a
        // qualunque ora, altrimenti a mezzogiorno il volume si perde.
        numberSideFar = Color(0xFF43464C),
        numberChamfer = Color(0xFFDFE1E5),
        numberShadowAlpha = 0.12f * day,
        
        pillBackground = text,
        pillText = background,
        sunCore = lerp(SunYellowCore, SunRedCore, sky.redness),
        sunShade = lerp(SunYellowShade, SunRedShade, sky.redness),
        moonCore = Color(0xFFF6F7F9),
        moonShade = Color(0xFF9AA0AA),
        cloudCore = Color(0xFFFFFFFF),
        cloudShade = Color(0xFFBFC4CC),
        rainCloudCore = Color(0xFF9BA1AB),
        rainCloudShade = Color(0xFF474C56),
        rain = Color(0xFF3C8DF5),
    )
}

/**
 * Iridescenza: azzurro, rosa e giallo tenui, mai saturi. Le fasce trasparenti
 * interposte servono a spezzare il bordo, cosi' la rifrazione non gira uniforme
 * attorno a tutta la sagoma e resta entro il dieci-quindici per cento di
 * superficie.
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

val LocalMeteoColors = staticCompositionLocalOf { skyColors(SkyState.Giorno) }





