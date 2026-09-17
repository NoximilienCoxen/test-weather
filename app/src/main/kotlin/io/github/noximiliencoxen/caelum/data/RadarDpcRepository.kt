package io.github.noximiliencoxen.caelum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.util.Base64

/**
 * Il radar delle precipitazioni, dal Dipartimento della Protezione Civile.
 *
 * Fonte: `radar-api.protezionecivile.it`, il servizio che alimenta la mappa
 * pubblica del Dipartimento. I dati sono in **CC BY-SA 4.0**, e la licenza
 * chiede l'attribuzione **a schermo**: la scritta sotto la mappa non e'
 * cortesia, e' la condizione d'uso. Non va tolta, e non va sostituita col nome
 * di un'altra fonte.
 *
 * ## Questo file e' scritto senza aver mai visto una risposta
 *
 * E' una cosa che in questo progetto non era mai successa, e va detta prima di
 * tutto il resto. `WeatherAlertsRepository` ha un commento che spiega quanto
 * costo' fidarsi di due campi dedotti invece che letti - `awareness_level` e
 * `awareness_type`, che nel feed non esistono - e quella lezione vale anche
 * qui. Solo che qui la risposta **non si puo' leggere**: la sonda `probe-api`
 * ha chiesto a nove indirizzi del servizio, con e senza `Origin`, su tutti i
 * tipi di prodotto, e ne ha avuti nove `HTTP 403 Access Denied` in HTML (vedi
 * CONTESTO 13-septies). Non e' l'indirizzo sbagliato - quello risponde 404 -
 * e' il servizio che rifiuta **il chiamante**: un runner di GitHub e' un
 * indirizzo di datacentro, e quel servizio e' nato per il proprio sito.
 *
 * Da un telefono italiano, con ogni probabilita', risponde. Ma da qui non si
 * puo' verificare, e non c'e' modo di sapere come si chiamino i campi.
 *
 * **Percio' questo lettore non conosce nessun nome di campo.** Non c'e' nessun
 * `@SerialName`, nessuna data class del prodotto. Si scorre il JSON e si cerca
 * per **forma**:
 *
 * - l'istante e' un intero che sta nella finestra plausibile di un'epoca
 *   (secondi o millesimi di secondo) - vedi [cercaEpoca];
 * - l'immagine e' una stringa che, decodificata da base64, comincia con la
 *   firma di un PNG - vedi [cercaImmagine]; oppure e' il corpo stesso della
 *   risposta, se il server dichiara un tipo `image`;
 * - il riquadro geografico e' un array di quattro numeri, o di due coppie, i
 *   cui valori cadono dove devono cadere delle coordinate - vedi [cercaBbox].
 *
 * Dedurre un nome e' tirare a indovinare; riconoscere una forma no. Un intero
 * da milletrecento miliardi dentro una risposta di un radar **e'** un istante,
 * comunque lo si chiami.
 *
 * ## Cosa succede quando non si riconosce niente
 *
 * Fallisce, e **dice cosa ha visto**: [Illeggibile] porta con se' i primi
 * duecento caratteri della risposta e l'elenco delle chiavi di primo livello.
 * Quella riga finisce sotto la mappa, sul telefono di chi l'app la usa. E'
 * l'unico modo che questo progetto ha di leggere una risposta vera del DPC: la
 * sonda non ci arriva, il telefono si'.
 *
 * ## Il riquadro non si inventa
 *
 * Se il prodotto non dichiara il proprio riquadro, l'immagine **non si
 * disegna**. Posarla su un riquadro scelto da noi vorrebbe dire mostrare la
 * pioggia dove non e', e una mappa che sbaglia di cinquanta chilometri e'
 * peggio di nessuna mappa: chi la guarda non ha modo di accorgersene.
 *
 * Come `AirQualityRepository` e `WeatherAlertsRepository`, questo e' un
 * **arricchimento**: sta su un altro host, arriva dopo, e se non arriva la
 * schermata della pioggia funziona lo stesso.
 */
class RadarDpcRepository(private val place: Place) {

    /**
     * Fuori dal dominio del radar italiano.
     *
     * Non e' un guasto, ed e' importante che non venga mostrato come tale:
     * a Tokyo il radar non e' vuoto, e' **assente**. Stessa distinzione che
     * `WeatherAlertsRepository.OutOfCoverage` fa per le allerte, e per la
     * stessa ragione - "nessuna pioggia" e "non lo so" non sono la stessa
     * frase.
     */
    class OutOfCoverage : Exception("Il radar del DPC copre l'Italia")

    /**
     * Ha risposto, ma in una forma che non si riconosce.
     *
     * [indizio] e' quello che si e' visto: serve a chi legge lo schermo, non
     * al codice. Il giorno in cui qualcuno riportera' quella riga, questo file
     * potra' smettere di indovinare la forma e leggere i nomi veri.
     */
    class Illeggibile(val indizio: String) : Exception("Risposta del radar non riconosciuta: $indizio")

    suspend fun load(): Result<RadarProdotto> = withContext(Dispatchers.IO) {
        runCatching {
            if (!DOMINIO_DPC.contiene(place.latitude, place.longitude)) throw OutOfCoverage()
            // SRI prima di VMI: SRI e' l'intensita' di pioggia al suolo in
            // millimetri l'ora, che e' la domanda di questa sala; VMI e' il
            // massimo sulla verticale, che e' piu' spettacolare e meno
            // pertinente. Se il primo non c'e' si prende il secondo, perche'
            // una mappa approssimata batte una mappa assente.
            val errori = mutableListOf<String>()
            for (tipo in listOf("SRI", "VMI")) {
                val esito = runCatching { prodotto(tipo) }
                esito.getOrNull()?.let { return@runCatching it }
                errori += "$tipo: ${esito.exceptionOrNull()?.message.orEmpty().take(120)}"
            }
            throw Illeggibile(errori.joinToString(" | "))
        }
    }

    private fun prodotto(tipo: String): RadarProdotto {
        val indice = httpGet(
            "$BASE/findLastProductByType?type=$tipo",
            fonte = "Radar-DPC ($tipo)",
            timeoutMs = 8_000,
        )
        val istanteMs = RadarForma.cercaEpoca(Json.parseToJsonElement(indice))
            ?: throw Illeggibile(RadarForma.riassunto(indice))

        val risposta = httpGetBytes(
            "$BASE/getProduct?type=$tipo&productDate=$istanteMs",
            fonte = "Radar-DPC ($tipo)",
            timeoutMs = 15_000,
        )

        // Due forme possibili, e si distinguono da cio' che il server
        // dichiara: o il corpo **e'** l'immagine, o la contiene.
        if (risposta.tipo.startsWith("image/")) {
            // Un'immagine nuda non porta con se' il proprio riquadro, e senza
            // riquadro non si posa: si prova a chiederlo a chi lo sa.
            val riquadro = riquadroDichiarato(tipo) ?: throw Illeggibile(
                "immagine ${risposta.byte.size} byte senza riquadro dichiarato",
            )
            return RadarProdotto(Instant.ofEpochMilli(istanteMs), risposta.byte, riquadro)
        }

        val testo = risposta.testo
        val albero = runCatching { Json.parseToJsonElement(testo) }.getOrNull()
            ?: throw Illeggibile(RadarForma.riassunto(testo))
        val png = RadarForma.cercaImmagine(albero) ?: throw Illeggibile(RadarForma.riassunto(testo))
        val riquadro = RadarForma.cercaBbox(albero) ?: riquadroDichiarato(tipo)
            ?: throw Illeggibile("immagine trovata, riquadro no: ${RadarForma.riassunto(testo)}")
        return RadarProdotto(Instant.ofEpochMilli(istanteMs), png, riquadro)
    }

    /**
     * Il riquadro chiesto al servizio invece che dedotto.
     *
     * Se `getProduct` non lo porta, l'ultima carta prima di arrendersi e'
     * `findAvailableProductsByType`, che di solito descrive il prodotto invece
     * di servirlo. Se nemmeno li' c'e' un array che somigli a delle
     * coordinate, si torna `null` e la mappa non si disegna: **il riquadro non
     * si inventa**.
     */
    private fun riquadroDichiarato(tipo: String): RadarRiquadro? = runCatching {
        val testo = httpGet(
            "$BASE/findAvailableProductsByType?type=$tipo",
            fonte = "Radar-DPC ($tipo)",
            timeoutMs = 8_000,
        )
        RadarForma.cercaBbox(Json.parseToJsonElement(testo))
    }.getOrNull()

    private companion object {
        const val BASE = "https://radar-api.protezionecivile.it/wide/product"

        /**
         * Dove arriva il radar italiano, a spanne.
         *
         * Serve **solo** a decidere se la domanda ha senso per la localita'
         * scelta, non a disegnare: quello che si disegna usa il riquadro che
         * il prodotto dichiara. Un rettangolo largo si puo' permettere di
         * essere approssimato; uno su cui si posa un'immagine no.
         */
        val DOMINIO_DPC = RadarRiquadro(latMin = 34.5, lonMin = 4.0, latMax = 48.5, lonMax = 21.5)
    }
}


/**
 * Riconoscere una risposta dalla sua forma, non dai suoi nomi.
 *
 * Sta fuori dal repository, e non per ordine: e' l'unica parte di questo
 * lavoro che si possa **provare** senza rete. Il resto - aprire una
 * connessione, chiedere due indirizzi in fila, ripiegare da SRI a VMI - si
 * prova solo con un servizio che risponde, e quel servizio da qui risponde
 * 403. Queste quattro funzioni no: prendono un albero JSON e tornano un
 * numero, dei byte o un rettangolo, e su quelle `RadarFormaTest` puo' dire
 * qualcosa di vero.
 *
 * `internal` e non privato proprio per questo. Il perche' di ogni regola sta
 * sulla funzione che la applica.
 */
internal object RadarForma {

    fun riassunto(corpo: String): String {
        val chiavi = runCatching {
            (Json.parseToJsonElement(corpo) as? JsonObject)?.keys?.joinToString(",")
        }.getOrNull()
        val testa = corpo.trim().replace(Regex("\\s+"), " ").take(200)
        return if (chiavi.isNullOrEmpty()) testa else "chiavi[$chiavi] $testa"
    }

    /**
     * Un istante riconosciuto dalla sua taglia, non dal suo nome.
     *
     * Le epoche plausibili stanno in due finestre: i secondi fra il 2001 e
     * il 2033, i millesimi nello stesso intervallo. Fuori di li' c'e' di
     * tutto - identificativi, dimensioni, codici - e niente che sia una
     * data. Si prende il **massimo**, perche' quando ce n'e' piu' d'una la
     * piu' recente e' quella che si vuole mostrare.
     */
    fun cercaEpoca(albero: JsonElement): Long? =
        numeri(albero).mapNotNull { n ->
            val v = n.toLong()
            when {
                v in 1_000_000_000L..2_000_000_000L -> v * 1000L
                v in 1_000_000_000_000L..2_000_000_000_000L -> v
                else -> null
            }
        }.maxOrNull()

    /** La firma dei primi otto byte di un PNG. */
    val FIRMA_PNG = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    /**
     * L'immagine riconosciuta dalla propria firma.
     *
     * Si guarda ogni stringa dell'albero, si toglie l'eventuale prefisso
     * `data:image/png;base64,` che alcuni servizi ci mettono davanti, si
     * decodifica e si controllano gli otto byte di testa. Una stringa che
     * decodifica in qualcosa che **comincia come un PNG** e' un PNG: non
     * serve sapere come si chiama il campo che la contiene.
     */
    fun cercaImmagine(albero: JsonElement): ByteArray? =
        stringhe(albero).asSequence()
            .filter { it.length > 512 }
            .mapNotNull { grezza ->
                val corpo = grezza.substringAfter("base64,", grezza)
                runCatching { Base64.getMimeDecoder().decode(corpo) }.getOrNull()
            }
            .firstOrNull { byte ->
                byte.size > FIRMA_PNG.size &&
                    FIRMA_PNG.indices.all { byte[it] == FIRMA_PNG[it] }
            }

    /**
     * Il riquadro riconosciuto dai valori che contiene.
     *
     * Due forme accettate: quattro numeri di fila, o due coppie. In
     * entrambi i casi si accetta solo se i quattro valori, letti come due
     * latitudini e due longitudini, cadono dove devono cadere **e**
     * coprono l'Italia: un array di quattro numeri qualsiasi non e' un
     * riquadro, e prenderlo per tale sposterebbe la pioggia.
     *
     * L'ordine e' ambiguo per costruzione - c'e' chi scrive lat,lon e chi
     * lon,lat - e si decide su quale dei due il rettangolo risulta
     * plausibile. Se lo sono tutti e due, il rettangolo e' cosi' piccolo o
     * cosi' quadrato che i due ordini si equivalgono, e si prende il
     * primo.
     */
    fun cercaBbox(albero: JsonElement): RadarRiquadro? =
        quaterne(albero).firstNotNullOfOrNull { q ->
            riquadro(q[0], q[1], q[2], q[3]) ?: riquadro(q[1], q[0], q[3], q[2])
        }

    fun riquadro(lat1: Double, lon1: Double, lat2: Double, lon2: Double): RadarRiquadro? {
        val r = RadarRiquadro(
            latMin = minOf(lat1, lat2),
            lonMin = minOf(lon1, lon2),
            latMax = maxOf(lat1, lat2),
            lonMax = maxOf(lon1, lon2),
        )
        val plausibile = r.latMin >= 30.0 && r.latMax <= 52.0 &&
            r.lonMin >= 0.0 && r.lonMax <= 26.0 &&
            r.latMax - r.latMin > 3.0 && r.lonMax - r.lonMin > 3.0
        return r.takeIf { plausibile }
    }

    fun numeri(e: JsonElement): List<Double> = when (e) {
        is JsonObject -> e.values.flatMap { numeri(it) }
        is JsonArray -> e.flatMap { numeri(it) }
        is JsonPrimitive -> listOfNotNull(if (e.isString) null else e.doubleOrNull)
        else -> emptyList()
    }

    fun stringhe(e: JsonElement): List<String> = when (e) {
        is JsonObject -> e.values.flatMap { stringhe(it) }
        is JsonArray -> e.flatMap { stringhe(it) }
        is JsonPrimitive -> if (e.isString) listOf(e.content) else emptyList()
        else -> emptyList()
    }

    /** Ogni array di quattro numeri dell'albero, comprese le due coppie appaiate. */
    fun quaterne(e: JsonElement): List<DoubleArray> = when (e) {
        is JsonObject -> e.values.flatMap { quaterne(it) }
        is JsonArray -> {
            val piatto = e.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.doubleOrNull }
            val coppie = e.mapNotNull { figlio ->
                (figlio as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.doubleOrNull }
                    ?.takeIf { it.size == 2 }
            }
            buildList {
                if (piatto.size == 4) add(piatto.toDoubleArray())
                if (coppie.size == 2) {
                    add(doubleArrayOf(coppie[0][0], coppie[0][1], coppie[1][0], coppie[1][1]))
                }
                addAll(e.flatMap { quaterne(it) })
            }
        }
        else -> emptyList()
    }
}

/** Un fotogramma del radar: quando, cosa, e sopra quale pezzo di mondo. */
class RadarProdotto(
    val istante: Instant,
    val png: ByteArray,
    val riquadro: RadarRiquadro,
)

/** Gli estremi geografici di un'immagine, in gradi. */
data class RadarRiquadro(
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double,
) {
    fun contiene(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax
}

/**
 * A che punto sta il radar, per chi deve disegnarlo.
 *
 * Quattro casi e non tre, perche' **"fuori copertura" e "non disponibile" non
 * sono la stessa cosa** e mostrarli con la stessa faccia sarebbe una bugia per
 * omissione: il primo e' una proprieta' del posto - il radar italiano finisce
 * dove finisce l'Italia - il secondo e' un guasto. Stessa distinzione che le
 * allerte fanno gia' con `alertsOutOfCoverage`.
 */
sealed interface StatoRadar {
    /** Si sta chiedendo. */
    data object InCorso : StatoRadar

    /** C'e' un fotogramma da posare. */
    data class Pronto(val prodotto: RadarProdotto) : StatoRadar

    /** La localita' scelta sta fuori dal dominio del radar italiano. */
    data object FuoriCopertura : StatoRadar

    /**
     * Ha risposto male, o non ha risposto.
     *
     * [indizio] e' quello che si e' visto - le chiavi, la testa del corpo - e
     * finisce **sullo schermo**. E' brutto da leggere e sta li' apposta: la
     * sonda della CI al DPC non ci arriva (403 da un datacentro), un telefono
     * italiano si', e questa riga e' l'unico modo che il progetto ha di sapere
     * com'e' fatta davvero una risposta di quel servizio.
     */
    data class NonDisponibile(val indizio: String?) : StatoRadar
}
