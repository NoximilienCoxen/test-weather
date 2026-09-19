package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring

/**
 * Le molle di Sala, in un posto solo.
 *
 * Tarate qui e non nei punti d'uso per la stessa ragione per cui il giro sta in
 * `SalaGiro.kt`: due copie della stessa molla divergono al primo che ne tara
 * una, e sette sale che si assestano a velocita' leggermente diverse si
 * sentono come sette schermate invece che come una galleria.
 *
 * **Ogni molla qui dentro dichiara la propria soglia di visibilita'**, e non e'
 * un vezzo. Senza, una molla resta formalmente viva a limare millesimi, e ogni
 * fotogramma speso li' e' un ridisegno a schermo fermo: e' la trappola #8
 * rientrata dalla porta di servizio, un millesimo per volta.
 */

/**
 * Due modi per cui un passaggio non deve avvenire.
 *
 * @param istantanee lo chiede l'aggancio di cattura: gli scatti della CI devono
 *   essere riproducibili byte per byte, e una molla in volo al momento dello
 *   scatto li rende dipendenti da un `sleep`. Vero solo sotto
 *   `BuildConfig.AGGANCI_CATTURA`.
 * @param ridotte lo chiede chi guarda, dalle impostazioni.
 */
internal fun <T> ferma(istantanee: Boolean, ridotte: Boolean, molla: AnimationSpec<T>): AnimationSpec<T> =
    if (istantanee || ridotte) snap() else molla

/**
 * La carta che scurisce.
 *
 * **Criticamente smorzata**, e questo e' vincolante: un rimbalzo oltre 1
 * porterebbe la carta piu' scura di `paperDark`, che non e' un colore che
 * esiste. A rigidita' 60 lo scalino di `paperDarkness` e' coperto in circa sei
 * decimi di secondo, e la fascia in cui nessun inchiostro regge sta a schermo
 * un decimo e mezzo.
 */
fun mollaCarta(istantanee: Boolean, ridotte: Boolean = false): AnimationSpec<Float> = ferma(
    istantanee, ridotte,
    spring(dampingRatio = 1f, stiffness = 60f, visibilityThreshold = 0.002f),
)

// **`mollaColore` e `mollaLettura` se ne sono andate.** La prima smorzava le
// macchie dell'acquerello, la seconda il numero dei gradi mentre si scorrono le
// ore: due andature tarate per due cose che oggi si animano altrove - le tinte
// dentro `SalaPalette`, il numero senza molla perche' cambia di scatto con
// l'ora. Restano le due vive, la carta e la scena.

/**
 * I sette numeri della scena: sole, copertura, tempesta, bagnato, ghiaccio,
 * neve, notte.
 *
 * **Criticamente smorzata anche questa, e per un motivo concreto**: una
 * copertura che sfora oltre 1 gonfia le masse oltre la loro taglia, e una che
 * scende sotto 0 da' raggi negativi.
 */
fun mollaScena(istantanee: Boolean, ridotte: Boolean = false): AnimationSpec<Float> = ferma(
    istantanee, ridotte,
    spring(dampingRatio = 1f, stiffness = 90f, visibilityThreshold = 0.002f),
)
