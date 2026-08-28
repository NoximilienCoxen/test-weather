package com.forli.meteo.data

import java.time.LocalDate
import java.time.LocalDateTime

data class CurrentWeather(
    val temperature: Double? = null,
    val apparent: Double? = null,
    val humidity: Double? = null,
    val dewPoint: Double? = null,
    val precipitation: Double? = null,
    val weatherCode: Int? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val windGusts: Double? = null,
    /** Vero di giorno: sceglie fra sole e luna nell'icona del widget. */
    val isDay: Boolean = true,
)

data class DayForecast(
    val date: LocalDate,
    /** Sigla mostrata nella striscia in fondo: OGGI, DOM, LUN... */
    val label: String,
    val weatherCode: Int? = null,
    val tempMax: Double? = null,
    val tempMin: Double? = null,
    val apparentMax: Double? = null,
    val apparentMin: Double? = null,
    val humidityMean: Double? = null,
    val dewPointMean: Double? = null,
    val precipitationSum: Double? = null,
    val precipProbability: Int? = null,
    val precipHours: Double? = null,
    val windMax: Double? = null,
    val gustMax: Double? = null,
    val windDirection: Double? = null,
    val uvMax: Double? = null,
    /** Alba e tramonto: governano il colore del sole e quello del cielo. */
    val sunrise: LocalDateTime? = null,
    val sunset: LocalDateTime? = null,
)

/** Un'ora della previsione: e' l'unita' su cui scorre la schermata principale. */
data class HourForecast(
    val time: LocalDateTime,
    val temperature: Double? = null,
    val apparent: Double? = null,
    val weatherCode: Int? = null,
    /** Millimetri: dice quanto forte, non quanto probabile. */
    val precipitation: Double? = null,
    /** Percentuale: dice quanto probabile, non quanto forte. */
    val precipProbability: Int? = null,
    val isDay: Boolean = true,
)

data class Forecast(
    val current: CurrentWeather,
    val days: List<DayForecast>,
    val hours: List<HourForecast> = emptyList(),
    /** La localita' a cui si riferisce: dati e posto viaggiano insieme. */
    val place: Place = Place.FORLI,
    /** Scarto dall'ora universale della localita', in secondi. */
    val utcOffsetSeconds: Int = 0,
    /** Quando e' stata ricevuta: la schermata delle impostazioni lo dichiara. */
    val fetchedAt: LocalDateTime = LocalDateTime.now(),
) {
    /** Il giorno in cui cade un certo istante, per alba e tramonto. */
    fun dayOf(moment: LocalDateTime): DayForecast? =
        days.firstOrNull { it.date == moment.toLocalDate() } ?: days.firstOrNull()

    /**
     * Che ore sono nella localita' mostrata.
     *
     * Non l'orologio del telefono: gli orari della previsione sono espressi nel
     * fuso del posto, e da quando il posto lo sceglie l'utente i due possono
     * essere mezza giornata distanti. Confrontarli darebbe un'ora corrente
     * sbagliata di preciso quel tanto.
     */
    fun nowThere(): LocalDateTime =
        java.time.Instant.now()
            .atOffset(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
            .toLocalDateTime()
}
