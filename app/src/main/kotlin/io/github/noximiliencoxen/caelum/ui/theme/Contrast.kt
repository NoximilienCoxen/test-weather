package io.github.noximiliencoxen.caelum.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Il contrasto, calcolato invece che sperato.
 *
 * L'app disegna testo su fondi che non conosce in anticipo: il cielo cambia con
 * l'ora, le aree dei grafici sono coperte da un gradiente di temperatura, le
 * pillole prendono il colore della grandezza che rappresentano. Fissare i colori
 * a mano vuol dire indovinare, e si e' gia' visto come va a finire - il titolo
 * del dettaglio era `colors.text`, cioe' quasi nero, su un pannello antracite
 * fisso: a mezzogiorno spariva.
 *
 * Qui il colore del testo non si sceglie, si **ricava** dal fondo su cui cadra',
 * secondo la formula di contrasto della WCAG 2.1. Chi disegna dichiara dove
 * scrive, e la leggibilita' viene garantita per costruzione.
 */

/** Soglia WCAG AA per il testo normale. */
const val CONTRAST_AA = 4.5f

/** Soglia WCAG AA per il testo grande (>= 18pt, o 14pt in grassetto). */
const val CONTRAST_AA_LARGE = 3f

/**
 * Luminanza relativa secondo la WCAG.
 *
 * Non e' la media dei canali e non e' la componente L di HSL: i tre canali
 * pesano in modo diverso perche' l'occhio li pesa in modo diverso, e ognuno
 * passa prima per la de-gamma sRGB. Un verde pieno e un blu pieno hanno la
 * stessa "luminosita'" in HSL e luminanze che differiscono di dieci volte.
 */
fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/**
 * Il rapporto di contrasto fra due colori, da 1 (identici) a 21 (nero e bianco).
 *
 * I colori con trasparenza non hanno un contrasto proprio: quello che si vede
 * dipende da cosa c'e' sotto. Chi ne passa uno lo compone prima sul fondo.
 */
fun Color.contrastRatio(other: Color): Float {
    val a = relativeLuminance()
    val b = other.relativeLuminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/** Bianco o nero: quello dei due che si legge meglio su questo fondo. */
fun Color.onColor(): Color =
    if (contrastRatio(Color.White) >= contrastRatio(Color.Black)) Color.White else Color.Black

/**
 * Lo stesso colore, spinto quanto basta perche' si legga sopra [background].
 *
 * Si muove verso il polo opposto al fondo - schiarisce su fondo scuro, scurisce
 * su fondo chiaro - a passi piccoli, e si ferma appena raggiunta la soglia. La
 * tinta resta riconoscibile finche' e' possibile: l'azzurro della pioggia
 * diventa un azzurro piu' chiaro, non diventa bianco. Solo quando nemmeno il
 * polo basta ci si arrende al bianco o al nero, che e' sempre la risposta
 * giusta di ultima istanza.
 *
 * **Non e' una funzione da chiamare a ogni fotogramma**: costa una manciata di
 * elevamenti a potenza per passo. Va dentro un `remember` con il fondo per
 * chiave, o calcolata una volta sola alla costruzione della palette.
 */
fun Color.readableOn(background: Color, minRatio: Float = CONTRAST_AA): Color {
    val solid = if (alpha < 1f) copy(alpha = 1f).compositeOver(background) else this
    if (solid.contrastRatio(background) >= minRatio) return solid

    val pole = background.onColor()
    // Sedici passi: oltre, la differenza fra un passo e il successivo e' sotto
    // la soglia di un canale a otto bit e si girerebbe a vuoto.
    for (step in 1..16) {
        val candidate = lerp(solid, pole, step / 16f)
        if (candidate.contrastRatio(background) >= minRatio) return candidate
    }
    return pole
}

/**
 * Il testo secondario: leggibile, ma un gradino sotto il primario.
 *
 * Serve alle didascalie e alle etichette degli assi, che devono farsi da parte
 * senza sparire. Parte dal primario e lo avvicina al fondo finche' puo', ma non
 * scende mai sotto la soglia: e' esattamente il passaggio in cui, mescolando a
 * occhio, sono nati i grigi al tre e mezzo per uno che oggi si leggono male.
 */
fun Color.mutedOn(background: Color, minRatio: Float = CONTRAST_AA): Color {
    var best = this
    for (step in 1..8) {
        val candidate = lerp(this, background, step * 0.06f)
        if (candidate.contrastRatio(background) < minRatio) break
        best = candidate
    }
    return best
}

/**
 * Leggibile su **tutti e due** i capi di una sfumatura.
 *
 * Da quando il fondo della schermata principale e' un cielo sfumato, sotto un
 * testo solo ci sono due colori diversi: in cima il nome della localita' sta
 * sullo zenit, in fondo l'ora e la barra stanno sull'orizzonte. Garantire la
 * soglia sul solo tono medio vuol dire perderla a uno dei due capi - ed e'
 * esattamente il modo in cui era gia' sparito una volta il titolo del dettaglio,
 * che era corretto sul fondo sbagliato.
 *
 * Si provano i poli dei due capi e si tiene il primo passo che regge entrambi.
 * Se nessuno dei due basta - un cielo che andasse dal quasi nero al quasi bianco
 * non ha un colore di testo che lo attraversi - si tiene il candidato che regge
 * meglio il capo peggiore, che e' il male minore invece del bianco d'ufficio.
 *
 * Vuole i due capi **opachi**: con la trasparenza non c'e' un fondo solo su cui
 * comporre, e comporre sul capo sbagliato darebbe una misura falsa.
 */
fun Color.readableOnBoth(first: Color, second: Color, minRatio: Float = CONTRAST_AA): Color {
    fun worstOf(color: Color) = min(color.contrastRatio(first), color.contrastRatio(second))

    if (worstOf(this) >= minRatio) return this
    var best = this
    var bestWorst = worstOf(this)
    for (pole in listOf(first.onColor(), second.onColor())) {
        for (step in 1..16) {
            val candidate = lerp(this, pole, step / 16f)
            val worst = worstOf(candidate)
            if (worst >= minRatio) return candidate
            if (worst > bestWorst) {
                bestWorst = worst
                best = candidate
            }
        }
    }
    return best
}

/**
 * Il testo secondario su una sfumatura: si fa da parte verso il tono medio dei
 * due capi e si ferma appena uno dei due scende sotto la soglia.
 *
 * Il capo peggiore comanda, come deve essere: un'etichetta che si legge in cima
 * e sbianca in fondo e' un'etichetta illeggibile, non un'etichetta a meta'.
 */
fun Color.mutedOnBoth(first: Color, second: Color, minRatio: Float = CONTRAST_AA): Color {
    val middle = lerp(first, second, 0.5f)
    var best = this
    for (step in 1..8) {
        val candidate = lerp(this, middle, step * 0.06f)
        if (candidate.contrastRatio(first) < minRatio) break
        if (candidate.contrastRatio(second) < minRatio) break
        best = candidate
    }
    return best
}

/**
 * Vero quando due colori sono cosi' vicini da non distinguersi.
 *
 * Serve a decidere se un bordo serve davvero: fra due superfici che gia'
 * staccano, una riga in piu' e' rumore.
 */
fun Color.isNear(other: Color, tolerance: Float = 0.04f): Boolean =
    abs(red - other.red) < tolerance &&
        abs(green - other.green) < tolerance &&
        abs(blue - other.blue) < tolerance
