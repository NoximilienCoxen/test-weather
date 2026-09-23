package io.github.noximiliencoxen.caelum.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
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
 * Il pallore della luna, ripreso dal corpo che la scultura disegna gia'
 * (`MeteoColors.moonCore`): la pillola e il pallino della pagina devono avere
 * il colore della cosa che annunciano, non un azzurro scelto a parte.
 */
internal val MoonTint = Color(0xFFDDE3EE)

// **Qui stava `MeteoAccents`, e con lei se ne vanno `skyAccents` e
// `toAccents`.** Erano le tinte delle grandezze - sole, pioggia, vento, aria,
// luna, allerta - piu' quattro colori dei grafici, calcolate contro il fondo su
// cui sarebbero finite e messe a disposizione con un `CompositionLocal`. Le
// leggeva il feed. Da quando c'e' Sala, che le sue tinte se le prende da
// `SalaPalette`, `LocalMeteoAccents.current` non compare piu' in nessun file:
// si calcolavano a ogni fotogramma d'animazione - una decina di ricerche di
// contrasto, ognuna a passi di elevamento a potenza - per essere fornite a
// nessuno.

// ── Le venti tinte che non si muovono mai ───────────────────────────────────
//
// **`toColorScheme` ricalcolava tutto, e solo due voci dipendono dall'ora.**
// Il commento qui sotto lo dice gia' da sempre - *e' l'unica coppia dello
// schema che si muove durante il giorno* - ma le altre venti stavano dentro la
// funzione, quindi si rifacevano con lei: e la funzione gira **a ogni
// fotogramma in cui il cielo si muove**, perche' `MeteoTheme` la ricorda sulla
// tavolozza intera e la tavolozza intera cambia col fondo.
//
// Ognuna e' una ricerca di contrasto - [readableOn] e [mutedOn] scandiscono la
// luminanza a passi, e ogni passo e' un elevamento a potenza per canale - su
// colori che sono **costanti scritte in questo file**. Portarle qui fuori non
// cambia un numero: le espressioni sono le stesse, parola per parola, e i
// valori dipendono solo da token che non si muovono.
//
// L'ordine conta: `OnPanelVariant` legge `OnPanel`, e i tre `On...Container`
// leggono il proprio container. Una proprieta' di file che ne legge una
// dichiarata piu' in basso si prende il valore di prima dell'inizializzazione.

private val OnPanel = Color.White.readableOn(PanelContainer)

// Il grigio secondario si ricava contro la superficie **piu' chiara** su cui
// puo' finire, non contro quella media: le pillole spente stanno su
// `surfaceContainerHighest`, ed e' li' che il contrasto e' piu' magro.
// Tarandolo sul container si otteneva 4,49:1 su quelle pillole - meglio del
// 4,17:1 di prima, ma pur sempre sotto la soglia, cioe' lo stesso difetto
// spostato di un decimo. Contro la piu' chiara passa ovunque.
private val OnPanelVariant = OnPanel.mutedOn(PanelContainerHighest)

private val PrimaryOnPanel = SunTint.readableOn(PanelContainer, CONTRAST_AA_LARGE)
private val PrimaryContainer = lerp(PanelContainerHigh, SunTint, 0.14f)
private val OnPrimaryContainer = SunTint.readableOn(PrimaryContainer)

private val SecondaryOnPanel = RainTint.readableOn(PanelContainer, CONTRAST_AA_LARGE)
private val SecondaryContainer = lerp(PanelContainerHigh, RainTint, 0.14f)
private val OnSecondaryContainer = RainTint.readableOn(SecondaryContainer)

private val TertiaryOnPanel = AirTint.readableOn(PanelContainer, CONTRAST_AA_LARGE)
private val TertiaryContainer = lerp(PanelContainerHigh, AirTint, 0.14f)
private val OnTertiaryContainer = AirTint.readableOn(TertiaryContainer)

private val ErrorOnPanel = AlertTint.readableOn(PanelContainer, CONTRAST_AA_LARGE)
private val ErrorContainer = lerp(PanelContainerHigh, AlertTint, 0.16f)
private val OnErrorContainer = AlertTint.readableOn(ErrorContainer)

/** L'inverso serve alle pillole selezionate, che sono chiare su scuro. */
private val InverseSurface = Color(0xFFF1F2F5)
private val InverseOnSurface = Color.Black.readableOn(InverseSurface)
private val InversePrimary = SunTint.readableOn(InverseSurface)

/**
 * Lo schema Material dell'app.
 *
 * Si parte da [darkColorScheme] e non dal costruttore di `ColorScheme`: quello
 * ha una trentina di parametri posizionali che cambiano fra una versione e
 * l'altra della libreria, e un token aggiunto a monte diventerebbe qui uno
 * spostamento silenzioso di tutti quelli che seguono.
 *
 * Di calcolato resta **solo la coppia del fondo**: tutto il resto sono le
 * costanti qui sopra.
 */
fun MeteoColors.toColorScheme(): ColorScheme {
    val onBackground = text.readableOn(background)

    return darkColorScheme(
        primary = PrimaryOnPanel,
        onPrimary = SunTint.onColor(),
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,

        secondary = SecondaryOnPanel,
        onSecondary = RainTint.onColor(),
        secondaryContainer = SecondaryContainer,
        onSecondaryContainer = OnSecondaryContainer,

        tertiary = TertiaryOnPanel,
        onTertiary = AirTint.onColor(),
        tertiaryContainer = TertiaryContainer,
        onTertiaryContainer = OnTertiaryContainer,

        error = ErrorOnPanel,
        onError = AlertTint.onColor(),
        errorContainer = ErrorContainer,
        onErrorContainer = OnErrorContainer,

        // Il fondo segue l'ora; il testo che ci sta sopra viene calcolato, non
        // scelto. E' l'unica coppia dello schema che si muove durante il giorno.
        background = background,
        onBackground = onBackground,

        surface = PanelSurface,
        onSurface = OnPanel,
        surfaceVariant = PanelContainerHigh,
        onSurfaceVariant = OnPanelVariant,
        surfaceDim = PanelSurfaceDim,
        surfaceBright = PanelSurfaceBright,
        surfaceContainerLowest = PanelContainerLowest,
        surfaceContainerLow = PanelContainerLow,
        surfaceContainer = PanelContainer,
        surfaceContainerHigh = PanelContainerHigh,
        surfaceContainerHighest = PanelContainerHighest,

        // Il testo dentro le pillole selezionate esce da `inverseOnSurface`,
        // quindi non c'e' modo di scrivere bianco su bianco senza accorgersene.
        inverseSurface = InverseSurface,
        inverseOnSurface = InverseOnSurface,
        inversePrimary = InversePrimary,

        outline = PanelOutline,
        outlineVariant = PanelOutlineVariant,
        scrim = Color(0xCC000000),
    )
}


