package com.forli.meteo.ui

import com.forli.meteo.prefs.TempUnit
import kotlin.math.roundToInt

private const val EMPTY = "--"

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

fun Double?.asMillimetres(): String = this?.let { String.format("%.1f MM", it) } ?: EMPTY
fun Double?.asMillimetresPerDay(): String = this?.let { String.format("%.1f MM/GIORNO", it) } ?: EMPTY
fun Double?.asMetresPerSecond(): String = this?.let { String.format("%.1f M/S", it) } ?: EMPTY
fun Double?.asHours(): String = this?.let { "${it.roundToInt()} H" } ?: EMPTY
fun Double?.asIndex(): String = this?.let { String.format("%.1f", it) } ?: EMPTY
fun Double?.asPercent(): String = this?.let { "${it.roundToInt()}%" } ?: EMPTY
fun Int?.asPercent(): String = this?.let { "$it%" } ?: EMPTY
