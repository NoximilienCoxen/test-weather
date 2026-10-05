package io.github.noximiliencoxen.caelum.data

import io.github.noximiliencoxen.caelum.lingua.inInglese
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Le allerte ufficiali di tutti gli altri paesi, dall'IFRC Alert Hub.
 *
 * L'Alert Hub della Federazione internazionale della Croce Rossa raccoglie i
 * feed CAP dei servizi registrati presso la WMO e li espone in un'API GraphQL
 * (`alerthub-api.ifrc.org/graphql/`, l'indirizzo l'ha trovato la sonda nel
 * codice del sito). E' la strada per i paesi che non hanno un lettore loro:
 * le fonti nazionali (MeteoAlarm, NWS, ECCC, JMA) restano davanti dove ci
 * sono, perche' sono piu' vicine all'ente.
 *
 * ## Com'e' fatta davvero l'API
 *
 * Letta da risposte vere della sonda `probe_allerte_mondo.py` del 5 ottobre
 * 2026 (copiate in `src/test/resources/ifrc-*.json`):
 *
 * - 196 paesi, 38 con allerte in quel momento, Russia compresa (570, dal
 *   feed CAP di Roshydromet su meteoinfo.ru). Ogni paese e ogni regione
 *   (`admin1s`) ha un riquadro `bbox` in GeoJSON: e' cosi' che si trova dove
 *   sta un posto, **senza confrontare nomi** in lingue diverse.
 * - `alerts(filters: {country: {pk}, admin1})` da' le allerte di una
 *   regione, con le `infos` CAP: evento, severita', inizio, fine, testo e
 *   aree con i poligoni "lat,lon lat,lon ...".
 * - **Una voce per lingua**: Roshydromet manda la stessa allerta in russo e
 *   in inglese come due allerte distinte, stessi orari e stesso poligono. Si
 *   uniscono e si tiene la lingua dell'app, o l'inglese, o la prima.
 * - Alcune allerte stanno nella regione "Unknown": si chiedono anche quelle,
 *   e decide il poligono.
 * - I riquadri si sovrappongono (vedi [tuttiQuelliCheContengono]): si
 *   chiedono fino a tre regioni candidate, in fino a due paesi.
 */
internal object IfrcAlerts {

    const val ENDPOINT = "https://alerthub-api.ifrc.org/graphql/"

    fun load(place: Place, fuso: ZoneOffset?): List<WeatherAlert> {
        val lat = place.latitude
        val lon = place.longitude
        val paesi = tuttiQuelliCheContengono(luoghi(domanda("{ public { allCountries { id iso3 bbox } } }"), "allCountries"), lat, lon)
        if (paesi.isEmpty()) throw WeatherAlertsRepository.OutOfCoverage(place.country)
        // Un paese vale se una delle sue regioni contiene il punto: il riquadro
        // della Russia copre mezza Asia, quello delle sue regioni no.
        val richieste = mutableListOf<Pair<String, String?>>() // paese, regione
        var primaRegione: String? = null
        for (paese in paesi.take(MAX_CANDIDATI)) {
            val regioni = luoghi(domanda("{ public { country(pk: \"${paese.id}\") { admin1s { id name bbox } } } }"), "admin1s")
            val candidate = tuttiQuelliCheContengono(regioni, lat, lon).take(MAX_CANDIDATI)
            if (candidate.isEmpty()) continue
            // Un'allerta senza poligono si attribuisce solo a una regione che
            // non ha rivali: con Tomsk, Kemerovo e Novosibirsk tutte candidate,
            // un'allerta di Kemerovo senza poligono finirebbe a Tomsk.
            if (primaRegione == null && candidate.size == 1) primaRegione = candidate.single().id
            candidate.forEach { richieste += paese.id to it.id }
            regioni.firstOrNull { it.nome.equals("Unknown", ignoreCase = true) }?.let { richieste += paese.id to it.id }
            if (richieste.map { it.first }.distinct().size >= 2) break
        }
        // Nessuna regione contiene il punto (riquadri imprecisi, regioni
        // mancanti): si chiede il paese piu' piccolo intero, e decidono i poligoni.
        if (richieste.isEmpty()) richieste += paesi.first().id to null
        val adesso = OffsetDateTime.now()
        return richieste.distinct().flatMap { (paese, regione) ->
            val corpo = domanda(allerte(paese, regione, limite = if (regione == null) 100 else 50))
            // Senza poligono, un'allerta vale solo per la regione senza rivali
            // (sopra), o per il paese intero quando nessuna regione e' candidata.
            parse(corpo, lat, lon, fuso, adesso, senzaPoligonoVale = regione == primaRegione || regione == null)
        }
            .distinctBy { it.id }
            .sortedByDescending { it.level.weight }
    }

    /** Quante candidate al massimo, per paesi e per regioni: oltre, sono riquadri che si toccano appena. */
    private const val MAX_CANDIDATI = 3

    private fun domanda(query: String): String =
        httpPostJson(ENDPOINT, buildJsonObject { put("query", query) }.toString(), fonte = "IFRC Alert Hub")

    private fun allerte(paese: String, regione: String?, limite: Int): String {
        val filtro = if (regione != null) "{country: {pk: \"$paese\"}, admin1: \"$regione\"}" else "{country: {pk: \"$paese\"}}"
        return "{ public { alerts(filters: $filtro, pagination: {offset: 0, limit: $limite}) { items { " +
            "id status msgType sender infos { language event severity onset expires senderName headline " +
            "description instruction areas { areaDesc polygons { value } } } } } } }"
    }

    /** Un paese o una regione col suo riquadro. */
    data class Luogo(val id: String, val nome: String, val ovest: Double, val sud: Double, val est: Double, val nord: Double) {
        fun contiene(lat: Double, lon: Double) = lat in sud..nord && lon in ovest..est
        val area: Double get() = (est - ovest) * (nord - sud)
    }

    /**
     * I luoghi il cui riquadro contiene il punto, dal piu' piccolo.
     *
     * Un riquadro non e' un confine: sul campione russo il punto di Tomsk cade
     * anche nel riquadro, piu' piccolo, della regione di Novosibirsk. Per
     * questo non se ne sceglie uno solo: si chiedono le candidate, e decide il
     * poligono di ciascuna allerta.
     */
    fun tuttiQuelliCheContengono(luoghi: List<Luogo>, lat: Double, lon: Double): List<Luogo> =
        luoghi.filter { it.contiene(lat, lon) }.sortedBy { it.area }

    /** I luoghi di una risposta, cercati per chiave ovunque stiano (paesi o regioni). */
    fun luoghi(body: String, chiave: String): List<Luogo> {
        val radice = json.parseToJsonElement(body)
        val elenco = trova(radice, chiave) as? JsonArray ?: return emptyList()
        return elenco.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val nome = (o["name"] ?: o["iso3"])?.jsonPrimitive?.content.orEmpty()
            val anello = (o["bbox"] as? JsonObject)?.get("coordinates")?.jsonArray?.firstOrNull()?.jsonArray
                ?: return@mapNotNull null
            val punti = anello.mapNotNull { p ->
                val c = p.jsonArray
                val lo = c.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                val la = c.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                if (lo != null && la != null) lo to la else null
            }
            if (punti.isEmpty()) return@mapNotNull null
            Luogo(id, nome, punti.minOf { it.first }, punti.minOf { it.second }, punti.maxOf { it.first }, punti.maxOf { it.second })
        }
    }

    private fun trova(e: JsonElement, chiave: String): JsonElement? = when (e) {
        is JsonObject -> e[chiave] ?: e.values.firstNotNullOfOrNull { trova(it, chiave) }
        is JsonArray -> e.firstNotNullOfOrNull { trova(it, chiave) }
        else -> null
    }

    /**
     * Le allerte di una risposta che riguardano il punto e sono in corso.
     *
     * Valgono solo i messaggi veri e non le revoche; con un poligono, il punto
     * deve starci dentro; senza, basta la regione da cui sono state chieste.
     */
    fun parse(
        body: String,
        lat: Double,
        lon: Double,
        fuso: ZoneOffset?,
        adesso: OffsetDateTime,
        senzaPoligonoVale: Boolean = true,
    ): List<WeatherAlert> {
        val voci = json.decodeFromString(IfrcRisposta.serializer(), body).data?.public?.alerts?.items.orEmpty()
        val lingua = if (inInglese()) "en" else "it"
        return voci
            .filter { it.status.equals("ACTUAL", ignoreCase = true) && !it.msgType.equals("CANCEL", ignoreCase = true) }
            .flatMap { voce -> voce.infos.map { voce to it } }
            .filter { (_, info) -> info.event != null }
            .filter { (_, info) ->
                val fine = info.expires?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
                fine == null || fine.isAfter(adesso)
            }
            .filter { (_, info) ->
                val poligoni = info.areas.flatMap { it.polygons }.mapNotNull { it.value }
                if (poligoni.isEmpty()) senzaPoligonoVale else poligoni.any { dentroPoligonoCap(it, lat, lon) != false }
            }
            // La stessa allerta in piu' lingue: stessi orari, stessa severita', stesso poligono.
            .groupBy { (_, info) ->
                listOf(info.onset, info.expires, info.severity, info.areas.firstOrNull()?.polygons?.firstOrNull()?.value)
            }
            .map { (_, stesse) ->
                stesse.firstOrNull { it.second.language?.startsWith(lingua) == true }
                    ?: stesse.firstOrNull { it.second.language?.startsWith("en") == true }
                    ?: stesse.first()
            }
            .map { (voce, info) -> info.toAlert(voce, fuso) }
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
}

@Serializable internal data class IfrcRisposta(val data: IfrcDati? = null)
@Serializable internal data class IfrcDati(val public: IfrcPubblico? = null)
@Serializable internal data class IfrcPubblico(val alerts: IfrcElenco? = null)
@Serializable internal data class IfrcElenco(val items: List<IfrcAllerta> = emptyList())

@Serializable
internal data class IfrcAllerta(
    val id: String,
    val status: String? = null,
    val msgType: String? = null,
    val sender: String? = null,
    val infos: List<IfrcInfo> = emptyList(),
)

@Serializable
internal data class IfrcInfo(
    val language: String? = null,
    val event: String? = null,
    val severity: String? = null,
    val onset: String? = null,
    val expires: String? = null,
    val senderName: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val areas: List<IfrcArea> = emptyList(),
) {
    val level: AlertLevel
        get() = when (severity?.uppercase()) {
            "EXTREME" -> AlertLevel.ROSSA
            "SEVERE" -> AlertLevel.ARANCIONE
            else -> AlertLevel.GIALLA
        }

    fun toAlert(voce: IfrcAllerta, fuso: ZoneOffset?): WeatherAlert {
        fun locale(testo: String?): LocalDateTime? {
            val istante = testo?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
            return if (fuso != null) {
                istante.withOffsetSameInstant(fuso).toLocalDateTime()
            } else {
                istante.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
        }
        val tipo = tipoDaEvento(event)
        val ente = senderName?.takeIf { it.isNotBlank() } ?: voce.sender?.takeIf { it.isNotBlank() }
        return WeatherAlert(
            id = "ifrc|${voce.id}",
            level = level,
            kind = tipo,
            headline = "${level.label}: ${tipo.label.lowercase()}",
            // Il nome dell'evento come lo scrive l'ente, in testa.
            description = listOfNotNull(event, description?.takeIf { it.isNotBlank() && it != event })
                .joinToString("\n\n"),
            instruction = instruction?.takeIf { it.isNotBlank() },
            onset = locale(onset),
            expires = locale(expires),
            areaDesc = areas.mapNotNull { it.areaDesc }.distinct().joinToString(", ").ifBlank { null },
            source = ente?.let { "IFRC Alert Hub - $it" } ?: "IFRC Alert Hub",
            official = true,
            fonte = FonteAllerte.IFRC,
        )
    }
}

@Serializable internal data class IfrcArea(val areaDesc: String? = null, val polygons: List<IfrcPoligono> = emptyList())
@Serializable internal data class IfrcPoligono(val value: String? = null)
