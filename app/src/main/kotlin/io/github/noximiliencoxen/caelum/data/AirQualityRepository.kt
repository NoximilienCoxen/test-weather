package io.github.noximiliencoxen.caelum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Qualita' dell'aria secondo l'indice europeo.
 *
 * Fonte separata da quella delle previsioni: Open-Meteo la serve su un altro
 * host, e non ha senso appesantire ogni previsione con inquinanti che solo un
 * widget guarda.
 */
@Serializable
private data class AirQualityDto(
    val current: AirQualityCurrentDto? = null,
    val error: Boolean? = null,
    val reason: String? = null,
)

@Serializable
private data class AirQualityCurrentDto(
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    val pm10: Double? = null,
)

/** Le sei bande dell'indice europeo, con il nome che l'AEA usa in italiano. */
enum class AirBand(val label: String) {
    BUONA("BUONA"),
    DISCRETA("DISCRETA"),
    MEDIA("MEDIA"),
    SCARSA("SCARSA"),
    MOLTO_SCARSA("MOLTO SCARSA"),
    ESTREMAMENTE_SCARSA("PESSIMA"),
    ;

    companion object {
        fun of(aqi: Int?): AirBand? = when {
            aqi == null -> null
            aqi <= 20 -> BUONA
            aqi <= 40 -> DISCRETA
            aqi <= 60 -> MEDIA
            aqi <= 80 -> SCARSA
            aqi <= 100 -> MOLTO_SCARSA
            else -> ESTREMAMENTE_SCARSA
        }
    }
}

/** Quel che serve a dire com'e' l'aria adesso. */
data class AirQuality(
    val europeanAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
) {
    val band: AirBand? get() = AirBand.of(europeanAqi)
}

class AirQualityRepository(private val place: Place = Place.FORLI) {

    // Tollerante di proposito: se un giorno l'API aggiunge o rinomina un
    // campo, il widget mostra "--" invece di sparire con un errore.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    suspend fun load(): Result<AirQuality> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(ENDPOINT)
                append("?latitude=").append(place.latitude)
                append("&longitude=").append(place.longitude)
                append("&timezone=auto")
                append("&current=").append(CURRENT_VARS)
            }
            val dto = json.decodeFromString<AirQualityDto>(httpGet(url))
            if (dto.error == true) error(dto.reason ?: "Open-Meteo ha risposto con un errore")
            AirQuality(
                europeanAqi = dto.current?.europeanAqi?.roundToInt(),
                pm25 = dto.current?.pm25,
                pm10 = dto.current?.pm10,
            )
        }
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
            if (code !in 200..299) error("HTTP $code dalla qualita' dell'aria: ${text.take(200)}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val ENDPOINT = "https://air-quality-api.open-meteo.com/v1/air-quality"
        const val CURRENT_VARS = "european_aqi,pm2_5,pm10"
    }
}
