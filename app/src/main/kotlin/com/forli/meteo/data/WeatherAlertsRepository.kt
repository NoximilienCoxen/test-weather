package com.forli.meteo.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Le allerte ufficiali, da MeteoAlarm.
 *
 * **Perche' MeteoAlarm e non l'Aeronautica Militare.** Il servizio meteo
 * dell'Aeronautica pubblica bollettini su meteoam.it, ma non espone un'API
 * pubblica documentata per gli avvisi: i dati si ottengono per accordo, non
 * con una GET. MeteoAlarm invece e' il canale di EUMETNET su cui i servizi
 * nazionali pubblicano i propri avvisi in formato CAP, ed e' li' che
 * finiscono anche quelli italiani del Dipartimento della Protezione Civile e
 * dei centri funzionali regionali. E' la stessa informazione, per una via che
 * si puo' leggere.
 *
 * **Perche' l'Atom e non l'RSS.** Gli RSS legacy sono stati spenti il
 * 14 gennaio 2026. L'Atom porta gli stessi dati ed e' quello mantenuto.
 *
 * Nessuna libreria XML: `Xml.newPullParser()` e' nella piattaforma da sempre,
 * e il progetto non ha nemmeno una libreria di rete - aggiungerne una per
 * leggere una dozzina di elementi sarebbe fuori scala.
 *
 * Come `AirQualityRepository`, questo e' un **arricchimento**: sta su un altro
 * host, arriva dopo, e se non arriva la schermata funziona lo stesso.
 */
class WeatherAlertsRepository(private val place: Place) {

    suspend fun load(): Result<List<WeatherAlert>> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = countrySlug(place.country)
                // Fuori dai paesi che MeteoAlarm copre non c'e' un feed da
                // interrogare, e non e' un errore: si dichiara e basta, cosi'
                // chi chiama sa che deve cavarsela con le soglie.
                ?: throw OutOfCoverage(place.country)
            val body = httpGet("$FEED_ENDPOINT$slug")
            val now = OffsetDateTime.now()
            parseAtom(body)
                .filter { it.isCurrent(now) }
                .filter { it.isWorthShowing() }
                .filter { it.matches(place) }
                .map { it.toAlert() }
        }
    }

    /** Il posto non e' fra quelli che MeteoAlarm serve. */
    class OutOfCoverage(val country: String?) :
        Exception("MeteoAlarm non copre " + (country ?: "questa localita'"))

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/atom+xml, application/xml")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code da MeteoAlarm: ${text.take(200)}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FEED_ENDPOINT = "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-"

        /**
         * Il nome del paese come lo scrive l'indirizzo del feed.
         *
         * MeteoAlarm non usa i codici ISO nell'URL ma il nome inglese in
         * minuscolo, con i trattini al posto degli spazi. La mappa parte dai
         * nomi italiani perche' e' come `Place.country` arriva dalla
         * geocodifica di Open-Meteo, interrogata con `language=it`.
         *
         * Un paese che non c'e' torna nullo e non tira a indovinare: uno slug
         * inventato darebbe un 404, cioe' un guasto, dove la verita' e'
         * semplicemente "qui non c'e' copertura".
         */
        fun countrySlug(country: String?): String? {
            val key = country?.trim()?.lowercase() ?: return null
            return COUNTRIES[key]
        }

        private val COUNTRIES: Map<String, String> = mapOf(
            "italia" to "italy", "italy" to "italy",
            "austria" to "austria",
            "belgio" to "belgium", "belgium" to "belgium",
            "bosnia ed erzegovina" to "bosnia-herzegovina",
            "bulgaria" to "bulgaria",
            "cipro" to "cyprus", "cyprus" to "cyprus",
            "croazia" to "croatia", "croatia" to "croatia",
            "danimarca" to "denmark", "denmark" to "denmark",
            "estonia" to "estonia",
            "finlandia" to "finland", "finland" to "finland",
            "francia" to "france", "france" to "france",
            "germania" to "germany", "germany" to "germany",
            "grecia" to "greece", "greece" to "greece",
            "irlanda" to "ireland", "ireland" to "ireland",
            "islanda" to "iceland", "iceland" to "iceland",
            "lettonia" to "latvia", "latvia" to "latvia",
            "lituania" to "lithuania", "lithuania" to "lithuania",
            "lussemburgo" to "luxembourg", "luxembourg" to "luxembourg",
            "macedonia del nord" to "north-macedonia",
            "malta" to "malta",
            "moldavia" to "moldova", "moldova" to "moldova",
            "montenegro" to "montenegro",
            "norvegia" to "norway", "norway" to "norway",
            "paesi bassi" to "netherlands", "netherlands" to "netherlands",
            "polonia" to "poland", "poland" to "poland",
            "portogallo" to "portugal", "portugal" to "portugal",
            "regno unito" to "united-kingdom", "united kingdom" to "united-kingdom",
            "repubblica ceca" to "czechia", "czechia" to "czechia",
            "romania" to "romania",
            "serbia" to "serbia",
            "slovacchia" to "slovakia", "slovakia" to "slovakia",
            "slovenia" to "slovenia",
            "spagna" to "spain", "spain" to "spain",
            "svezia" to "sweden", "sweden" to "sweden",
            "svizzera" to "switzerland", "switzerland" to "switzerland",
            "ungheria" to "hungary", "hungary" to "hungary",
        )
    }
}

// ---------------------------------------------------------------------------
// Lettura del feed
// ---------------------------------------------------------------------------

/**
 * Una voce del feed, ancora grezza.
 *
 * Si tiene separata dal modello di dominio perche' qui ci sono i codici della
 * fonte - `awareness_level`, `awareness_type`, il poligono come stringa - e
 * quelli non devono uscire da questo file.
 */
internal data class AtomEntry(
    val id: String,
    val awarenessLevel: Int?,
    val awarenessType: Int?,
    val headline: String?,
    val description: String?,
    val instruction: String?,
    // Con il fuso, non senza: un avviso che scade alle 18 di Lisbona non scade
    // alle 18 di Forli', e per sapere se e' ancora valido serve l'istante. Il
    // fuso si perde solo al momento di scriverlo a schermo.
    val onset: OffsetDateTime?,
    val expires: OffsetDateTime?,
    val areaDesc: String?,
    val polygons: List<List<Pair<Double, Double>>>,
    val sender: String?,
) {
    /**
     * Se questo avviso riguarda il posto che si sta guardando.
     *
     * Il poligono ha la precedenza su tutto: e' la geometria vera dell'area,
     * e dice si' o no senza interpretare nomi. Quando manca si ripiega sul
     * nome dell'area confrontato con la regione della localita'.
     *
     * **Senza ne' l'uno ne' l'altro l'avviso si tiene.** E' la scelta meno
     * ovvia del file e vale la pena dirla: scartare cio' che non si sa
     * collocare significa, per un'allerta, non darla. Il nome dell'area viaggia
     * fino alla schermata e ci si legge sopra, quindi chi guarda puo' decidere
     * da se' se lo riguarda - cosa che non puo' fare se l'avviso non compare.
     */
    /**
     * Un avviso scaduto non e' un avviso.
     *
     * Il feed tiene in scena le voci per un po' dopo la fine, e mostrarle
     * vorrebbe dire annunciare un temporale finito ieri sera.
     */
    fun isCurrent(now: OffsetDateTime): Boolean = expires == null || expires.isAfter(now)

    /**
     * Il verde non si mostra.
     *
     * `awareness_level` 1 vuol dire "nessun avviso", ed e' la maggior parte
     * delle voci di un feed nazionale in una giornata normale. Una fascia che
     * comparisse per dire che non succede niente insegnerebbe a ignorarla.
     *
     * Un livello **mancante** invece si tiene: e' un avviso che qualcuno ha
     * emesso senza classificarlo, e scartarlo sarebbe peggio che mostrarlo al
     * gradino piu' basso.
     */
    fun isWorthShowing(): Boolean = awarenessLevel == null || awarenessLevel >= 2

    fun matches(place: Place): Boolean {
        if (polygons.isNotEmpty()) {
            return polygons.any { it.containsPoint(place.latitude, place.longitude) }
        }
        val area = areaDesc?.lowercase() ?: return true
        val admin = place.admin?.lowercase()
        val name = place.name.lowercase()
        return admin?.let { area.contains(it) || it.contains(area) } == true ||
            area.contains(name)
    }

    fun toAlert(): WeatherAlert {
        val kind = AlertKind.ofAwareness(awarenessType)
        val level = AlertLevel.ofAwareness(awarenessLevel) ?: AlertLevel.GIALLA
        return WeatherAlert(
            id = id,
            level = level,
            kind = kind,
            // Il titolo della fonte quando c'e': e' scritto da chi l'avviso
            // l'ha emesso. Solo se manca si compone qualcosa dai codici.
            headline = headline?.takeIf { it.isNotBlank() }
                ?: "${level.label}: ${kind.label.lowercase()}",
            description = description?.takeIf { it.isNotBlank() },
            instruction = instruction?.takeIf { it.isNotBlank() },
            onset = onset?.toLocalDateTime(),
            expires = expires?.toLocalDateTime(),
            areaDesc = areaDesc,
            source = sender?.takeIf { it.isNotBlank() }?.let { "MeteoAlarm - $it" }
                ?: "MeteoAlarm",
            official = true,
        )
    }
}

/**
 * Legge il feed Atom e ne cava le voci CAP.
 *
 * Il parser guarda i **nomi locali** degli elementi e ignora i prefissi di
 * spazio dei nomi: nel feed convivono `cap:`, `atom:` e a volte nessuno dei
 * due, e inseguire i prefissi sarebbe un modo per rompersi al primo che cambia.
 *
 * I parametri CAP sono coppie `valueName`/`value` dentro un `parameter`, non
 * elementi con un nome proprio: `awareness_level` e' il **contenuto** di un
 * `valueName`, e il numero che interessa sta nel `value` accanto. Per questo
 * si tiene traccia dell'ultimo nome letto.
 */
internal fun parseAtom(xml: String): List<AtomEntry> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(StringReader(xml))

    val entries = mutableListOf<AtomEntry>()
    var id = ""
    var level: Int? = null
    var type: Int? = null
    var headline: String? = null
    var description: String? = null
    var instruction: String? = null
    var onset: OffsetDateTime? = null
    var expires: OffsetDateTime? = null
    var area: String? = null
    var sender: String? = null
    var polygons = mutableListOf<List<Pair<Double, Double>>>()
    var lastValueName: String? = null

    fun reset() {
        id = ""
        level = null
        type = null
        headline = null
        description = null
        instruction = null
        onset = null
        expires = null
        area = null
        sender = null
        polygons = mutableListOf()
        lastValueName = null
    }

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        // Mai nullo: cosi' i confronti con gli insiemi restano fra String,
        // senza passare per la `contains` estesa sui nullable.
        val name = parser.name?.substringAfter(':').orEmpty()
        when (event) {
            XmlPullParser.START_TAG -> when {
                name in RECORD_TAGS -> reset()
                // **Solo le foglie passano da `nextText()`.** Chiamarla su un
                // elemento che ha figli - `area` contiene `areaDesc` e
                // `polygon`, `parameter` contiene `valueName` e `value` -
                // solleva, e avrebbe fatto fallire la lettura dell'intero feed
                // sulla prima voce con un poligono, cioe' su quasi tutte.
                name in LEAF_TAGS -> {
                    val text = runCatching { parser.nextText() }.getOrNull()?.trim().orEmpty()
                    when (name) {
                        "id", "identifier" -> if (id.isEmpty()) id = text
                        "headline" -> headline = text
                        "description" -> description = text
                        "instruction" -> instruction = text
                        "areaDesc" -> area = text
                        "senderName" -> sender = text
                        "onset", "effective" -> if (onset == null) onset = parseTime(text)
                        "expires" -> expires = parseTime(text)
                        "polygon" -> parsePolygon(text)?.let { polygons += it }
                        "valueName" -> lastValueName = text
                        "value" -> {
                            when (lastValueName) {
                                "awareness_level" -> level = leadingInt(text)
                                "awareness_type" -> type = leadingInt(text)
                            }
                            lastValueName = null
                        }
                    }
                }
            }

            XmlPullParser.END_TAG -> if (name in RECORD_TAGS && id.isNotEmpty()) {
                entries += AtomEntry(
                    id = id,
                    awarenessLevel = level,
                    awarenessType = type,
                    headline = headline,
                    description = description,
                    instruction = instruction,
                    onset = onset,
                    expires = expires,
                    areaDesc = area,
                    polygons = polygons.toList(),
                    sender = sender,
                )
                reset()
            }
        }
        event = parser.next()
    }
    return entries
}

/**
 * Dove comincia e finisce una voce.
 *
 * Due nomi e non uno: nel feed Atom ogni avviso e' un `entry`, ma i documenti
 * CAP serviti a parte usano `alert`. Riconoscerli entrambi costa una voce in
 * un insieme e toglie di mezzo un intero secondo lettore.
 */
private val RECORD_TAGS = setOf("entry", "alert")

/** Gli elementi che contengono testo e nient'altro. */
private val LEAF_TAGS = setOf(
    "id", "identifier", "headline", "description", "instruction",
    "areaDesc", "senderName", "onset", "effective", "expires",
    "polygon", "valueName", "value",
)

/**
 * Il primo numero dentro una stringa come "3; orange" oppure "2".
 *
 * MeteoAlarm scrive il livello in modi diversi a seconda di chi lo pubblica, e
 * un `toInt()` secco fallirebbe su meta' dei paesi.
 */
private fun leadingInt(text: String): Int? =
    Regex("\\d+").find(text)?.value?.toIntOrNull()

/**
 * Le date del CAP sono ISO-8601 con fuso ("2026-09-03T15:00:00+02:00").
 *
 * Si tiene con il suo fuso: serve a sapere se l'avviso e' ancora valido, e
 * quel confronto va fatto sull'istante, non sui numeri dell'orologio.
 */
private fun parseTime(text: String): OffsetDateTime? = runCatching {
    OffsetDateTime.parse(text)
}.getOrNull() ?: runCatching {
    // Qualche emittente scrive l'ora senza fuso. Si assume quello del
    // telefono: e' un'ipotesi, ma e' l'unica disponibile, e sbagliare di
    // un'ora la scadenza e' meglio che buttare l'avviso.
    LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime()
}.getOrNull()

/**
 * Un poligono CAP: coppie "lat,lon" separate da spazi, primo punto ripetuto in
 * fondo a chiudere l'anello.
 */
private fun parsePolygon(text: String): List<Pair<Double, Double>>? {
    val points = text.trim().split(Regex("\\s+")).mapNotNull { pair ->
        val parts = pair.split(',')
        if (parts.size < 2) return@mapNotNull null
        val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        lat to lon
    }
    return points.takeIf { it.size >= 3 }
}

/**
 * Punto dentro poligono, con il lancio di raggio.
 *
 * Si conta quante volte una semiretta orizzontale uscente dal punto attraversa
 * i lati: dispari dentro, pari fuori. Va bene per aree meteo, che sono
 * poligoni semplici di qualche decina di vertici, e non serve niente di piu'
 * dotto per rispondere a "questo avviso mi riguarda".
 */
internal fun List<Pair<Double, Double>>.containsPoint(lat: Double, lon: Double): Boolean {
    var inside = false
    var j = lastIndex
    for (i in indices) {
        val (latI, lonI) = this[i]
        val (latJ, lonJ) = this[j]
        if ((lonI > lon) != (lonJ > lon)) {
            val crossLat = latI + (lon - lonI) / (lonJ - lonI) * (latJ - latI)
            if (lat < crossLat) inside = !inside
        }
        j = i
    }
    return inside
}
