package com.forli.meteo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Accesso a Open-Meteo con la sola HttpURLConnection della piattaforma.
 * Nessuna libreria di rete: la richiesta e' una sola e la risposta e' piccola.
 *
 * Le temperature arrivano sempre in gradi Celsius e la conversione avviene al
 * momento di scriverle. Chiedere i Fahrenheit all'API significherebbe rifare
 * l'intera richiesta per cambiare un'unita' di misura, cioe' restare fermi
 * davanti a una schermata vuota per una scelta che e' solo di scrittura.
 */
class WeatherRepository(private val place: Place = Place.FORLI) {

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
            dto.toForecast(place)
        }
    }

    private fun buildUrl(): String = buildString {
        append(FORECAST_ENDPOINT)
        append("?latitude=").append(place.latitude)
        append("&longitude=").append(place.longitude)
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
        const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODING_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"

        const val CURRENT_VARS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m," +
                "precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day"

        const val HOURLY_VARS =
            "temperature_2m,apparent_temperature,weather_code,precipitation," +
                "precipitation_probability,wind_speed_10m,wind_direction_10m,is_day"

        const val DAILY_VARS =
            "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
                "apparent_temperature_min,precipitation_sum,precipitation_probability_max," +
                "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max," +
                "relative_humidity_2m_mean,dew_point_2m_mean,precipitation_hours,sunrise,sunset"

        /**
         * Ricerca di localita' per nome, sempre su Open-Meteo e sempre senza
         * chiave. Vive qui e non in una classe a parte perche' e' la stessa
         * fonte: separarle darebbe l'idea di due servizi diversi.
         */
        suspend fun search(query: String): Result<List<Place>> = withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                if (trimmed.length < 2) return@runCatching emptyList()
                val url = buildString {
                    append(GEOCODING_ENDPOINT)
                    append("?name=").append(URLEncoder.encode(trimmed, "UTF-8"))
                    append("&count=8&language=it&format=json")
                }
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/json")
                }
                val body = try {
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error("HTTP $code dalla ricerca localita'")
                    text
                } finally {
                    connection.disconnect()
                }
                val parsed = Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<GeocodingResponse>(body)
                parsed.results.map { hit ->
                    Place(
                        name = hit.name,
                        admin = hit.admin1,
                        country = hit.country,
                        latitude = hit.latitude,
                        longitude = hit.longitude,
                    )
                }
            }
        }
    }
}

/**
 * La barra racconta la giornata, non la settimana: l'API ne restituisce
 * centosessantotto, ma centosessantotto tratti in una barra sola sono
 * illeggibili.
 */
private const val HOURS_IN_DAY = 24

private fun <T> List<T>.at(index: Int): T? = getOrNull(index)

private fun String?.asDateTime(): LocalDateTime? =
    this?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

internal fun OpenMeteoResponse.toForecast(place: Place): Forecast {
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
            sunrise = d.sunrise.at(i).asDateTime(),
            sunset = d.sunset.at(i).asDateTime(),
        )
    }.orEmpty()

    val hours = hourly?.time?.mapIndexedNotNull { i, iso ->
        val at = iso.asDateTime() ?: return@mapIndexedNotNull null
        HourForecast(
            time = at,
            temperature = hourly.temperature.at(i),
            apparent = hourly.apparent.at(i),
            weatherCode = hourly.weatherCode.at(i),
            precipitation = hourly.precipitation.at(i),
            precipProbability = hourly.precipProbability.at(i),
            windSpeed = hourly.windSpeed.at(i),
            windDirection = hourly.windDirection.at(i),
            isDay = (hourly.isDay.at(i) ?: 1) == 1,
        )
    }.orEmpty().take(HOURS_IN_DAY)

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
        place = place,
        utcOffsetSeconds = utcOffsetSeconds ?: 0,
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
