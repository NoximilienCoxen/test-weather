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

fun Double?.asMillimetres(): String = this?.let { String.format("%.1f MM", it) } ?: EMPTY
fun Double?.asMillimetresPerDay(): String = this?.let { String.format("%.1f MM/GIORNO", it) } ?: EMPTY
fun Double?.asMetresPerSecond(): String = this?.let { String.format("%.1f M/S", it) } ?: EMPTY
fun Double?.asHours(): String = this?.let { "${it.roundToInt()} H" } ?: EMPTY
fun Double?.asIndex(): String = this?.let { String.format("%.1f", it) } ?: EMPTY
fun Double?.asPercent(): String = this?.let { "${it.roundToInt()}%" } ?: EMPTY
fun Int?.asPercent(): String = this?.let { "$it%" } ?: EMPTY
