package io.github.noximiliencoxen.caelum.ui.scene

import java.time.LocalDateTime
import java.time.Month
import kotlin.random.Random

/**
 * Le scene che aprono l'app: una a caso, di giorno o di notte secondo l'ora.
 *
 * Ognuna e' un quadretto disegnato a codice, a tinte piatte con la tavolozza
 * dell'app, e si muove: le onde, la pioggia, la neve, il mulino. Stanno nel
 * benvenuto al primo avvio e, per un attimo, a ogni apertura mentre arrivano
 * i dati - li' si vedono davvero, perche' il benvenuto si vede una volta sola.
 */
enum class Scena(val nome: String) {
    MARE("Sole e mare"),
    CITTA_NATALE("Città innevata"),
    TEMPESTA("Nave in tempesta"),
    FERMATA("Fermata sotto la pioggia"),
    GRANO("Campo di grano"),
    ARCOBALENO("Dopo la pioggia"),
    MONTAGNA("Montagna"),
    NEBBIA("Colline nella nebbia"),
}

/**
 * Quale scena, adesso. A caso fra quelle di stagione: la citta' natalizia solo
 * dal primo dicembre all'Epifania, perche' a luglio sarebbe uno scherzo.
 */
fun scegliScena(momento: LocalDateTime, caso: Random = Random.Default): Scena {
    val possibili = Scena.entries.filter { it != Scena.CITTA_NATALE || natalizio(momento) }
    return possibili[caso.nextInt(possibili.size)]
}

/** Dal primo dicembre al sei gennaio compresi. */
fun natalizio(momento: LocalDateTime): Boolean =
    momento.month == Month.DECEMBER || (momento.month == Month.JANUARY && momento.dayOfMonth <= 6)

/**
 * Se e' notte, per una scena d'apertura.
 *
 * All'avvio l'app non sa ancora dove sei, quindi niente alba e tramonto veri:
 * bastano quelli medi dell'Italia per stagione. Sbagliare di mezz'ora al
 * crepuscolo, per un quadretto, non si vede.
 */
fun notteA(momento: LocalDateTime): Boolean {
    val ora = momento.hour + momento.minute / 60f
    val (alba, tramonto) = when (momento.month) {
        Month.NOVEMBER, Month.DECEMBER, Month.JANUARY -> 7.5f to 17f
        Month.FEBRUARY, Month.OCTOBER -> 7f to 18f
        Month.MARCH, Month.SEPTEMBER -> 6.5f to 19.5f
        Month.APRIL, Month.AUGUST -> 6.2f to 20.3f
        else -> 5.6f to 21f
    }
    return ora < alba || ora >= tramonto
}
