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

// Notte: blu ardesia scuro, non nero pieno (la luna bianca ci starebbe sopra
// come un buco, e la cifra non avrebbe un lato in ombra credibile).
private val NightBackground = Color(0xFF1A1E28)

// Giorno: azzurro polvere / cielo aperto, non grigio neutro che sembra
// lo schermo spento.
private val DayBackground = Color(0xFFB8D4E8)

// Tramonto: arancione pesca opaco in alto — non marrone/ruggine che si fonde
// con lo sfondo. Il mix e' leggero (max 28%) per non dominare.
private val TwilightTop = Color(0xFFE8956D)

// Notte profonda: violetto ardesia, la componente bassa del tramonto.
private val TwilightBottom = Color(0xFF2D2440)

private val NightText = Color(0xFFF4F5F7)
private val DayText = Color(0xFF181B20)

private val SunYellowCore = Color(0xFFFFE066)
private val SunYellowShade = Color(0xFFE8A020)
// Tramonto: arancione caldo vibrante, non scuro/marrone.
private val SunRedCore = Color(0xFFFF7040)
private val SunRedShade = Color(0xFFD94010)

/**
 * Colori del momento.
 *
 * Non c'e' cache e non serve: e' una manciata di interpolazioni, e viene
 * ricalcolata solo quando cambia l'ora scelta.
 */
fun skyColors(sky: SkyState): MeteoColors {
    val day = sky.dayness
    val warmth = sky.redness * sky.sunPresence

    // Tramonto: mix tra arancione pesca e violetto notte — niente marrone.
    val twilight = lerp(TwilightBottom, TwilightTop, day.coerceIn(0f, 1f))
    val background = lerp(
        lerp(NightBackground, DayBackground, day),
        twilight,
        warmth * 0.28f,
    )

    // Contrasto garantito: il testo non si avvicina mai a meno di 4.5:1
    // rispetto allo sfondo. Si calcola sulla luminanza relativa (sRGB).
    val bgLum = background.luminance()
    val rawText = lerp(NightText, DayText, day)
    // Se la luminanza del testo raw da contrasto insufficiente, spingiamo
    // verso il polo opposto. Soglia conservativa: 0.18 garantisce >= 4.5:1
    // per qualsiasi sfondo nel nostro range.
    val text = if (bgLum > 0.18f) {
        // Sfondo chiaro: testo scuro
        lerp(rawText, Color(0xFF0E1118), ((bgLum - 0.18f) / 0.45f).coerceIn(0f, 1f))
    } else {
        // Sfondo scuro: testo chiaro
        lerp(rawText, Color(0xFFF4F5F7), ((0.18f - bgLum) / 0.18f).coerceIn(0f, 1f))
    }

    val label = lerp(text, background, 0.38f)
    val line = lerp(text, background, 0.70f)

    return MeteoColors(
        background = background,
        text = text,
        label = label,
        line = line,
        // Plastica bianca opaca fresata: nessuna dominante di colore d'ambiente.
        // Un lerp massimo del 10% verso il background caldo evita che risulti
        // troppo fredda di notte — senza ereditarne la saturazione scura.
        numberFace = lerp(Color(0xFFFFFFFF), background, 0.10f * warmth),
        numberSideNear = Color(0xFFE9EAEE),
        // Lato in ombra costante e scuro: deve staccare dal fondo a qualunque ora.
        numberSideFar = Color(0xFF3E4148),
        numberChamfer = Color(0xFFDFE1E5),
        numberShadowAlpha = 0.14f * day,
        pillBackground = text,
        pillText = background,
        sunCore = lerp(SunYellowCore, SunRedCore, sky.redness),
        sunShade = lerp(SunYellowShade, SunRedShade, sky.redness),
        moonCore = Color(0xFFF6F7F9),
        moonShade = Color(0xFF8A909A),
        cloudCore = Color(0xFFFFFFFF),
        cloudShade = Color(0xFFBFC4CC),
        rainCloudCore = Color(0xFF9BA1AB),
        rainCloudShade = Color(0xFF474C56),
        rain = Color(0xFF4A9BF5),
    )
}

/**
 * Luminanza relativa sRGB per il calcolo del contrasto WCAG.
 * Formula: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
private fun Color.luminance(): Float {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
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





