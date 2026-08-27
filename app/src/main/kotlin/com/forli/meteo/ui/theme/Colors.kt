package com.forli.meteo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.forli.meteo.data.SkyState

/**
 * Un tema solo, che segue l'ora invece del sistema.
 *
 * Chiaro e scuro non erano una scelta sensata da offrire: sono la stessa
 * informazione che l'app gia' possiede, chiesta due volte.
 *
 * ## Perche' il fondo non e' piu' un grigio che schiarisce
 *
 * Lo era, e il difetto era misurabile. Il testo interpolava dal quasi bianco al
 * quasi nero mentre il fondo interpolava dal quasi nero al grigio chiaro: le due
 * scale si incrociavano, e nell'incrocio il contrasto scendeva a **1,09 contro
 * 1**, cioe' testo dello stesso identico tono del fondo. Non era un caso limite:
 * cadeva attorno a `dayness` 0,6, cioe' tutte le ore fra la meta' del pomeriggio
 * e il tramonto, ogni giorno.
 *
 * Rincorrere il difetto scegliendo il bianco o il nero secondo quale rende di
 * piu' non lo risolve: nel punto d'incrocio i due si equivalgono, e qualunque
 * regola faccia scattare l'inchiostro da un polo all'altro produce un salto di
 * colore mentre si scorre la barra delle ore. Interpolarli e' peggio ancora,
 * perche' la strada da bianco a nero passa **per** il colore del fondo.
 *
 * L'unica soluzione senza scatti e' togliere l'incrocio: il fondo non sale mai
 * fino al grigio chiaro, e l'inchiostro resta bianco a tutte le ore. Vivace non
 * vuol dire chiaro - un ambra saturo a luminanza 0,19 e' molto piu' vivo di un
 * grigio a 0,45 - e cosi' la cifra di plastica bianca ha finalmente qualcosa
 * contro cui staccare. Il peggior contrasto misurato sull'intero giro delle
 * ventiquattro ore e' **4,7 contro 1** in cima allo schermo e **16 contro 1** in
 * fondo, dove sta quasi tutto il testo.
 *
 * [readableOn] resta come rete: se un domani questi colori venissero ritoccati,
 * corregge da sola l'inchiostro invece di lasciar tornare il difetto.
 */
@Immutable
data class MeteoColors(
    /** Tinta di riferimento del fondo: le superfici piene la usano. */
    val background: Color,
    /** Cima del gradiente di fondo: e' qui che sta il colore vivo. */
    val skyTop: Color,
    /** Fondo del gradiente: sempre profondo, ed e' li' che vive il testo. */
    val skyBottom: Color,
    val text: Color,
    val label: Color,
    /** Testo in cima allo schermo, dove il cielo e' piu' chiaro. */
    val textOnSky: Color,
    val labelOnSky: Color,
    /**
     * Ombra dietro il testo sopra la scena.
     *
     * Il contrasto col fondo e' gia' garantito dai colori; questa serve dove il
     * testo scavalca la scultura o un banco di nebbia, cioe' dove il fondo non
     * e' piu' il fondo.
     */
    val textShadow: Color,
    val line: Color,
    /** Faccia frontale della cifra: satinata, piatta, senza gradiente colorato. */
    val numberFace: Color,
    /** Parete piu' vicina alla faccia frontale. */
    val numberSideNear: Color,
    /** Parete piu' lontana, in fondo allo spessore. */
    val numberSideFar: Color,
    /** Smusso rivolto verso la luce. */
    val numberChamfer: Color,
    /** Quanto stacca l'ombra portata: serve solo se il cielo dietro e' chiaro. */
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
    /** Strato secondario, piu' lontano e piu' smorzato: da' profondita' al cielo. */
    val cloudDistant: Color,
    /** Nuvola carica di pioggia: e' un altro oggetto, non la stessa piu' scura. */
    val rainCloudCore: Color,
    val rainCloudShade: Color,
    val rain: Color,
    val snow: Color,
    /** Nebbia: il banco vicino e quello che si perde in fondo. */
    val fogNear: Color,
    val fogFar: Color,
    val star: Color,
    val bird: Color,
)

// ---------------------------------------------------------------------------
// Gli estremi del cielo. Nessuno arriva al grigio chiaro: vedi la nota sopra.
// ---------------------------------------------------------------------------

private val NightTop = Color(0xFF262B45)
private val NightBottom = Color(0xFF0E1015)

private val DayTop = Color(0xFF3F76B4)
private val DayBottom = Color(0xFF1B2129)

/** L'ora dorata: ambra pieno e opaco, non un velo arancione sopra il grigio. */
private val GoldenTop = Color(0xFFB5622C)
private val GoldenBottom = Color(0xFF2A1C1E)

/**
 * La nebbia si porta via il colore prima ancora della visibilita': il cielo
 * diventa una lastra grigia senza direzione, senza cima e senza fondo.
 */
private val FogTop = Color(0xFF6E767E)
private val FogBottom = Color(0xFF2A2E33)

private val Ink = Color(0xFFF4F5F7)

/**
 * La luce dell'ora dorata, come la vede un oggetto bianco.
 *
 * Non e' un velo arancione steso sopra la scena: e' il colore che **prende** la
 * plastica bianca quando l'unica luce in giro e' radente. Per questo entra nella
 * tavolozza del solido invece che in un rettangolo davanti - una velatura
 * uniforme tingerebbe allo stesso modo la faccia illuminata e quella in ombra,
 * cioe' cancellerebbe proprio il volume che l'ora dorata dovrebbe esaltare.
 */
private val GoldenLight = Color(0xFFFFDCB4)
private val GoldenShade = Color(0xFF5A3F35)

private val SunYellowCore = Color(0xFFFFDE59)
private val SunYellowShade = Color(0xFFE39A0C)
private val SunRedCore = Color(0xFFFF8A4C)
private val SunRedShade = Color(0xFFC9331D)

/**
 * Colori del momento.
 *
 * Non c'e' cache e non serve: e' una manciata di interpolazioni, e viene
 * ricalcolata solo quando cambia l'ora scelta.
 *
 * @param fog quanto e' fitta la nebbia, da 0 a 1. Scolora il cielo e ne
 *   appiattisce il gradiente, che e' cio' che la nebbia fa davvero.
 */
fun skyColors(sky: SkyState, fog: Float = 0f): MeteoColors {
    val day = sky.dayness
    val golden = sky.golden * (0.35f + 0.65f * sky.sunPresence)
    val haze = fog.coerceIn(0f, 1f)

    val top = lerp(
        lerp(lerp(NightTop, DayTop, day), GoldenTop, golden),
        FogTop,
        haze * 0.85f,
    )
    val bottom = lerp(
        lerp(lerp(NightBottom, DayBottom, day), GoldenBottom, golden),
        FogBottom,
        haze * 0.85f,
    )

    // Il riferimento pieno sta piu' vicino al fondo che alla cima: le superfici
    // che lo usano - il foglio del dettaglio, le impostazioni - sono pagine di
    // testo, e una pagina di testo vuole il tono su cui il testo si legge.
    val base = lerp(bottom, top, 0.22f)

    val text = readableOn(bottom, Ink, BODY_CONTRAST)
    val onSky = readableOn(top, Ink, BODY_CONTRAST)

    return MeteoColors(
        background = base,
        skyTop = top,
        skyBottom = bottom,
        text = text,
        // Smorzata verso il fondo, ma non oltre: a 0,45 il rapporto peggiore
        // misurato sul giro delle ore resta 5,8 contro 1.
        label = readableOn(bottom, lerp(text, bottom, 0.45f), LABEL_CONTRAST),
        textOnSky = onSky,
        labelOnSky = readableOn(top, lerp(onSky, top, 0.35f), LABEL_CONTRAST),
        textShadow = Color(0xFF000000).copy(alpha = 0.55f),
        line = lerp(text, base, 0.70f),
        // Le facce esposte prendono la luce calda, quella in ombra prende il
        // riflesso del cielo: e' lo scarto fra le due a raccontare che la luce
        // viene di lato, che e' tutto quello che l'alba e il tramonto sono.
        numberFace = lerp(Color(0xFFFFFFFF), GoldenLight, golden * 0.62f),
        numberSideNear = lerp(Color(0xFFE9EAEE), GoldenLight, golden * 0.48f),
        // Il lato in ombra deve staccare dal fondo a qualunque ora, altrimenti
        // a mezzogiorno il volume si perde. Sull'ora dorata non schiarisce: si
        // sposta di tinta, verso il bruno del controluce.
        numberSideFar = lerp(Color(0xFF43464C), GoldenShade, golden * 0.55f),
        numberChamfer = lerp(Color(0xFFDFE1E5), GoldenLight, golden * 0.70f),
        // L'ombra portata serve solo se dietro la cifra il cielo e' chiaro. Su
        // un fondo profondo una macchia nera non stacca niente, e costa: e' la
        // superficie piu' grande che il disegno tocchi.
        numberShadowAlpha = (luminance(bottom) * 1.9f).coerceIn(0f, 0.20f),
        pillBackground = text,
        pillText = bottom,
        sunCore = lerp(SunYellowCore, SunRedCore, sky.redness),
        sunShade = lerp(SunYellowShade, SunRedShade, sky.redness),
        moonCore = Color(0xFFF6F7F9),
        moonShade = Color(0xFF9AA0AA),
        // Le nuvole al tramonto sono la cosa piu' arancione del cielo, e sono
        // anche quelle che il tramonto lo rendono riconoscibile: un cielo caldo
        // con sopra nuvole bianche non si legge come tramonto, si legge come un
        // errore di fotografia.
        cloudCore = lerp(Color(0xFFFFFFFF), Color(0xFFFFD2A6), golden * 0.85f),
        cloudShade = lerp(Color(0xFFBFC4CC), Color(0xFF9C6A55), golden * 0.70f),
        // Lo strato lontano non e' la stessa nuvola piu' piccola: e' piu' vicino
        // al colore del cielo, perche' l'aria che ci sta in mezzo se lo mangia.
        cloudDistant = lerp(Color(0xFFD6DBE2), top, 0.45f),
        rainCloudCore = Color(0xFF9BA1AB),
        rainCloudShade = Color(0xFF474C56),
        rain = Color(0xFF6FB2FF),
        snow = Color(0xFFF2F6FB),
        fogNear = Color(0xFFE4E8ED),
        fogFar = lerp(Color(0xFFB9C0C8), top, 0.35f),
        star = Color(0xFFEFF3FF),
        bird = lerp(Color(0xFF1A1D24), top, 0.18f),
    )
}

// ---------------------------------------------------------------------------
// Contrasto
// ---------------------------------------------------------------------------

/** Rapporto minimo per il testo che porta informazione. E' il livello AA. */
private const val BODY_CONTRAST = 4.5f

/** Le etichette sono corte, maiuscole e spaziate: reggono il livello grande. */
private const val LABEL_CONTRAST = 3f

/**
 * Lo stesso inchiostro, spinto quanto basta a staccare dal fondo.
 *
 * Se [preferred] gia' regge, non lo tocca: la scelta estetica vale finche' e'
 * anche leggibile. Altrimenti lo porta verso il bianco o verso il nero - quello
 * che su questo fondo rende di piu' - cercando per bisezione il minimo
 * spostamento che raggiunge [target]. Il minimo, non il massimo: portarlo al
 * polo e basta butterebbe via la tinta per un problema che si risolveva con un
 * quarto di strada.
 */
private fun readableOn(background: Color, preferred: Color, target: Float): Color {
    if (contrastRatio(preferred, background) >= target) return preferred
    val pole = if (contrastRatio(Color.White, background) >=
        contrastRatio(Color.Black, background)
    ) {
        Color.White
    } else {
        Color.Black
    }
    var low = 0f
    var high = 1f
    repeat(BISECTIONS) {
        val mid = (low + high) / 2f
        if (contrastRatio(lerp(preferred, pole, mid), background) >= target) {
            high = mid
        } else {
            low = mid
        }
    }
    return lerp(preferred, pole, high)
}

private const val BISECTIONS = 9

/** Rapporto di contrasto WCAG fra due colori opachi. */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = luminance(a)
    val lb = luminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Luminanza relativa secondo WCAG.
 *
 * Non e' la media dei canali e nemmeno il valore del modello HSV: il verde pesa
 * sette volte il blu, e un blu scuro e un verde dello stesso "valore" non si
 * leggono affatto allo stesso modo.
 */
private fun luminance(color: Color): Float =
    0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)

private fun channel(value: Float): Float =
    if (value <= 0.04045f) value / 12.92f else Math.pow(
        ((value + 0.055f) / 1.055f).toDouble(),
        2.4,
    ).toFloat()

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
