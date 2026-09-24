package io.github.noximiliencoxen.caelum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Un quarto d'ora della previsione a breve. */
data class QuartoDOra(val inizio: LocalDateTime, val millimetri: Double?, val codice: Int?)

/** Cosa sta arrivando: in ordine di gravita', la grandine per prima. */
enum class TipoPrecipitazione { GRANDINE, TEMPORALE, NEVE, PIOGGIA }

/**
 * Qualcosa che comincia a cadere entro l'ora, nel quarto d'ora [inizio].
 *
 * [millimetri] e' il totale della prima mezz'ora a partire da li': dice se e'
 * una spruzzata o un rovescio, che e' la domanda di chi legge la notifica.
 */
data class PrecipitazioneInArrivo(
    val tipo: TipoPrecipitazione,
    val inizio: LocalDateTime,
    val millimetri: Double,
)

/**
 * Se nell'ora che viene comincia a cadere qualcosa, e cosa.
 *
 * **"In arrivo" vuol dire che adesso non cade.** Se il quarto d'ora in corso
 * e' gia' bagnato la risposta e' nulla: chi e' sotto la pioggia non ha
 * bisogno che glielo si dica, e una notifica ogni quarto d'ora durante un
 * temporale sarebbe il modo piu' rapido per farsele spegnere tutte.
 *
 * La grandine vince su tutto il resto della finestra: se un quarto d'ora
 * qualunque dell'ora la prevede, la notifica parla di grandine anche se
 * comincia con due gocce, perche' e' la cosa per cui si corre a mettere
 * l'auto al coperto.
 */
fun precipitazioneInArrivo(
    quarti: List<QuartoDOra>,
    adesso: LocalDateTime,
    finestraMinuti: Long = 60,
): PrecipitazioneInArrivo? {
    val inCorso = quarti.lastOrNull { !it.inizio.isAfter(adesso) }
        ?.takeIf { ChronoUnit.MINUTES.between(it.inizio, adesso) < 15 }
    if (inCorso != null && bagnato(inCorso)) return null

    val fine = adesso.plusMinutes(finestraMinuti)
    val prossimi = quarti.filter { it.inizio.isAfter(adesso) && !it.inizio.isAfter(fine) }
    val primo = prossimi.firstOrNull(::bagnato) ?: return null

    val daLi = prossimi.filter { !it.inizio.isBefore(primo.inizio) }
    val tipo = when {
        daLi.any { it.codice == 96 || it.codice == 99 } -> TipoPrecipitazione.GRANDINE
        daLi.any { it.codice == 95 } -> TipoPrecipitazione.TEMPORALE
        primo.codice?.let { Wmo.family(it) == Wmo.Family.NEVE } == true -> TipoPrecipitazione.NEVE
        else -> TipoPrecipitazione.PIOGGIA
    }
    val mm = daLi.take(2).sumOf { it.millimetri ?: 0.0 }
    return PrecipitazioneInArrivo(tipo, primo.inizio, mm)
}

/**
 * Bagnato vuol dire che cade qualcosa di misurabile, o che il codice lo dice.
 *
 * La soglia e' due decimi di millimetro in un quarto d'ora: sotto e' la
 * pioviggine che i modelli spargono ai bordi di ogni nuvola, e un avviso per
 * quella insegnerebbe a ignorare gli avvisi.
 */
private fun bagnato(q: QuartoDOra): Boolean {
    val codice = q.codice
    if (codice != null && codice >= 95) return true
    return (q.millimetri ?: 0.0) >= 0.2 && (codice == null || codice >= 51)
}

/**
 * La previsione a quarti d'ora di Open-Meteo, per le prossime due ore.
 *
 * Una richiesta a parte e piccola, e non un pezzo della previsione intera:
 * la fa il lavoro in background ogni quarto d'ora, e scaricare otto giorni
 * per guardarne due ore sarebbe uno spreco per il telefono e per chi offre il
 * servizio gratis.
 */
object PrevisioneABreve {

    /** I quarti d'ora, e l'ora di adesso **nel fuso della citta'**, non del telefono. */
    class Risposta(val quarti: List<QuartoDOra>, val adesso: LocalDateTime)

    suspend fun carica(place: Place): Result<Risposta> = withContext(Dispatchers.IO) {
        runCatching {
            require(place.hasFiniteCoordinates) { "Coordinate non utilizzabili per ${place.name}" }
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${place.latitude}&longitude=${place.longitude}" +
                "&timezone=auto&minutely_15=precipitation,weather_code&forecast_minutely_15=10"
            leggi(httpGet(url, fonte = "Open-Meteo"))
        }
    }

    internal fun leggi(body: String, istante: Instant = Instant.now()): Risposta {
        val dto = json.decodeFromString<RispostaQuarti>(body)
        val adesso = LocalDateTime.ofInstant(istante, ZoneOffset.ofTotalSeconds(dto.utcOffsetSeconds))
        val q = dto.minutely15 ?: return Risposta(emptyList(), adesso)
        val quarti = q.time.mapIndexedNotNull { i, t ->
            val inizio = runCatching { LocalDateTime.parse(t) }.getOrNull() ?: return@mapIndexedNotNull null
            QuartoDOra(inizio, q.precipitation.getOrNull(i), q.weatherCode.getOrNull(i))
        }
        return Risposta(quarti, adesso)
    }

    private val json = Json { ignoreUnknownKeys = true }
}

@Serializable
private data class RispostaQuarti(
    @SerialName("minutely_15") val minutely15: Quarti? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
)

@Serializable
private data class Quarti(
    val time: List<String> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)
