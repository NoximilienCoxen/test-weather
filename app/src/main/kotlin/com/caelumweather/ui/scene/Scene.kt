package com.caelumweather.ui.scene

import com.caelumweather.lingua.tr
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
enum class Scena(private val ita: String, private val eng: String, private val ted: String, private val fra: String, private val spa: String) {
    MARE("Sole e mare", "Sun and sea", "Sonne und Meer", "Soleil et mer", "Sol y mar"),
    CITTA_NATALE("Città innevata", "Snowy town", "Verschneite Stadt", "Ville enneigée", "Ciudad nevada"),
    TEMPESTA("Nave in tempesta", "Ship in a storm", "Schiff im Sturm", "Navire dans la tempête", "Barco en la tormenta"),
    FERMATA("Fermata sotto la pioggia", "Bus stop in the rain", "Haltestelle im Regen", "Arrêt de bus sous la pluie", "Parada bajo la lluvia"),
    GRANO("Campo di grano", "Wheat field", "Weizenfeld", "Champ de blé", "Campo de trigo"),
    ARCOBALENO("Dopo la pioggia", "After the rain", "Nach dem Regen", "Après la pluie", "Después de la lluvia"),
    MONTAGNA("Montagna", "Mountain", "Berge", "Montagne", "Montaña"),
    ;

    val nome: String get() = tr(ita, eng, ted, fra, spa)
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
