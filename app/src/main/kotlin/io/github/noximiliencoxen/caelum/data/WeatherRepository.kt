package io.github.noximiliencoxen.caelum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
class WeatherRepository(
    private val place: Place = Place.FORLI,
    private val model: WeatherModel = WeatherModel.AUTO,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    suspend fun load(): Result<Forecast> = withContext(Dispatchers.IO) {
        runCatching {
            // Una coordinata non finita non si concatena in un URL: `NaN` ci
            // finisce come testo, Open-Meteo lo rimanda indietro dentro il JSON
            // e il fallimento si presenta come un errore di lettura del
            // formato, dove non c'e' niente da capire. Meglio fermarsi qui, con
            // il nome di chi ha portato il numero. La sorgente vera si tura a
            // monte (vedi `DeviceLocation.describe`): questa e' la rete di
            // sicurezza per una localita' salvata quando la falla c'era ancora.
            require(place.hasFiniteCoordinates) {
                "Coordinate non utilizzabili per ${place.name}: " +
                    "${place.latitude}, ${place.longitude}"
            }
            val body = httpGet(buildUrl(), fonte = "Open-Meteo")
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
        // Otto e non sette: la striscia della settimana in fondo alla schermata
        // principale mostra oggi piu' sette giorni. Open-Meteo arriva a sedici,
        // quindi l'ottavo non costa una richiesta in piu' ne' un endpoint
        // diverso - allunga soltanto la lista che gia' arriva.
        append("&forecast_days=8")
        append("&wind_speed_unit=ms")
        modelsQueryValue()?.let { append("&models=").append(it) }
        append("&current=").append(CURRENT_VARS)
        append("&daily=").append(DAILY_VARS)
        append("&hourly=").append(HOURLY_VARS)
    }

    /**
     * Quale modello passare all'API.
     *
     * Con AUTO si lascia `best_match` di Open-Meteo: sceglie lui il modello
     * piu' adatto per la posizione richiesta e garantisce sempre 7 giorni
     * completi nel blocco `daily`. ICON-2I (ARPAE/ItaliaMeteo) e' piu'
     * preciso per l'Italia nelle prime 72 ore, ma copre solo ~3 giorni nel
     * blocco `daily` — i giorni 4-7 tornano con tempMax/tempMin nulli e la
     * curva settimanale si interrompe a meta'. Per questo non viene piu'
     * forzato in automatico: chi lo vuole lo sceglie dalle impostazioni.
     */
    private fun modelsQueryValue(): String? =
        if (model != WeatherModel.AUTO) model.apiValue else null

    companion object {
        const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODING_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"

        const val CURRENT_VARS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m," +
                "precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day"

        /**
         * Le grandezze orarie.
         *
         * La seconda meta' dell'elenco e' quella che mancava, e non era una
         * dimenticanza innocua: senza vento orario il grafico del vento in
         * modalita' GIORNO usciva vuoto, e umidita', rugiada e UV venivano
         * presi dal blocco `current` - cioe' da adesso - e mostrati sotto
         * un'intestazione che dichiarava tutt'altra ora.
         *
         * Costano poco: la risposta e' colonnare, e sono numeri.
         */
        const val HOURLY_VARS =
            "temperature_2m,apparent_temperature,weather_code,precipitation," +
                "precipitation_probability,is_day," +
                "relative_humidity_2m,dew_point_2m,wind_speed_10m,wind_gusts_10m," +
                "wind_direction_10m,uv_index,cloud_cover,surface_pressure,visibility," +
                "rain,snowfall"

        const val DAILY_VARS =
            "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
                "apparent_temperature_min,precipitation_sum,precipitation_probability_max," +
                "wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,uv_index_max," +
                "relative_humidity_2m_mean,dew_point_2m_mean,precipitation_hours," +
                "rain_sum,snowfall_sum,sunshine_duration,sunrise,sunset"

        // **Qui stava `loadNorm`, la media storica degli ultimi dieci anni.**
        // Scriveva in `DayForecast.normTemp`, che non leggeva nessuno da quando
        // il grafico che la mostrava e' uscito dalla schermata. Costava dieci
        // richieste in fila all'archivio a ogni previsione, e la sua media
        // divideva per dieci anche quando dieci anni non avevano risposto.
        // Rimossa insieme a `ARCHIVE_ENDPOINT`, `NORM_YEARS`, `simpleHttpGet`,
        // `ArchiveResponse` e `ArchiveDailyDto`. Se torna, torna col grafico.

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
                // Otto secondi e non dieci: qui si sta scrivendo in una
                // casella, e chi scrive aspetta meno volentieri di chi ha
                // appena aperto l'app.
                val body = httpGet(url, fonte = "la ricerca localita'", timeoutMs = 8_000)
                val parsed = lenientJson
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
 *
 * **Il taglio riguarda la barra, non la risposta.** Prima le altre
 * centoquarantaquattro venivano buttate via qui, e con loro qualunque
 * possibilita' di disegnare l'andamento orario di un giorno che non fosse
 * oggi: il dettaglio di mercoledi' non aveva niente da mostrare perche' il
 * repository aveva gia' deciso che non serviva a nessuno.
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
            rainSum = d.rainSum.at(i),
            snowfallSum = d.snowfallSum.at(i),
            sunshineSeconds = d.sunshineSeconds.at(i),
            sunrise = d.sunrise.at(i).asDateTime(),
            sunset = d.sunset.at(i).asDateTime(),
        )
    }.orEmpty()

    val allHours = hourly?.time?.mapIndexedNotNull { i, iso ->
        val at = iso.asDateTime() ?: return@mapIndexedNotNull null
        HourForecast(
            time = at,
            temperature = hourly.temperature.at(i),
            apparent = hourly.apparent.at(i),
            weatherCode = hourly.weatherCode.at(i),
            precipitation = hourly.precipitation.at(i),
            precipProbability = hourly.precipProbability.at(i),
            isDay = (hourly.isDay.at(i) ?: 1) == 1,
            humidity = hourly.humidity.at(i),
            dewPoint = hourly.dewPoint.at(i),
            windSpeed = hourly.windSpeed.at(i),
            windGusts = hourly.windGusts.at(i),
            windDirection = hourly.windDirection.at(i),
            uvIndex = hourly.uvIndex.at(i),
            cloudCover = hourly.cloudCover.at(i),
            pressure = hourly.pressure.at(i),
            visibility = hourly.visibility.at(i),
            rain = hourly.rain.at(i),
            snowfall = hourly.snowfall.at(i),
        )
    }.orEmpty()

    return Forecast(
        hours = allHours.take(HOURS_IN_DAY),
        allHours = allHours,
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
            isDay = (current?.isDay ?: 1) == 1,
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

/**
 * Il lettore JSON delle due chiamate accessorie, costruito una volta sola.
 *
 * Costruire un `Json` non e' gratis - il compilatore stesso lo segnala - e
 * questo veniva rifatto a ogni ricerca di localita' e a ogni richiesta della
 * norma storica, cioe' proprio dove si digita una lettera per volta.
 *
 * E' separato da quello dell'istanza (`json`, piu' permissivo) apposta: quello
 * legge la previsione, che ha campi nulli e valori da correggere; questi due
 * leggono risposte semplici e non hanno bisogno delle stesse concessioni.
 */
private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
