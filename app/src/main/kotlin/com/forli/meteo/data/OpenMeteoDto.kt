package com.forli.meteo.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open-Meteo restituisce il blocco daily in forma colonnare: una lista per
 * variabile, tutte allineate sull'indice del giorno.
 */
@Serializable
data class OpenMeteoResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    /**
     * Scarto dall'ora universale della localita' richiesta. Serve a sapere che
     * ore sono *li'*: con una citta' scelta dall'utente l'orologio del telefono
     * non e' piu' una risposta.
     */
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    val current: CurrentDto? = null,
    val daily: DailyDto? = null,
    val hourly: HourlyDto? = null,
    val error: Boolean? = null,
    val reason: String? = null,
)

@Serializable
data class CurrentDto(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Double? = null,
    @SerialName("apparent_temperature") val apparent: Double? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Double? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
)

@Serializable
data class DailyDto(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_max") val apparentMax: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_min") val apparentMin: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipProbability: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windMax: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m_max") val gustMax: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirection: List<Double?> = emptyList(),
    @SerialName("uv_index_max") val uvMax: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m_mean") val humidityMean: List<Double?> = emptyList(),
    @SerialName("dew_point_2m_mean") val dewPointMean: List<Double?> = emptyList(),
    @SerialName("precipitation_hours") val precipitationHours: List<Double?> = emptyList(),
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
)

/** Risposta della ricerca localita'. Qui non c'e' niente di colonnare. */
@Serializable
data class GeocodingResponse(
    val results: List<GeocodingHit> = emptyList(),
)

@Serializable
data class GeocodingHit(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Regione. L'API la chiama cosi' perche' esistono admin2, admin3... */
    val admin1: String? = null,
    val country: String? = null,
)

/** Anche il blocco orario e' colonnare: una lista per variabile. */
@Serializable
data class HourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("apparent_temperature") val apparent: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipProbability: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
)
