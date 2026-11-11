package com.caelumweather.data

import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.TempUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * "Com'era un anno fa oggi": un giorno dell'archivio di Open-Meteo.
 *
 * **Una richiesta sola, e solo per questo.** L'archivio era gia' nell'app: serviva
 * alla "norma storica", la media degli ultimi dieci anni, che costava dieci
 * richieste in fila a ogni previsione e finiva in un campo che nessuno leggeva
 * (vedi la nota in `WeatherRepository`). Qui si chiede **un giorno**, una
 * volta per localita' e per data, e la risposta si tiene in memoria: aprire e
 * richiudere l'app non la richiede, cambiare giorno non la richiede.
 *
 * E' il quinto indirizzo di Open-Meteo (`archive-api.open-meteo.com`) e sta
 * nell'informativa (`docs/privacy.md`) e nelle note legali.
 *
 * **Non provato contro una risposta vera**: dal container in cui e' stato
 * scritto l'host era bloccato. Il formato e' quello documentato e identico al
 * blocco `daily` della previsione; `StoricoTest` lo legge da un esempio fatto a
 * mano, e il primo avvio su un telefono dira' se la realta' combacia.
 */
data class GiornoStorico(
    val data: LocalDate,
    val tempMax: Double?,
    val tempMin: Double?,
    /** Millimetri di precipitazione nel giorno. */
    val pioggia: Double?,
    val codice: Int?,
)

@Serializable
private data class StoricoDto(
    val daily: StoricoGiorniDto? = null,
    val error: Boolean? = null,
    val reason: String? = null,
)

@Serializable
private data class StoricoGiorniDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val max: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val min: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val pioggia: List<Double?> = emptyList(),
    @SerialName("weather_code") val codice: List<Int?> = emptyList(),
)

private val storicoJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

/**
 * Legge la risposta dell'archivio per un giorno solo. Nullo se non porta
 * nemmeno una temperatura (giorno troppo recente, o fuori copertura); un
 * errore dichiarato dall'API solleva.
 */
internal fun parseStorico(body: String): GiornoStorico? {
    val dto = storicoJson.decodeFromString<StoricoDto>(body)
    if (dto.error == true) error(dto.reason ?: "Open-Meteo (archivio) ha risposto con un errore")
    val d = dto.daily ?: return null
    val data = d.time.firstOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val g = GiornoStorico(
        data = data,
        tempMax = d.max.firstOrNull(),
        tempMin = d.min.firstOrNull(),
        pioggia = d.pioggia.firstOrNull(),
        codice = d.codice.firstOrNull(),
    )
    return g.takeIf { it.tempMax != null || it.tempMin != null }
}

/** Il giorno di un anno prima: il 29 febbraio diventa il 28, come fa `java.time`. */
fun unAnnoPrima(oggi: LocalDate): LocalDate = oggi.minusYears(1)

class StoricoRepository(private val place: Place) {

    suspend fun unAnnoFa(oggi: LocalDate): Result<GiornoStorico?> = withContext(Dispatchers.IO) {
        runCatching {
            require(place.hasFiniteCoordinates) { "Coordinate non utilizzabili per ${place.name}" }
            val giorno = unAnnoPrima(oggi)
            val url = buildString {
                append(ARCHIVIO_ENDPOINT)
                append("?latitude=").append(place.latitude)
                append("&longitude=").append(place.longitude)
                append("&start_date=").append(giorno).append("&end_date=").append(giorno)
                append("&daily=").append(ARCHIVIO_VARS)
                append("&timezone=auto")
            }
            parseStorico(httpGet(url, fonte = "Open-Meteo (archivio)"))
        }
    }

    companion object {
        const val ARCHIVIO_ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"
        const val ARCHIVIO_VARS = "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code"
    }
}

/**
 * Le due righe di "un anno fa": cosa c'era, e quanto e' diverso da oggi.
 *
 * Il confronto e' sulle **massime mostrate** (arrotondate, nell'unita' scelta),
 * come per "ieri": chi ricorda il 17 di un anno fa e legge il 20 di oggi vuole
 * sentirsi dire 3, anche se i decimali direbbero 2,6. Sotto i due gradi si dice
 * "come un anno fa": un grado non e' una differenza che si racconta.
 */
data class RigheUnAnnoFa(val cosaEra: String, val confronto: String?)

fun righeUnAnnoFa(storico: GiornoStorico, oggi: DayForecast?, unita: TempUnit): RigheUnAnnoFa {
    fun g(v: Double?) = v?.let { "${unita.from(it).roundToInt()}°" } ?: "--"
    val pioggia = storico.pioggia?.takeIf { it >= 0.1 }?.let {
        tr(
            "${String.format(Lingue.locale, "%.1f", it)} mm di pioggia",
            "${String.format(Lingue.locale, "%.1f", it)} mm of rain",
            "${String.format(Lingue.locale, "%.1f", it)} mm Regen",
            "${String.format(Lingue.locale, "%.1f", it)} mm de pluie",
            "${String.format(Lingue.locale, "%.1f", it)} mm de lluvia",
        )
    }
    val tempo = storico.codice?.let { Wmo.condition(it).lowercase(Lingue.locale) }
    val cosaEra = listOfNotNull(tempo, "${g(storico.tempMax)} / ${g(storico.tempMin)}", pioggia).joinToString(" · ")

    val ora = oggi?.tempMax
    val allora = storico.tempMax
    val scarto = if (ora != null && allora != null) unita.from(ora).roundToInt() - unita.from(allora).roundToInt() else null
    val confronto = scarto?.let {
        when {
            kotlin.math.abs(it) < 2 -> tr("Oggi quasi come allora.", "Today is about the same.", "Heute fast wie damals.", "Aujourd'hui presque pareil.", "Hoy casi igual.")
            it > 0 -> tr("Oggi $it° in più sulla massima.", "Today is $it° warmer on the high.", "Heute $it° wärmer beim Höchstwert.", "Aujourd'hui $it° de plus à la maximale.", "Hoy $it° más en la máxima.")
            else -> tr("Oggi ${-it}° in meno sulla massima.", "Today is ${-it}° colder on the high.", "Heute ${-it}° kälter beim Höchstwert.", "Aujourd'hui ${-it}° de moins à la maximale.", "Hoy ${-it}° menos en la máxima.")
        }
    }
    return RigheUnAnnoFa(cosaEra, confronto)
}
