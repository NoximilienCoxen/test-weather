package io.github.noximiliencoxen.caelum.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Le allerte ufficiali degli Stati Uniti, dal National Weather Service.
 *
 * `api.weather.gov/alerts/active?point=lat,lon` restituisce le allerte in
 * corso **per quel punto**: niente confronto di nomi di regione come per
 * MeteoAlarm, lo fa il servizio sulle sue zone. Senza chiave; chiede solo un
 * `User-Agent` che dica chi chiama, e `httpGet` lo manda gia'.
 *
 * ## Com'e' fatta davvero la risposta
 *
 * Letta da risposte vere, catturate dalla sonda `probe_allerte_mondo.py` il 5
 * ottobre 2026 (California, Florida, Alaska) e copiate in
 * `src/test/resources/nws-*.json`, non dedotta dalla documentazione:
 *
 * - **GeoJSON e basta.** Con `Accept: application/cap+xml` il servizio
 *   risponde lo stesso in `application/geo+json`.
 * - Ogni voce ha `event` in inglese ("Extreme Heat Warning", "Rip Current
 *   Statement"), `severity` CAP, `onset`, `ends`, `expires`, `areaDesc`,
 *   `senderName`, `description`, `instruction`, tutto in `properties`.
 * - **`expires` non e' la fine dell'evento**: e' la scadenza del messaggio,
 *   che viene riemesso. Un Heat Advisory in vigore fino al 7 ottobre portava
 *   `expires` alle 18 del 5. La fine e' `ends`, e quando manca (un Flood
 *   Warning in Florida) resta `expires`.
 * - Nessun colore: il NWS gradua con Warning, Watch, Advisory e Statement. Il
 *   colore dell'app viene dalla `severity` CAP - Extreme rossa, Severe
 *   arancione, il resto gialla - e il nome originale dell'evento resta in
 *   testa alla descrizione, perche' chi legge lo ritrovi sui canali del NWS.
 */
internal object NwsAlerts {

    const val ENDPOINT = "https://api.weather.gov/alerts/active?point="

    /** I tipi preferiti davanti e il jolly in fondo, come per MeteoAlarm (§8-ter). */
    const val ACCEPT = "application/geo+json, application/json;q=0.9, */*;q=0.8"

    /** Le coordinate con quattro decimali: il servizio rifiuta i punti troppo lunghi. */
    fun indirizzo(place: Place): String =
        ENDPOINT + "%.4f,%.4f".format(java.util.Locale.ROOT, place.latitude, place.longitude)

    fun load(place: Place, fuso: ZoneOffset?): List<WeatherAlert> =
        parse(httpGet(indirizzo(place), fonte = "National Weather Service", accept = ACCEPT), fuso)

    /**
     * Le allerte di una risposta, gia' nel modello dell'app.
     *
     * Si tengono solo i messaggi veri (`status` "Actual": il servizio emette
     * anche prove ed esercitazioni) e non le revoche. Lo stesso evento puo'
     * arrivare piu' volte, riemesso da uffici vicini o aggiornato: vale uno
     * per evento e fine, il piu' recente.
     */
    fun parse(body: String, fuso: ZoneOffset?): List<WeatherAlert> {
        val voci = json.decodeFromString(NwsRisposta.serializer(), body).features
            .map { it.properties }
            .filter { it.status.equals("Actual", ignoreCase = true) }
            .filterNot { it.messageType.equals("Cancel", ignoreCase = true) }
            .filter { it.event != null }
        return voci
            .groupBy { it.event to (it.ends ?: it.expires) }
            .map { (_, stesse) -> stesse.maxBy { it.sent.orEmpty() } }
            .map { it.toAlert(fuso) }
            .sortedByDescending { it.level.weight }
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
}

@Serializable
internal data class NwsRisposta(val features: List<NwsVoce> = emptyList())

@Serializable
internal data class NwsVoce(val properties: NwsAllerta)

@Serializable
internal data class NwsAllerta(
    val id: String? = null,
    val areaDesc: String? = null,
    val sent: String? = null,
    val onset: String? = null,
    val expires: String? = null,
    val ends: String? = null,
    val status: String? = null,
    val messageType: String? = null,
    val severity: String? = null,
    val event: String? = null,
    val senderName: String? = null,
    val description: String? = null,
    val instruction: String? = null,
) {
    val level: AlertLevel
        get() = when (severity?.lowercase()) {
            "extreme" -> AlertLevel.ROSSA
            "severe" -> AlertLevel.ARANCIONE
            else -> AlertLevel.GIALLA
        }

    fun toAlert(fuso: ZoneOffset?): WeatherAlert {
        fun locale(testo: String?): LocalDateTime? {
            val istante = testo?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
            return if (fuso != null) {
                istante.withOffsetSameInstant(fuso).toLocalDateTime()
            } else {
                istante.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
        }
        val tipo = tipoDaEvento(event)
        return WeatherAlert(
            id = id ?: "$event|$ends|$expires",
            level = level,
            kind = tipo,
            headline = "${level.label}: ${tipo.label.lowercase()}",
            // Il nome del NWS in testa: "Extreme Heat Warning" e' cio' che chi
            // legge ritrova sui canali ufficiali, e il colore e' una traduzione.
            description = listOfNotNull(event, description?.takeIf { it.isNotBlank() })
                .joinToString("\n\n"),
            instruction = instruction?.takeIf { it.isNotBlank() },
            onset = locale(onset),
            expires = locale(ends ?: expires),
            areaDesc = areaDesc,
            source = senderName?.takeIf { it.isNotBlank() }?.let { "National Weather Service - $it" }
                ?: "National Weather Service",
            official = true,
            fonte = FonteAllerte.NWS,
        )
    }
}
