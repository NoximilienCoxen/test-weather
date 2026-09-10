package io.github.noximiliencoxen.caelum.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * La sola GET dell'app.
 *
 * **Era scritta cinque volte.** Lo stesso identico blocco - apri, chiedi, leggi
 * il flusso giusto a seconda del codice, chiudi nel `finally` - stava in
 * `WeatherRepository.httpGet`, di nuovo **in linea** dentro `search()`, in
 * `simpleHttpGet` accanto alla Norma, in `AirQualityRepository` e in
 * `WeatherAlertsRepository`. Un commento su una delle copie dichiarava la
 * ripetizione intenzionale - "il companion non puo' chiamare metodi di
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
internal fun httpGet(
    url: String,
    fonte: String,
    accept: String = "application/json",
    timeoutMs: Int = 10_000,
): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        // I feed di allertamento rispondono da un indirizzo e servono da un
        // altro. E' gia' il comportamento predefinito: sta scritto perche' si
        // veda che e' voluto, non ereditato per caso.
        instanceFollowRedirects = true
        setRequestProperty("Accept", accept)
    }
    try {
        val code = connection.responseCode
        val ok = code in 200..299
        val stream = if (ok) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (!ok) error("HTTP $code da $fonte: ${text.take(200)}")
        return text
    } finally {
        connection.disconnect()
    }
}
