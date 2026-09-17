package io.github.noximiliencoxen.caelum.data

import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Il radar delle precipitazioni, da RainViewer.
 *
 * ## Perche' non e' piu' il Dipartimento della Protezione Civile
 *
 * Perche' il DPC risponde `403 Access Denied` a chiunque non sia il proprio
 * sito, e questa non e' una supposizione: ci sono volute tre prove per
 * saperlo, ognuna delle quali smontava la spiegazione della precedente.
 *
 * 1. Nove indirizzi dalla CI, nove 403. Conclusione: rifiuta i datacentro.
 * 2. Cinque intestazioni diverse dalla CI, cinque 403 identici. Conclusione:
 *    non e' l'agente - ma quelle cinque prove partivano da un indirizzo gia'
 *    rifiutato, quindi **non potevano distinguere le due cause**.
 * 3. Dal telefono, su rete italiana, con un agente che dichiara nome e
 *    indirizzo del progetto: **403**. Quella e' la risposta, e l'ha data lo
 *    schermo di chi usa l'app, non una sonda.
 *
 * La lezione sta in CONTESTO 18: **si e' scritto un lettore intero per un
 * servizio che non ha mai risposto una volta**. Il codice era buono - non
 * indovinava nessun nome di campo, riconosceva per forma, aveva un test - e
 * non e' servito a niente, perche' nessuna eleganza compensa il non avere una
 * risposta da leggere.
 *
 * ## Come e' fatto questo, invece
 *
 * Questo servizio ha risposto **subito e sempre**, e la sua risposta sta
 * catturata in `ci-artifacts/api/radar.txt` da settimane. Quindi qui non c'e'
 * nessun riconoscimento per forma: ci sono i nomi veri dei campi, letti da una
 * risposta vera.
 *
 * ```
 * {"version":"2.0","generated":1789647326,
 *  "host":"https://tilecache.rainviewer.com",
 *  "radar":{"past":[{"time":1789639800,"path":"/v2/radar/057a891b2bed"}, ...],
 *           "nowcast":[]}}
 * ```
 *
 * L'indirizzo di una tessera si compone cosi', e anche questo e' stato
 * **provato** dalla sonda e non dedotto:
 *
 * ```
 * {host}{path}/{lato}/{zoom}/{colonna}/{riga}/{colore}/{morbido}_{neve}.png
 * ```
 *
 * ## Cosa questo radar non sa dire
 *
 * Il DPC copriva l'Italia, e "fuori copertura" era una domanda con una
 * risposta. RainViewer raccoglie radar da mezzo mondo e non dichiara dove
 * arrivano: dove non c'e' un radar le sue tessere sono **trasparenti**, cioe'
 * identiche a un cielo senza pioggia. Non potendo distinguere il vuoto
 * dall'assenza, non si finge di saperlo - lo dice la riga sotto la carta.
 *
 * Come `AirQualityRepository` e `WeatherAlertsRepository`, e' un
 * **arricchimento**: sta su un altro host, arriva dopo, e se non arriva "La
 * pioggia" mostra le dodici colonne che aveva gia'.
 */
class RadarRainViewerRepository(private val place: Place) {

    class SenzaFotogrammi : Exception("RainViewer non ha fotogrammi recenti")

    /**
     * L'elenco dei fotogrammi disponibili, e da che host prenderli.
     *
     * **Si chiede una volta e si tiene**, invece di rifarlo a ogni ora scelta:
     * e' un JSON da settecento byte che descrive le ultime due ore, e chi
     * scorre la barra avanti e indietro non deve pagare una richiesta a ogni
     * scatto del dito.
     */
    suspend fun indice(): Result<RadarIndice> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = Json { ignoreUnknownKeys = true }
                .decodeFromString<IndiceDto>(httpGet(INDICE, fonte = "RainViewer", timeoutMs = 8_000))
            val host = dto.host.orEmpty().ifEmpty { throw SenzaFotogrammi() }
            // Si prende il **misurato** e non il `nowcast`, che e' la
            // previsione a brevissimo: mescolarli senza dire quale sia quale
            // sarebbe la bugia di un'allerta calcolata spacciata per ufficiale.
            val fotogrammi = dto.radar?.past.orEmpty()
                .map { RadarFotogramma(Instant.ofEpochSecond(it.time), it.path) }
            if (fotogrammi.isEmpty()) throw SenzaFotogrammi()
            RadarIndice(host, fotogrammi)
        }
    }

    /**
     * Le tessere di **un** fotogramma, quelle che coprono la finestra.
     *
     * In parallelo: sono una manciata di immagini da pochi kilobyte, e in fila
     * costerebbero la somma dei tempi di andata e ritorno per niente.
     * `coroutineScope` le lega a questa chiamata - se chi aspetta se ne va, se
     * ne vanno anche loro.
     */
    suspend fun fotogramma(
        indice: RadarIndice,
        scelto: RadarFotogramma,
    ): Result<RadarProdotto> = withContext(Dispatchers.IO) {
        runCatching {
            val quali = RadarTessere.coprono(
                RadarTessere.finestraDaChiedere(place.latitude, place.longitude),
            )
            val tessere = coroutineScope {
                quali.map { (colonna, riga) ->
                    async {
                        val url = "${indice.host}${scelto.percorso}/${RadarTessere.LATO}" +
                            "/${RadarTessere.ZOOM}/$colonna/$riga/$COLORE/${MORBIDO}_$NEVE.png"
                        val risposta = httpGetBytes(url, fonte = "RainViewer", timeoutMs = 12_000)
                        RadarTessera(risposta.byte, RadarTessere.riquadroDi(colonna, riga))
                    }
                }.awaitAll()
            }
            RadarProdotto(
                istante = scelto.istante,
                tessere = tessere,
                attribuzione = ATTRIBUZIONE,
            )
        }
    }

    /**
     * Se in questo posto un radar ci guarda.
     *
     * **La maschera si legge al contrario, e non e' un dettaglio.** Quel
     * livello non disegna dove i radar arrivano: disegna dove **non**
     * arrivano - e' l'ombreggiatura che la mappa di RainViewer stende sulle
     * zone cieche. Un pixel opaco vuol dire "qui non guarda nessuno".
     *
     * Letta al dritto, l'app scriverebbe "fuori copertura" sopra Forli' mentre
     * disegna la pioggia che cade su Forli': il primo giro della sonda si era
     * fermato a due campioni e li aveva letti cosi'. La conferma e' arrivata
     * dal Kansas, che ha la rete radar piu' fitta del mondo e legge alfa zero,
     * e dal mezzo del Pacifico, che legge duecentocinquantacinque.
     *
     * Torna `null` quando non si e' potuto sapere - rete giu', tessera
     * illeggibile. `null` non e' "fuori copertura": e' "non lo so", e chi
     * chiama non deve confonderli.
     */
    suspend fun copertura(indice: RadarIndice): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            val colonna = RadarTessere.colonna(place.longitude)
            val riga = RadarTessere.riga(place.latitude)
            val url = "${indice.host}/v2/coverage/0/${RadarTessere.LATO}" +
                "/${RadarTessere.ZOOM}/$colonna/$riga/0/0_0.png"
            val byte = httpGetBytes(url, fonte = "RainViewer", timeoutMs = 8_000).byte
            val mappa = BitmapFactory.decodeByteArray(byte, 0, byte.size) ?: return@runCatching null
            val (x, y) = RadarTessere.pixelDentroLaTessera(
                place.latitude,
                place.longitude,
                RadarTessere.LATO,
            )
            val alfa = Color.alpha(mappa.getPixel(x.coerceIn(0, mappa.width - 1), y.coerceIn(0, mappa.height - 1)))
            mappa.recycle()
            alfa == 0
        }.getOrNull()
    }

    @Serializable
    private data class IndiceDto(val host: String? = null, val radar: RadarDto? = null)

    @Serializable
    private data class RadarDto(val past: List<FotogrammaDto>? = null)

    @Serializable
    private data class FotogrammaDto(val time: Long, val path: String)

    private companion object {
        const val INDICE = "https://api.rainviewer.com/public/weather-maps.json"

        /**
         * La riga sotto la carta.
         *
         * RainViewer per l'uso gratuito della sua API chiede di essere citato,
         * ed e' giusto a prescindere da cosa chieda: chi porta i dati porta
         * anche il proprio nome.
         *
         * Era piu' lunga: portava dietro l'avvertenza che una carta vuota non
         * vuol dire che non piove. Adesso quell'avvertenza ha un posto
         * migliore - `StatoRadar.FuoriCopertura`, che si accende **solo quando
         * e' vera** invece di stare scritta sempre. Un avviso che compare
         * sempre non e' un avviso, e' una cornice.
         */
        const val ATTRIBUZIONE = "Radar: RainViewer."

        /** La tavolozza delle intensita'. Zero e' quella originale di NOAA. */
        const val COLORE = 4

        /** Sfumare fra un valore e l'altro invece di scalinare. */
        const val MORBIDO = 1

        /** Distinguere la neve dalla pioggia col colore. */
        const val NEVE = 1
    }
}
