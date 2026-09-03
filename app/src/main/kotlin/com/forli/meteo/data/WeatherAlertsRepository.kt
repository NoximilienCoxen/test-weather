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
 * nazionali pubblicano i propri avvisi, ed e' li' che finiscono anche quelli
 * italiani della Protezione Civile e dei centri funzionali regionali. Stessa
 * informazione, per una via che si puo' leggere.
 *
 * **Perche' l'Atom e non l'RSS.** Gli RSS legacy sono stati spenti il
 * 14 gennaio 2026. L'Atom porta gli stessi dati ed e' quello mantenuto.
 *
 * ## Com'e' fatto davvero il feed
 *
 * Questa parte non e' stata dedotta: e' stata **letta** da una risposta vera,
 * catturata dal passo `probe-api` e finita in `ci-artifacts/api/allerte.xml`.
 * La prima stesura di questo file si fidava di `awareness_level` e
 * `awareness_type`, che nel feed **non esistono** - zero occorrenze su
 * trentacinquemila byte - e avrebbe mostrato ogni allerta come una gialla
 * generica senza che nessuno se ne accorgesse.
 *
 * Una voce vera:
 * ```
 * <entry>
 *   <cap:geocode><valueName>EMMA_ID</valueName><value>IT017</value></cap:geocode>
 *   <cap:areaDesc>Basilicata</cap:areaDesc>
 *   <cap:event>Yellow High-temperature Warning</cap:event>
 *   <cap:expires>2026-09-04T17:59:00+00:00</cap:expires>
 *   <cap:onset>2026-09-04T12:00:00+00:00</cap:onset>
 *   <cap:severity>Moderate</cap:severity>
 *   <link type="application/cap+xml" href="...">
 * </entry>
 * ```
 *
 * Da qui tre conseguenze che governano tutto il file:
 *
 * 1. **Il colore sta scritto nel testo di `cap:event`**, in inglese, e la
 *    `cap:severity` accanto e' piu' grossolana - nella cattura tutte e
 *    ventitre le voci erano `Moderate`. Si legge il colore, e la severita'
 *    resta come ripiego.
 * 2. **Non c'e' nessun poligono**: l'area e' un nome di regione. Il confronto
 *    dei nomi non e' un ripiego, e' la via principale.
 * 3. **Non ci sono ne' descrizione ne' raccomandazioni** nell'Atom: stanno nel
 *    documento CAP collegato, che si va a prendere **solo per le poche voci
 *    che riguardano la localita' mostrata**.
 *
 * Nessuna libreria XML: `Xml.newPullParser()` e' nella piattaforma, e il
 * progetto non ha nemmeno una libreria di rete.
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
            val body = httpGet(FEED_ENDPOINT + slug)
            val now = OffsetDateTime.now()
            val mine = parseFeed(body)
                .filter { it.isCurrent(now) }
                .filter { it.level != null }
                .filter { it.matches(place) }
                // Piu' di tre avvisi contemporanei sulla stessa regione non si
                // sono mai visti, e il tetto e' li' per non trasformare una
                // giornata storta in una raffica di richieste.
                .take(MAX_DETAILS)
            mine.map { entry -> entry.toAlert(detail = fetchDetail(entry.capUrl)) }
        }
    }

    /**
     * Il documento CAP di una singola voce: e' li' che stanno le parole.
     *
     * Best-effort per scelta: se non arriva, l'allerta si mostra lo stesso con
     * quello che l'Atom gia' diceva - colore, tipo, area e finestra oraria.
     * Un avviso senza il testo per esteso resta un avviso; un avviso non dato
     * per colpa di una seconda richiesta fallita, no.
     */
    private fun fetchDetail(url: String?): CapDetail? {
        if (url == null) return null
        return runCatching { parseDetail(httpGet(url)) }.getOrNull()
    }

    /** Il posto non e' fra quelli che MeteoAlarm serve. */
    class OutOfCoverage(val country: String?) :
        Exception("MeteoAlarm non copre " + (country ?: "questa localita'"))

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/atom+xml, application/cap+xml, application/xml")
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

        /** Quante voci al massimo meritano una seconda richiesta per il testo. */
        private const val MAX_DETAILS = 3

        /**
         * Il nome del paese come lo scrive l'indirizzo del feed.
         *
         * MeteoAlarm non usa i codici ISO nell'URL ma il nome inglese in
         * minuscolo, coi trattini al posto degli spazi. La mappa parte dai nomi
         * italiani perche' e' cosi' che `Place.country` arriva dalla
         * geocodifica di Open-Meteo, interrogata con `language=it`.
         *
         * Un paese che non c'e' torna nullo e non tira a indovinare: uno slug
         * inventato darebbe un 404, cioe' un guasto, dove la verita' e'
         * semplicemente "qui non c'e' copertura".
         */
        fun countrySlug(country: String?): String? =
            COUNTRIES[country?.trim()?.lowercase() ?: return null]

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
// Le voci del feed
// ---------------------------------------------------------------------------

/** Una voce dell'Atom, ancora con le parole della fonte. */
internal data class FeedEntry(
    val id: String,
    val event: String?,
    val severity: String?,
    val areaDesc: String?,
    val onset: OffsetDateTime?,
    // Con il fuso, non senza: un avviso che scade alle 18 di Lisbona non scade
    // alle 18 di Forli', e per sapere se e' ancora valido serve l'istante.
    val expires: OffsetDateTime?,
    val capUrl: String?,
) {
    /**
     * Il colore, letto dal testo di `cap:event` e non dalla severita'.
     *
     * `cap:event` dice "Yellow High-temperature Warning": il colore e' la prima
     * parola. `cap:severity` accanto e' piu' grossolana - nella cattura tutte
     * e ventitre le voci dicevano `Moderate`, comprese quelle che il testo
     * chiamava gialle - quindi serve solo da ripiego quando il colore manca.
     *
     * Nullo per il verde, che vuol dire "nessun avviso": una fascia che
     * comparisse per dire che non succede niente insegnerebbe a ignorarla.
     */
    val level: AlertLevel?
        get() {
            val text = event?.lowercase().orEmpty()
            return when {
                text.contains("red") -> AlertLevel.ROSSA
                text.contains("orange") -> AlertLevel.ARANCIONE
                text.contains("yellow") -> AlertLevel.GIALLA
                text.contains("green") -> null
                else -> when (severity?.lowercase()) {
                    "extreme" -> AlertLevel.ROSSA
                    "severe" -> AlertLevel.ARANCIONE
                    "moderate" -> AlertLevel.GIALLA
                    else -> null
                }
            }
        }

    /**
     * Il fenomeno, dalle parole inglesi di `cap:event`.
     *
     * Le voci sono quelle del vocabolario di MeteoAlarm. Cio' che non si
     * riconosce diventa [AlertKind.ALTRO] e **si mostra lo stesso**: un avviso
     * di cui non si sa il tipo resta un avviso, e buttarlo sarebbe il modo
     * peggiore di trattare l'ignoto.
     */
    val kind: AlertKind
        get() {
            val t = event?.lowercase().orEmpty()
            return when {
                t.contains("thunder") -> AlertKind.TEMPORALI
                t.contains("high-temperature") || t.contains("heat") -> AlertKind.CALDO
                t.contains("low-temperature") || t.contains("cold") -> AlertKind.FREDDO
                t.contains("wind") -> AlertKind.VENTO
                t.contains("snow") || t.contains("ice") -> AlertKind.NEVE_GHIACCIO
                t.contains("fog") -> AlertKind.NEBBIA
                t.contains("coastal") -> AlertKind.COSTIERO
                t.contains("forest") || t.contains("fire") -> AlertKind.INCENDI
                t.contains("avalanche") -> AlertKind.VALANGHE
                t.contains("flood") || t.contains("rain") -> AlertKind.PIOGGIA
                else -> AlertKind.ALTRO
            }
        }

    /** Un avviso scaduto non e' un avviso: il feed li tiene in scena un po' dopo la fine. */
    fun isCurrent(now: OffsetDateTime): Boolean = expires == null || expires.isAfter(now)

    /**
     * Se questo avviso riguarda il posto che si sta guardando.
     *
     * **Il confronto e' per parole, non per sottostringa**, e non e'
     * pignoleria: nel feed vero l'area di Forli' si chiama "Emilia e Romagna"
     * mentre Open-Meteo chiama la stessa regione "Emilia-Romagna". Nessuna
     * delle due contiene l'altra, e un `contains` avrebbe lasciato Forli'
     * **senza nessuna allerta, per sempre, in silenzio**. Ridotte a insiemi di
     * parole significative diventano lo stesso insieme.
     *
     * Senza un'area riconoscibile l'avviso si tiene: il nome dell'area viaggia
     * fino alla schermata e ci si legge sopra, quindi chi guarda puo' decidere
     * da se'. Un'allerta scartata per prudenza e' un'allerta non data.
     */
    fun matches(place: Place): Boolean {
        val area = significantWords(areaDesc) ?: return true
        val admin = significantWords(place.admin)
        if (admin != null && (area.containsAll(admin) || admin.containsAll(area))) return true
        val name = significantWords(place.name)
        return name != null && area.containsAll(name)
    }

    fun toAlert(detail: CapDetail?): WeatherAlert {
        val chosen = level ?: AlertLevel.GIALLA
        return WeatherAlert(
            id = id,
            level = chosen,
            kind = kind,
            // **Composto in italiano, non copiato.** Il feed scrive "Yellow
            // High-temperature Warning": mettere quella riga in cima a una
            // schermata italiana sarebbe la traduzione mancante piu' visibile
            // dell'app. Il titolo del documento CAP si usa solo se e' gia'
            // nella lingua giusta, cosa che qui non si puo' dare per scontata.
            headline = "${chosen.label}: ${kind.label.lowercase()}",
            description = detail?.description,
            instruction = detail?.instruction,
            onset = onset?.toLocalDateTime(),
            expires = expires?.toLocalDateTime(),
            areaDesc = areaDesc,
            source = detail?.sender?.takeIf { it.isNotBlank() }?.let { "MeteoAlarm - $it" }
                ?: "MeteoAlarm",
            official = true,
        )
    }
}

/** Quel poco che il documento CAP aggiunge, e che l'Atom non ha. */
internal data class CapDetail(
    val description: String?,
    val instruction: String?,
    val sender: String?,
)

/**
 * Le parole che contano di un nome di luogo.
 *
 * Minuscole, senza punteggiatura, e senza le parole corte: gli articoli e le
 * congiunzioni sono esattamente cio' che differisce fra "Emilia e Romagna" e
 * "Emilia-Romagna", e sono anche cio' che non porta significato.
 */
private fun significantWords(text: String?): Set<String>? {
    val words = text?.lowercase()
        ?.split(Regex("[^\\p{L}\\p{N}]+"))
        ?.filter { it.length >= 3 }
        ?.toSet()
        ?: return null
    return words.ifEmpty { null }
}

// ---------------------------------------------------------------------------
// Lettura dell'XML
// ---------------------------------------------------------------------------

/**
 * Legge il feed Atom.
 *
 * Il parser guarda i **nomi locali** e ignora i prefissi di spazio dei nomi:
 * nel feed convivono `cap:` e l'Atom senza prefisso, e inseguire i prefissi
 * sarebbe un modo per rompersi al primo che cambia.
 *
 * `nextText()` si chiama **solo sulle foglie**: su un elemento con figli -
 * `cap:geocode` contiene `valueName` e `value`, `author` contiene `name` e
 * `uri` - solleva, e avrebbe fatto fallire la lettura dell'intero feed.
 */
internal fun parseFeed(xml: String): List<FeedEntry> {
    val entries = mutableListOf<FeedEntry>()
    var id = ""
    var event: String? = null
    var severity: String? = null
    var area: String? = null
    var onset: OffsetDateTime? = null
    var expires: OffsetDateTime? = null
    var capUrl: String? = null

    fun reset() {
        id = ""; event = null; severity = null; area = null
        onset = null; expires = null; capUrl = null
    }

    forEachTag(xml) { name, parser ->
        when {
            name == "entry" && parser == null -> {
                // Chiusura: la voce e' completa.
                if (id.isNotEmpty()) {
                    entries += FeedEntry(id, event, severity, area, onset, expires, capUrl)
                }
                reset()
            }
            name == "entry" -> reset()
            // Il collegamento al documento CAP e' un attributo, non testo: si
            // legge prima di chiedere il contenuto dell'elemento.
            name == "link" && parser != null -> {
                val type = parser.getAttributeValue(null, "type")
                if (type?.contains("cap") == true) {
                    capUrl = parser.getAttributeValue(null, "href")
                }
            }
            parser != null && name in FEED_LEAVES -> {
                val text = runCatching { parser.nextText() }.getOrNull()?.trim().orEmpty()
                when (name) {
                    "id", "identifier" -> if (id.isEmpty()) id = text
                    "event" -> event = text
                    "severity" -> severity = text
                    "areaDesc" -> area = text
                    "onset", "effective" -> if (onset == null) onset = parseTime(text)
                    "expires" -> expires = parseTime(text)
                }
            }
        }
    }
    return entries
}

/** Le foglie dell'Atom che portano qualcosa di utile. */
private val FEED_LEAVES = setOf(
    "id", "identifier", "event", "severity", "areaDesc",
    "onset", "effective", "expires",
)

/**
 * Legge un documento CAP singolo: solo le tre cose che l'Atom non dava.
 *
 * Il primo `info` vince. I feed nazionali ne pubblicano spesso due, uno per
 * lingua, e concatenarli darebbe lo stesso testo scritto due volte.
 */
internal fun parseDetail(xml: String): CapDetail {
    var description: String? = null
    var instruction: String? = null
    var sender: String? = null
    forEachTag(xml) { name, parser ->
        if (parser != null && name in DETAIL_LEAVES) {
            val text = runCatching { parser.nextText() }.getOrNull()?.trim().orEmpty()
            when (name) {
                "description" -> if (description.isNullOrBlank()) description = text
                "instruction" -> if (instruction.isNullOrBlank()) instruction = text
                "senderName" -> if (sender.isNullOrBlank()) sender = text
            }
        }
    }
    return CapDetail(
        description = description?.takeIf { it.isNotBlank() },
        instruction = instruction?.takeIf { it.isNotBlank() },
        sender = sender?.takeIf { it.isNotBlank() },
    )
}

private val DETAIL_LEAVES = setOf("description", "instruction", "senderName")

/**
 * Percorre l'XML e chiama [onTag] a ogni apertura e chiusura di elemento.
 *
 * Il parser passato e' non nullo in apertura - cosi' chi ascolta puo' leggere
 * attributi o chiedere il testo - e nullo in chiusura. Un solo attraversamento
 * scritto una volta per due lettori: il feed e il documento CAP hanno la
 * stessa forma e non meritano due cicli identici.
 */
private inline fun forEachTag(xml: String, onTag: (String, XmlPullParser?) -> Unit) {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(StringReader(xml))
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        // Mai nullo, cosi' i confronti restano fra String.
        val name = parser.name?.substringAfter(':').orEmpty()
        when (event) {
            XmlPullParser.START_TAG -> onTag(name, parser)
            XmlPullParser.END_TAG -> onTag(name, null)
        }
        event = parser.next()
    }
}

/**
 * Le date del CAP sono ISO-8601 con fuso ("2026-09-04T17:59:00+00:00").
 *
 * Si tiene il fuso: serve a sapere se l'avviso e' ancora valido, e quel
 * confronto va fatto sull'istante, non sui numeri dell'orologio.
 */
private fun parseTime(text: String): OffsetDateTime? = runCatching {
    OffsetDateTime.parse(text)
}.getOrNull() ?: runCatching {
    // Qualche emittente scrive l'ora senza fuso. Si assume quello del telefono:
    // e' un'ipotesi, ma e' l'unica disponibile, e sbagliare di un'ora la
    // scadenza e' meglio che buttare l'avviso.
    LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toOffsetDateTime()
}.getOrNull()
