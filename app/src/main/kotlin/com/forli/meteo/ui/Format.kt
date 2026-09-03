package com.forli.meteo.ui

import com.forli.meteo.prefs.TempUnit
import kotlin.math.roundToInt

private const val EMPTY = "--"

/**
 * Il grado, scritto per numero e non per carattere.
 *
 * Il repository viaggia fra Windows e la CI e passa da `.gitattributes` che
 * dichiarano fine riga e permessi: un carattere fuori dall'ASCII in mezzo al
 * codice e' l'unico pezzo che nessuno di quei due controlli protegge, e un
 * transito storto lo trasformerebbe in un punto interrogativo alto mezzo
 * schermo.
 */
private const val DEGREE = "\u00B0"

/**
 * I gradi arrivano sempre in Celsius e vengono convertiti qui, all'ultimo
 * momento utile. Chiederli in Fahrenheit alla rete vorrebbe dire rifare tutta
 * la richiesta per cambiare un'unita' di misura, cioe' aspettare davanti a una
 * schermata vuota per una scelta che e' solo di scrittura.
 */
fun Double?.asDegrees(unit: TempUnit = TempUnit.CELSIUS): String =
    this?.let { "${unit.from(it).roundToInt()}${unit.symbol}" } ?: EMPTY

fun Double?.asPlainDegrees(unit: TempUnit = TempUnit.CELSIUS): String =
    this?.let { "${unit.from(it).roundToInt()}°" } ?: EMPTY

/**
 * Il numero gigante non porta unita': solo la cifra, arrotondata.
 * Due funzioni e non una con un parametro opzionale: la cifra gigante mostra
 * anche millimetri e metri al secondo, e una conversione di temperatura
 * applicata per svista al vento non lascerebbe traccia di se'.
 */
fun Double?.asBigNumber(): String = this?.roundToInt()?.toString() ?: EMPTY

fun Double?.asBigTemperature(unit: TempUnit): String =
    this?.let { unit.from(it).roundToInt().toString() } ?: EMPTY

/**
 * La stessa cifra col grado in coda.
 *
 * Un numero grande da solo non dice di che grandezza si tratta: sopra c'e' una
 * nuvola e sotto un'ora, e ventotto potrebbe essere qualunque cosa. Il simbolo
 * lo dichiara senza aggiungere una riga di testo, e nella schermata principale
 * viene estruso con le cifre invece di stare loro accanto.
 *
 * Non porta la lettera dell'unita': quella e' una scelta che si fa una volta
 * nelle impostazioni, e ripeterla a ogni ora sarebbe rumore.
 */
fun Double?.asBigDegrees(unit: TempUnit): String =
    this?.let { "${unit.from(it).roundToInt()}$DEGREE" } ?: EMPTY

/**
 * `Locale.ROOT` e non quello di sistema, su ogni formattazione numerica.
 *
 * In italiano `String.format("%.1f")` scrive la virgola, e "0,4 MM" accanto a
 * "44,2226° N" fa leggere quattro numeri dove ce ne sono due. Le coordinate lo
 * fissavano gia'; le altre formattazioni no, e restituivano il separatore del
 * telefono di chi guarda - cioe' un formato diverso a seconda del dispositivo.
 */
private val NUM: java.util.Locale = java.util.Locale.ROOT

/**
 * Il numero con i decimali richiesti.
 *
 * **Non chiamarla `dec`**: `Double.dec()` e' l'operatore di decremento della
 * libreria standard, e un membro vince sempre su un'estensione. Chiamandola
 * cosi', la chiamata non formattava un bel niente: restituiva il numero meno
 * uno, e i millimetri di pioggia uscivano negativi.
 */
private fun Double.fixed(decimals: Int = 1): String =
    String.format(NUM, "%.${decimals}f", this)

fun Double?.asMillimetres(): String = this?.let { "${it.fixed()} MM" } ?: EMPTY
fun Double?.asMillimetresPerDay(): String = this?.let { "${it.fixed()} MM/GIORNO" } ?: EMPTY
fun Double?.asMetresPerSecond(): String = this?.let { "${it.fixed()} M/S" } ?: EMPTY
fun Double?.asHours(): String = this?.let { "${it.roundToInt()} H" } ?: EMPTY
fun Double?.asIndex(): String = this?.let { it.fixed() } ?: EMPTY
fun Double?.asPercent(): String = this?.let { "${it.roundToInt()}%" } ?: EMPTY
fun Int?.asPercent(): String = this?.let { "$it%" } ?: EMPTY

/** Centimetri di neve: Open-Meteo li da' cosi', e convertirli sarebbe inventare. */
fun Double?.asCentimetres(): String = this?.let { "${it.fixed()} CM" } ?: EMPTY

/** Pressione al suolo. L'intero basta: il decimo di hPa non lo guarda nessuno. */
fun Double?.asHectopascal(): String = this?.let { "${it.roundToInt()} HPA" } ?: EMPTY

/**
 * Visibilita': in chilometri sopra il chilometro, in metri sotto.
 *
 * L'API la da' sempre in metri, e "24140 M" e' un numero che nessuno legge.
 * Sotto il chilometro invece i metri contano davvero, perche' e' li' che la
 * visibilita' smette di essere un dettaglio.
 */
fun Double?.asDistance(): String = when {
    this == null -> EMPTY
    this >= 1000.0 -> "${(this / 1000.0).fixed()} KM"
    else -> "${roundToInt()} M"
}

/**
 * Una durata in ore e minuti.
 *
 * "8 H" per otto ore e cinquanta minuti sbaglia di quasi un'ora, ed era il
 * modo in cui si scrivevano le ore di luce. Su una giornata di dicembre,
 * dove la luce e' otto ore in tutto, e' un decimo della giornata.
 */
fun Double?.asHoursMinutes(): String {
    val hours = this ?: return EMPTY
    if (hours < 0) return EMPTY
    val total = (hours * 60).roundToInt()
    val h = total / 60
    val m = total % 60
    return if (h == 0) "${m}m" else "${h}h ${m.toString().padStart(2, '0')}m"
}

/** Gli stessi minuti, partendo dai secondi che restituisce l'API. */
fun Double?.secondsAsHoursMinutes(): String =
    this?.let { (it / 3600.0).asHoursMinutes() } ?: EMPTY
