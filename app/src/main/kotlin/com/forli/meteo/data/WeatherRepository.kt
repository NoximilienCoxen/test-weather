package com.forli.meteo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Accesso a Open-Meteo con la sola HttpURLConnection della piattaforma.
 * Nessuna libreria di rete: la richiesta e' una sola e la risposta e' piccola.
 */
class WeatherRepository(
    private val latitude: Double = FORLI_LAT,
    private val longitude: Double = FORLI_LON,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    suspend fun load(): Result<Forecast> = withContext(Dispatchers.IO) {
        runCatching {
            val body = httpGet(buildUrl())
            val dto = json.decodeFromString<OpenMeteoResponse>(body)
            if (dto.error == true) error(dto.reason ?: "Open-Meteo ha risposto con un errore")
            dto.toForecast()
        }
    }

    private fun buildUrl(): String = buildString {
        append("https://api.open-meteo.com/v1/forecast")
        append("?latitude=").append(latitude)
        append("&longitude=").append(longitude)
        append("&timezone=auto")
        append("&forecast_days=7")
        append("&wind_speed_unit=ms")
        append("&current=").append(CURRENT_VARS)
        append("&daily=").append(DAILY_VARS)
        append("&hourly=").append(HOURLY_VARS)
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code da Open-Meteo: ${text.take(200)}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FORLI_LAT = 44.2226
        const val FORLI_LON = 12.0407
        const val CITY = "Forlì"

        private const val CURRENT_VARS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m," +
                "precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day"

        private const val HOURLY_VARS =
            "temperature_2m,apparent_temperature,weather_code,precipitation," +
                "precipitation_probability,is_day"

        private const val DAILY_VARS =
            "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
                "apparent_temperature_min,precipitation_sum,precipitation_probability_max," +
                "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max," +
                "relative_humidity_2m_mean,dew_point_2m_mean,precipitation_hours"
    }
}

private fun <T> List<T>.at(index: Int): T? = getOrNull(index)

internal fun OpenMeteoResponse.toForecast(): Forecast {
    val d = daily
    val today = LocalDate.now()

    val days = d?.time?.mapIndexed { i, iso ->
        val date = runCatching { LocalDate.parse(iso) }.getOrDefault(today.plusDays(i.toLong()))
        DayForecast(
            date = date,
            label = if (date == today) "OGGI" else date.dayOfWeek.italianShort(),
            weatherCode = d.weatherCode.at(i),
            tempMax = d.tempMax.at(i),
            tempMin = d.tempMin.at(i),
            apparentMax = d.apparentMax.at(i),
            apparentMin = d.apparentMin.at(i),
            humidityMean = d.humidityMean.at(i),
            dewPointMean = d.dewPointMean.at(i),
            precipitationSum = d.precipitationSum.at(i),
            precipProbability = d.precipProbability.at(i),
            precipHours = d.precipitationHours.at(i),
            windMax = d.windMax.at(i),
            gustMax = d.gustMax.at(i),
            windDirection = d.windDirection.at(i),
            uvMax = d.uvMax.at(i),
        )
    }.orEmpty()

    val hours = hourly?.time?.mapIndexedNotNull { i, iso ->
        val at = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return@mapIndexedNotNull null
        HourForecast(
            time = at,
            temperature = hourly.temperature.at(i),
            apparent = hourly.apparent.at(i),
            weatherCode = hourly.weatherCode.at(i),
            precipitation = hourly.precipitation.at(i),
            precipProbability = hourly.precipProbability.at(i),
            isDay = (hourly.isDay.at(i) ?: 1) == 1,
        )
    }.orEmpty()

    return Forecast(
        hours = hours,
        current = CurrentWeather(
            temperature = current?.temperature,
            apparent = current?.apparent,
            humidity = current?.humidity,
            dewPoint = current?.dewPoint,
            precipitation = current?.precipitation,
            weatherCode = current?.weatherCode,
            windSpeed = current?.windSpeed,
            windDirection = current?.windDirection,
            windGusts = current?.windGusts,
        ),
        days = days,
    )
}

private fun DayOfWeek.italianShort(): String = when (this) {
    DayOfWeek.MONDAY -> "LUN"
    DayOfWeek.TUESDAY -> "MAR"
    DayOfWeek.WEDNESDAY -> "MER"
    DayOfWeek.THURSDAY -> "GIO"
    DayOfWeek.FRIDAY -> "VEN"
    DayOfWeek.SATURDAY -> "SAB"
    DayOfWeek.SUNDAY -> "DOM"
}
