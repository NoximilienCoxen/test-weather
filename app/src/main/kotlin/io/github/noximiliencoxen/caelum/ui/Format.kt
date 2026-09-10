package io.github.noximiliencoxen.caelum.ui

import io.github.noximiliencoxen.caelum.prefs.TempUnit
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
fun Double?.asPlainDegrees(unit: TempUnit = TempUnit.CELSIUS): String =
    this?.let { "${unit.from(it).roundToInt()}°" } ?: EMPTY

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
fun Double?.asMetresPerSecond(): String = this?.let { "${it.fixed()} M/S" } ?: EMPTY
fun Double?.asIndex(): String = this?.let { it.fixed() } ?: EMPTY
fun Double?.asPercent(): String = this?.let { "${it.roundToInt()}%" } ?: EMPTY
fun Int?.asPercent(): String = this?.let { "$it%" } ?: EMPTY

/** Centimetri di neve: Open-Meteo li da' cosi', e convertirli sarebbe inventare. */
fun Double?.asCentimetres(): String = this?.let { "${it.fixed()} CM" } ?: EMPTY

// ---------------------------------------------------------------------------
// **Qui c'erano altri nove formattatori, e non li chiamava nessuno.**
//
// `asDegrees`, `asBigNumber`, `asBigTemperature`, `asMillimetresPerDay`,
// `asHours`, `asHectopascal`, `asDistance`, `asHoursMinutes` e
// `secondsAsHoursMinutes`: verificati uno per uno su main e su test, zero
// chiamanti fuori da questo file.
//
// Erano piu' della meta' del file, ed e' una meta' che raccontava bene: due di
// loro portavano un commento lungo che spiegava **perche' erano due funzioni e
// non una**, e quella spiegazione e' sopravvissuta di parecchio al codice che
// la giustificava. `asHoursMinutes` scriveva le ore di luce in ore e minuti
// per non sbagliare di un decimo di giornata a dicembre - un ragionamento
// giusto, per una schermata che poi non e' stata fatta.
//
// Le sezioni del feed che le vorranno (vento, sole, aria) le riscriveranno
// quando avranno qualcosa da mostrare: allora si sapra' anche in quale forma
// servono davvero, invece di indovinarla in anticipo come e' successo qui.
// ---------------------------------------------------------------------------
