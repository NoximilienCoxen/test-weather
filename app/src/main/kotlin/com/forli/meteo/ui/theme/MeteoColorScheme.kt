package com.forli.meteo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Lo schema Material 3 dell'app, costruito dalla palette del cielo.
 *
 * Due mondi convivono in questa app e lo schema deve tenerli insieme senza
 * mentire su nessuno dei due:
 *
 * - **La schermata principale segue l'ora.** Il fondo va dal grigio chiaro di
 *   mezzogiorno all'antracite di mezzanotte, ed e' il punto dell'app: la barra
 *   delle ore racconta anche il passare della luce. Quindi `background` e'
 *   quello di [MeteoColors], e `onBackground` viene **calcolato** su di esso.
 * - **I pannelli restano stabili.** Un foglio di numeri che si schiarisce e si
 *   scurisce mentre lo si legge non e' un tema, e' un lampeggio; e la scala di
 *   colore dei gradi, che dice quanto caldo fa, ha bisogno di un fondo neutro
 *   sotto per non cambiare significato ogni sei ore. Quindi le `surface` sono
 *   neutre scure e ferme.
 *
 * Cio' che le tiene insieme e' che **nessun colore di testo e' scritto a mano**:
 * ogni `onQualcosa` esce da [readableOn] sul proprio fondo, con la soglia WCAG
 * AA. E' questa la differenza con la versione precedente, dove il titolo del
 * dettaglio era il testo del tema (quasi nero a mezzogiorno) sopra un pannello
 * antracite fisso, e semplicemente spariva.
 */

// ── Le superfici dei pannelli ────────────────────────────────────────────────
// Ferme a qualunque ora. Non nero pieno: le schede scure con bordo sottile
// hanno bisogno di un gradino sotto di loro per staccare, e sul nero pieno una
// luna bianca si legge come un buco.
private val PanelSurface = Color(0xFF16181D)
private val PanelSurfaceDim = Color(0xFF101216)
private val PanelSurfaceBright = Color(0xFF2C2F36)
private val PanelContainerLowest = Color(0xFF0D0F12)
private val PanelContainerLow = Color(0xFF1A1D22)
private val PanelContainer = Color(0xFF1D2026)
private val PanelContainerHigh = Color(0xFF23262D)
private val PanelContainerHighest = Color(0xFF2A2E36)
private val PanelOutline = Color(0xFF565A63)
private val PanelOutlineVariant = Color(0xFF383C44)

/** Le tinte delle grandezze. Sono quelle di sempre, promosse a token. */
internal val SunTint = Color(0xFFFFDE59)
internal val RainTint = Color(0xFF3C8DF5)
internal val WindTint = Color(0xFF7EB8F7)
internal val AirTint = Color(0xFF6FD09A)
internal val AlertTint = Color(0xFFFF8A6B)

/**
 * I colori che l'app usa **oltre** a quelli che Material 3 nomina.
 *
 * Material non ha un token per "il colore della pioggia" ne' per "la linea di
 * riferimento di un grafico", e inventarne uno storcendo `tertiary` renderebbe
 * illeggibile il codice che lo legge. Stanno qui, gia' resi leggibili sul
 * proprio fondo, e si prendono da [LocalMeteoAccents].
 */
@Immutable
data class MeteoAccents(
    val sun: Color,
    val rain: Color,
    val wind: Color,
    val air: Color,
    val alert: Color,
    /** La curva di riferimento dietro quella colorata: l'effettiva sotto la percepita. */
    val ghost: Color,
    /** La media storica mensile: si distingue dalla griglia senza rubare la scena. */
    val norm: Color,
    /** Le tacche della griglia dei grafici. */
    val grid: Color,
    /** Il fondo su cui poggiano le etichette disegnate a mano dentro le tele. */
    val chartLabelBackground: Color,
)

/**
 * Lo schema Material dell'app.
 *
 * Si parte da [darkColorScheme] e non dal costruttore di `ColorScheme`: quello
 * ha una trentina di parametri posizionali che cambiano fra una versione e
 * l'altra della libreria, e un token aggiunto a monte diventerebbe qui uno
 * spostamento silenzioso di tutti quelli che seguono.
 */
fun MeteoColors.toColorScheme(): ColorScheme {
    val onPanel = Color.White.readableOn(PanelContainer)
    // Il grigio secondario si ricava contro la superficie **piu' chiara** su cui
    // puo' finire, non contro quella media: le pillole spente stanno su
    // `surfaceContainerHighest`, ed e' li' che il contrasto e' piu' magro.
    // Tarandolo sul container si otteneva 4,49:1 su quelle pillole - meglio del
    // 4,17:1 di prima, ma pur sempre sotto la soglia, cioe' lo stesso difetto
    // spostato di un decimo. Contro la piu' chiara passa ovunque.
    val onPanelVariant = onPanel.mutedOn(PanelContainerHighest)
    val onBackground = text.readableOn(background)

    return darkColorScheme(
        primary = SunTint.readableOn(PanelContainer, CONTRAST_AA_LARGE),
        onPrimary = SunTint.onColor(),
        primaryContainer = lerp(PanelContainerHigh, SunTint, 0.14f),
        onPrimaryContainer = SunTint.readableOn(lerp(PanelContainerHigh, SunTint, 0.14f)),

        secondary = RainTint.readableOn(PanelContainer, CONTRAST_AA_LARGE),
        onSecondary = RainTint.onColor(),
        secondaryContainer = lerp(PanelContainerHigh, RainTint, 0.14f),
        onSecondaryContainer = RainTint.readableOn(lerp(PanelContainerHigh, RainTint, 0.14f)),

        tertiary = AirTint.readableOn(PanelContainer, CONTRAST_AA_LARGE),
        onTertiary = AirTint.onColor(),
        tertiaryContainer = lerp(PanelContainerHigh, AirTint, 0.14f),
        onTertiaryContainer = AirTint.readableOn(lerp(PanelContainerHigh, AirTint, 0.14f)),

        error = AlertTint.readableOn(PanelContainer, CONTRAST_AA_LARGE),
        onError = AlertTint.onColor(),
        errorContainer = lerp(PanelContainerHigh, AlertTint, 0.16f),
        onErrorContainer = AlertTint.readableOn(lerp(PanelContainerHigh, AlertTint, 0.16f)),

        // Il fondo segue l'ora; il testo che ci sta sopra viene calcolato, non
        // scelto. E' l'unica coppia dello schema che si muove durante il giorno.
        background = background,
        onBackground = onBackground,

        surface = PanelSurface,
        onSurface = onPanel,
        surfaceVariant = PanelContainerHigh,
        onSurfaceVariant = onPanelVariant,
        surfaceDim = PanelSurfaceDim,
        surfaceBright = PanelSurfaceBright,
        surfaceContainerLowest = PanelContainerLowest,
        surfaceContainerLow = PanelContainerLow,
        surfaceContainer = PanelContainer,
        surfaceContainerHigh = PanelContainerHigh,
        surfaceContainerHighest = PanelContainerHighest,

        // L'inverso serve alle pillole selezionate, che sono chiare su scuro:
        // il testo dentro esce da `inverseOnSurface`, quindi non c'e' modo di
        // scrivere bianco su bianco senza accorgersene.
        inverseSurface = Color(0xFFF1F2F5),
        inverseOnSurface = Color.Black.readableOn(Color(0xFFF1F2F5)),
        inversePrimary = SunTint.readableOn(Color(0xFFF1F2F5)),

        outline = PanelOutline,
        outlineVariant = PanelOutlineVariant,
        scrim = Color(0xCC000000),
    )
}

/** Le tinte delle grandezze, gia' rese leggibili sulla superficie che le ospita. */
fun MeteoColors.toAccents(): MeteoAccents {
    val on = PanelContainer
    return MeteoAccents(
        // Soglia da testo grande: queste tinte colorano curve spesse e cifre
        // alte mezzo schermo, non didascalie. Portarle a 4.5:1 le sbiadirebbe
        // tutte verso lo stesso bianco sporco, e a quel punto non direbbero
        // piu' quale grandezza si sta guardando.
        sun = SunTint.readableOn(on, CONTRAST_AA_LARGE),
        rain = RainTint.readableOn(on, CONTRAST_AA_LARGE),
        wind = WindTint.readableOn(on, CONTRAST_AA_LARGE),
        air = AirTint.readableOn(on, CONTRAST_AA_LARGE),
        alert = AlertTint.readableOn(on, CONTRAST_AA_LARGE),
        ghost = Color(0xFFB4B4BE).readableOn(on, CONTRAST_AA_LARGE),
        norm = Color(0xFFCFCFD8).readableOn(on, CONTRAST_AA_LARGE),
        grid = PanelOutlineVariant,
        chartLabelBackground = PanelContainerLowest,
    )
}
