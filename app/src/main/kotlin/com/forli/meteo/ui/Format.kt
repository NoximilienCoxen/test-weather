package com.forli.meteo.ui

import kotlin.math.roundToInt

private const val EMPTY = "--"

fun Double?.asDegrees(): String = this?.let { "${it.roundToInt()}°C" } ?: EMPTY
fun Double?.asPlainDegrees(): String = this?.let { "${it.roundToInt()}°" } ?: EMPTY
fun Double?.asMillimetres(): String = this?.let { String.format("%.1f MM", it) } ?: EMPTY
fun Double?.asMillimetresPerDay(): String = this?.let { String.format("%.1f MM/GIORNO", it) } ?: EMPTY
fun Double?.asMetresPerSecond(): String = this?.let { String.format("%.1f M/S", it) } ?: EMPTY
fun Double?.asHours(): String = this?.let { "${it.roundToInt()} H" } ?: EMPTY
fun Double?.asIndex(): String = this?.let { String.format("%.1f", it) } ?: EMPTY
fun Double?.asPercent(): String = this?.let { "${it.roundToInt()}%" } ?: EMPTY
fun Int?.asPercent(): String = this?.let { "$it%" } ?: EMPTY

/** Il numero gigante non porta unita': solo la cifra, arrotondata. */
fun Double?.asBigNumber(): String = this?.roundToInt()?.toString() ?: EMPTY
