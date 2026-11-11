package com.caelumweather.data

import com.caelumweather.lingua.tr
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Oggi rispetto a ieri, sulle massime.
 *
 * **La differenza si fa sui numeri mostrati, non sui decimali.** Se ieri si
 * leggeva 17 e oggi si legge 20, lo scarto e' 3 anche quando i decimali
 * direbbero 2,6: chi ricorda il numero di ieri fa il conto su quello. Per
 * questo passa da [mostrati], che porta i gradi nell'unita' scelta.
 *
 * Le massime e non le medie: "piu' caldo di ieri" e' una frase sul pomeriggio,
 * e la minima della notte appena passata chi legge l'ha gia' vissuta.
 */
fun scartoDaIeri(oggi: DayForecast?, ieri: DayForecast?, mostrati: (Double) -> Double): Int? {
    val adesso = oggi?.tempMax ?: return null
    val prima = ieri?.tempMax ?: return null
    return mostrati(adesso).roundToInt() - mostrati(prima).roundToInt()
}

/** In poche lettere, per stare sotto la minima e la massima di Sala I. */
fun scartoDaIeriBreve(scarto: Int): String = when {
    scarto == 0 -> tr("come ieri", "same as yesterday")
    scarto > 0 -> tr("+$scarto° su ieri", "+$scarto° on yesterday")
    // Il meno vero, non il trattino: accanto a un piu' il trattino sembra un refuso.
    else -> tr("\u2212${abs(scarto)}° su ieri", "\u2212${abs(scarto)}° on yesterday")
}

/**
 * Per esteso, per il riepilogo del mattino. **Sotto i due gradi tace**: il
 * riepilogo dice solo cio' che cambia cosa fai, e un grado non cambia niente.
 */
fun scartoDaIeriEsteso(scarto: Int): String? = when {
    scarto >= 2 -> tr("$scarto° più caldo di ieri.", "$scarto° warmer than yesterday.")
    scarto <= -2 -> tr("${abs(scarto)}° più freddo di ieri.", "${abs(scarto)}° colder than yesterday.")
    else -> null
}
