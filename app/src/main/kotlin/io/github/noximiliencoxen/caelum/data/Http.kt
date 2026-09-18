package io.github.noximiliencoxen.caelum.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * La sola GET dell'app.
 *
 * **Una porta sola, di nuovo.** Per un periodo ce n'erano due: [httpGet] che
 * torna testo e una che tornava byte, perche' le tessere del radar erano
 * immagini e un'immagine letta come stringa e' una stringa rovinata. Se n'e'
 * andata col radar (CONTESTO 24). [RispostaGrezza] resta perche' e' quello che
 * [httpGetGrezzo] produce, e la connessione la apre, la legge e la chiude una
 * funzione sola.
 *
 * **Era scritta cinque volte.** Lo stesso identico blocco - apri, chiedi, leggi
 * il flusso giusto a seconda del codice, chiudi nel `finally` - stava in
 * `WeatherRepository.httpGet`, di nuovo **in linea** dentro `search()`, in
 * `simpleHttpGet` accanto alla Norma, in `AirQualityRepository` e in
 * `WeatherAlertsRepository`. Un commento su una delle copie dichiarava la
 * ripetizione intenzionale - "il companion non può chiamare metodi di
 * istanza" - e la spiegazione era vera per quella copia sola: non diceva
 * niente delle altre quattro, e la strada che le toglie tutte era questa, una
 * funzione di primo livello che non appartiene a nessuna classe.
 *
 * Le differenze fra le copie erano quattro, e sono diventate quattro parametri:
 *
 * - **[fonte]**, che finisce nel messaggio d'errore. E' l'unica cosa che
 *   cambiava davvero fra tre delle cinque, ed e' anche quella che conta: un
 *   "HTTP 503" senza dire di chi manda a cercare nel posto sbagliato.
 * - **[accept]**, perche' le allerte parlano XML e tutto il resto JSON.
 * - **[timeoutMs]**, che per la ricerca dei luoghi era otto secondi invece di
 *   dieci: chi sta scrivendo in una casella aspetta meno volentieri di chi ha
 *   appena aperto l'app.
 * - Il ripiego dell'errore: `errorStream` invece di `inputStream` quando il
 *   codice non e' un 2xx. Open-Meteo scrive li' dentro il **motivo** del
 *   rifiuto, e buttarlo via vorrebbe dire perdere l'unica riga che spiega
 *   perche' una richiesta e' stata respinta.
 *
 * **Resta bloccante, e resta un difetto noto.** `HttpURLConnection` non
 * risponde all'annullamento della coroutine: un `cancel()` mentre si sta
 * leggendo non interrompe niente, e la `disconnect()` sta nel `finally`, che
 * gira a lettura finita. Nel caso peggiore un lavoro "annullato" tiene occupato
 * un thread di IO per tutto il timeout. Si risolverebbe passando il thread e
 * chiudendo la connessione dall'esterno, oppure con un client che la
 * cancellazione la capisce - cioe' con una dipendenza in piu', che questo
 * progetto finora non ha voluto. Sta scritto qui perche' il posto in cui
 * risolverlo, se si decidera' di farlo, e' questo e uno solo.
 */
/**
 * Chi sta chiamando.
 *
 * Senza questa riga parte l'agente predefinito di Android - `Dalvik/2.1.0
 * (Linux; U; Android 14; ...)` - che e' fra i primi che una rete di
 * distribuzione scarta, perche' e' quello che manda qualsiasi cosa non sia un
 * browser.
 *
 * **Non e' un travestimento.** Dice il nome dell'app e dove sta il codice, che
 * e' quello che un agente serve a dire: se un giorno una fonte volesse
 * chiedere di smettere, da qui sa a chi scrivere. Non finge di essere Chrome e
 * non manda un `Referer` del sito di qualcun altro - se un servizio pubblico
 * risponde solo a chi si spaccia per il suo sito, quel servizio non e' aperto
 * a terzi, e girarci attorno non e' una cosa che questo progetto fa. Chi in
 * futuro fosse tentato di provarci, legga prima CONTESTO 16.5.
 *
 * Vale per tutte le fonti e non solo per il radar: e' cortesia verso ognuna, e
 * la policy delle tessere di OpenStreetMap un agente identificabile lo
 * **chiede**, se un giorno serviranno.
 */
private const val AGENTE = "Caelum/1.0 (+https://github.com/NoximilienCoxen/test-weather)"

internal fun httpGet(
    url: String,
    fonte: String,
    accept: String = "application/json",
    timeoutMs: Int = 10_000,
): String = httpGetGrezzo(url, fonte, accept, timeoutMs).testo

// Nota sul charset: prima qui c'era `bufferedReader()`, che sceglie la
// codifica dichiarata dalla connessione; adesso il testo si ricava dai byte
// in UTF-8 fisso. Non e' una perdita per queste fonti - JSON e' UTF-8 per
// specifica, e l'Atom di MeteoAlarm si dichiara UTF-8 - ma e' un'assunzione,
// e sta scritta perche' una fonte futura che parlasse un'altra codifica
// rompa qui e non a valle, dove sarebbe un accento storto senza spiegazione.

/** Il corpo di una risposta, coi byte intatti e il tipo che il server dichiara. */
internal class RispostaGrezza(val byte: ByteArray, val tipo: String) {
    /** Lo stesso corpo letto come testo, per chi si aspettava testo. */
    val testo: String get() = String(byte, Charsets.UTF_8)
}

private fun httpGetGrezzo(
    url: String,
    fonte: String,
    accept: String,
    timeoutMs: Int,
): RispostaGrezza {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        // I feed di allertamento rispondono da un indirizzo e servono da un
        // altro. E' gia' il comportamento predefinito: sta scritto perche' si
        // veda che e' voluto, non ereditato per caso.
        instanceFollowRedirects = true
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", AGENTE)
    }
    try {
        val code = connection.responseCode
        val ok = code in 200..299
        val stream = if (ok) connection.inputStream else connection.errorStream
        val byte = stream?.use { it.readBytes() } ?: ByteArray(0)
        if (!ok) error("HTTP $code da $fonte: ${String(byte, Charsets.UTF_8).take(200)}")
        return RispostaGrezza(byte, connection.contentType.orEmpty())
    } finally {
        connection.disconnect()
    }
}
