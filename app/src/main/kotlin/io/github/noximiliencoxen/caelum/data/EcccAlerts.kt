package io.github.noximiliencoxen.caelum.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Le allerte ufficiali del Canada, da Environment and Climate Change Canada.
 *
 * L'API OGC di GeoMet (`api.weather.gc.ca/collections/weather-alerts/items`)
 * restituisce GeoJSON con un poligono per zona, e accetta due filtri che qui
 * fanno tutto il lavoro: `status_en=active` e un `bbox` di qualche centinaio
 * di metri attorno al posto, cioe' le zone che lo toccano. Senza chiave;
 * licenza Open Government Licence - Canada.
 *
 * ## Com'e' fatta davvero la risposta
 *
 * Letta da una risposta vera della sonda `probe_allerte_mondo.py` (5 ottobre
 * 2026, tre "special weather statement" in Quebec, copiata in
 * `src/test/resources/eccc-quebec.json`). Quel giorno nessuna allerta era
 * attiva in tutto il Canada (`status_en=active` dava zero voci), quindi i
 * campi si conoscono da voci gia' concluse (`status_en` "ended"):
 *
 * - `alert_type`: warning, watch, advisory, statement; `alert_name_en` per
 *   esteso ("special weather statement"); `alert_text_en` anche vuoto.
 * - `risk_colour_en`: il colore, quando l'avviso ne ha uno (nel campione e'
 *   nullo). Senza, decide `alert_type`: warning arancione, il resto gialla.
 * - `event_end_datetime` e' la fine dell'evento, `expiration_datetime` quella
 *   del messaggio (un'ora dopo la pubblicazione, nel campione).
 *   `validity_datetime` cade fra pubblicazione e fine evento, e si legge come
 *   inizio: **e' un'interpretazione**, la prima allerta attiva vera dira' se
 *   regge.
 */
internal object EcccAlerts {

    const val ENDPOINT = "https://api.weather.gc.ca/collections/weather-alerts/items"

    const val ACCEPT = "application/geo+json, application/json;q=0.9, */*;q=0.8"

    /** Mezzo centesimo di grado per lato: qualche centinaio di metri. */
    private const val MEZZO_LATO = 0.005

    fun indirizzo(place: Place): String = ENDPOINT + "?f=json&status_en=active&bbox=" +
        "%.4f,%.4f,%.4f,%.4f".format(
            Locale.ROOT,
            place.longitude - MEZZO_LATO, place.latitude - MEZZO_LATO,
            place.longitude + MEZZO_LATO, place.latitude + MEZZO_LATO,
        )

    fun load(place: Place, fuso: ZoneOffset?): List<WeatherAlert> =
        parse(httpGet(indirizzo(place), fonte = "Environment Canada", accept = ACCEPT), fuso)

    /**
     * Le allerte di una risposta, una per avviso.
     *
     * Le concluse si scartano anche qui, oltre che col filtro della richiesta:
     * un filtro lato server che un giorno cambiasse nome tornerebbe tutto, e
     * un'allerta finita mostrata come in corso e' un avviso dove non c'e'.
     * Lo stesso avviso copre piu' zone: vale una volta.
     */
    fun parse(body: String, fuso: ZoneOffset?): List<WeatherAlert> =
        json.decodeFromString(EcccRisposta.serializer(), body).features
            .map { it.properties }
            .filterNot { it.status_en.equals("ended", ignoreCase = true) }
            .filter { it.alert_name_en != null }
            .distinctBy { Triple(it.alert_code, it.alert_name_en, it.event_end_datetime) }
            .map { it.toAlert(fuso) }
            .sortedByDescending { it.level.weight }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
}

@Serializable
internal data class EcccRisposta(val features: List<EcccVoce> = emptyList())

@Serializable
internal data class EcccVoce(val properties: EcccAllerta)

@Suppress("PropertyName") // i nomi sono quelli del servizio
@Serializable
internal data class EcccAllerta(
    val alert_code: String? = null,
    val alert_type: String? = null,
    val alert_name_en: String? = null,
    val publication_datetime: String? = null,
    val expiration_datetime: String? = null,
    val validity_datetime: String? = null,
    val event_end_datetime: String? = null,
    val alert_text_en: String? = null,
    val risk_colour_en: String? = null,
    val feature_name_en: String? = null,
    val province: String? = null,
    val status_en: String? = null,
    val feature_id: String? = null,
) {
    val level: AlertLevel
        get() = when (risk_colour_en?.lowercase()) {
            "red" -> AlertLevel.ROSSA
            "orange" -> AlertLevel.ARANCIONE
            "yellow" -> AlertLevel.GIALLA
            else -> if (alert_type.equals("warning", ignoreCase = true)) AlertLevel.ARANCIONE else AlertLevel.GIALLA
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
        val tipo = tipoDaEvento(alert_name_en)
        val nome = alert_name_en.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) }
        return WeatherAlert(
            id = listOfNotNull(alert_code, feature_id, event_end_datetime).joinToString("|"),
            level = level,
            kind = tipo,
            headline = "${level.label}: ${tipo.label.lowercase()}",
            // Il nome di ECCC in testa, come per il NWS: e' cio' che si
            // ritrova sui canali ufficiali.
            description = listOfNotNull(nome.takeIf { it.isNotBlank() }, alert_text_en?.takeIf { it.isNotBlank() })
                .joinToString("\n\n"),
            onset = locale(validity_datetime),
            expires = locale(event_end_datetime ?: expiration_datetime),
            areaDesc = listOfNotNull(feature_name_en, province).joinToString(", ").ifBlank { null },
            source = "Environment and Climate Change Canada",
            official = true,
            fonte = FonteAllerte.ECCC,
        )
    }
}
