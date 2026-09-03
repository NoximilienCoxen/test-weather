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
    /** Millimetri di sola pioggia, separati dalla neve nel totale. */
    @SerialName("rain_sum") val rainSum: List<Double?> = emptyList(),
    /** Centimetri di neve. */
    @SerialName("snowfall_sum") val snowfallSum: List<Double?> = emptyList(),
    /**
     * Secondi di sole effettivo.
     *
     * Non e' la stessa cosa della luce fra alba e tramonto, che e' quel che
     * la pagina SOLE mostrava spacciandolo per "luce solare": una giornata
     * coperta ha le stesse quattordici ore di luce e zero ore di sole.
     */
    @SerialName("sunshine_duration") val sunshineSeconds: List<Double?> = emptyList(),
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

/**
 * Risposta dell'API storica (archive-api.open-meteo.com).
 *
 * Serve solo a ricavare la media mensile della temperatura: un campo solo,
 * una lista di valori giornalieri da mediare. La struttura e' identica alla
 * forecast, ma in un tipo separato perche' i campi non coincidono e mischiare
 * i due causerebbe deserializzazioni silenziose con campi nulli.
 */
@Serializable
data class ArchiveResponse(
    val daily: ArchiveDailyDto? = null,
    val error: Boolean? = null,
    val reason: String? = null,
)

@Serializable
data class ArchiveDailyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_mean") val tempMean: List<Double?> = emptyList(),
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
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    // Le grandezze qui sotto non venivano chiese all'API, e la schermata di
    // dettaglio ne pagava il prezzo: il grafico del vento in modalita' GIORNO
    // era letteralmente vuoto, e umidita' e punto di rugiada venivano dal
    // blocco `current` - cioe' da adesso - mentre l'intestazione dichiarava
    // l'ora selezionata.
    @SerialName("relative_humidity_2m") val humidity: List<Double?> = emptyList(),
    @SerialName("dew_point_2m") val dewPoint: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Int?> = emptyList(),
    @SerialName("surface_pressure") val pressure: List<Double?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    /** Millimetri di sola pioggia: distinguerla dalla neve cambia cosa indossi. */
    val rain: List<Double?> = emptyList(),
    /** Centimetri di neve, non millimetri: e' l'unita' che usa Open-Meteo. */
    val snowfall: List<Double?> = emptyList(),
)
