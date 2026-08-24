package com.forli.meteo.ui.home

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos

/**
 * Fase lunare calcolata in locale: Open-Meteo non la fornisce, e per disegnare
 * una mediana basta il mese sinodico medio a partire da un novilunio noto.
 * L'errore accumulato resta ben sotto il giorno per gli anni che ci
 * interessano, cioe' invisibile a questa dimensione.
 */
object MoonPhase {

    private val KNOWN_NEW_MOON: LocalDate = LocalDate.of(2000, 1, 6)
    private const val SYNODIC_DAYS = 29.530588853

    /** 0 = novilunio, 0.5 = plenilunio, 1 = novilunio successivo. */
    fun at(date: LocalDate): Float {
        val days = ChronoUnit.DAYS.between(KNOWN_NEW_MOON, date).toDouble()
        val cycles = days / SYNODIC_DAYS
        return ((cycles - kotlin.math.floor(cycles)).toFloat()).coerceIn(0f, 1f)
    }

    /** Frazione illuminata, da 0 a 1. */
    fun illumination(phase: Float): Float =
        ((1f - cos(2.0 * Math.PI * phase).toFloat()) / 2f).coerceIn(0f, 1f)

    /**
     * Semiasse orizzontale del terminatore, in frazione del raggio.
     * Zero al quarto, uno ai due estremi del ciclo.
     */
    fun terminator(phase: Float): Float = abs(cos(2.0 * Math.PI * phase).toFloat())

    /** Vero da novilunio a plenilunio: la parte illuminata sta a destra. */
    fun waxing(phase: Float): Boolean = phase < 0.5f
}
