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
)
