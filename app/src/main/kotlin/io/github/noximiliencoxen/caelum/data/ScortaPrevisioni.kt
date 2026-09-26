package io.github.noximiliencoxen.caelum.data

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * L'ultima risposta buona di Open-Meteo, su disco: una per posto e per modello.
 *
 * **Era del solo widget, e l'app apriva sul vuoto senza rete.** Il widget
 * teneva gia' la sua scorta (vedi `widget/WidgetForecast.kt`) e ridisegnava
 * quella quando la rete mancava; l'app no, e aperta in metropolitana restava
 * sui trattini finche' la rete non tornava. Adesso la scorta e' una sola e la
 * scrivono tutti e due: l'app la riempie a ogni previsione arrivata, il widget
 * la trova piu' fresca, e all'apertura l'app mostra subito l'ultima buona
 * mentre chiede quella nuova.
 *
 * Si conserva il **testo** della risposta e non la [Forecast]: riletto passa
 * dallo stesso parser di una risposta fresca, e non serve serializzare il
 * modello.
 */
object ScortaPrevisioni {

    /** Oltre questa eta' una previsione conservata non dice piu' niente di utile. */
    const val MAX_GIORNI = 7L

    /**
     * Il file di un posto, dentro [base] (`noBackupFilesDir`: e' una scorta,
     * non un dato da portare su un altro telefono).
     *
     * La cartella si chiama ancora `widget-previsioni` perche' e' quella che il
     * widget usava gia': cambiarle nome lascerebbe orfane le scorte di chi ha
     * l'app installata. Il modello automatico tiene il nome di allora, gli altri
     * lo portano in coda - il widget chiede sempre l'automatico, e una
     * previsione ICON-2I letta come "automatica" sarebbe un altro motore sotto
     * lo stesso nome.
     */
    fun file(base: File, place: Place, model: WeatherModel = WeatherModel.AUTO): File {
        val coordinate = String.format(Locale.ROOT, "%.4f_%.4f", place.latitude, place.longitude)
        val nome = if (model == WeatherModel.AUTO) {
            "$coordinate.json"
        } else {
            "${coordinate}_${model.name.lowercase(Locale.ROOT)}.json"
        }
        return File(File(base, "widget-previsioni"), nome)
    }

    /**
     * Scrive su un file accanto e poi lo rinomina: una scrittura interrotta a
     * meta' non deve lasciare una previsione troncata al posto di quella buona.
     * E intanto butta via quelle troppo vecchie, di posti che nessuno guarda
     * piu'.
     */
    fun conserva(file: File, testo: String) {
        val cartella = file.parentFile ?: return
        cartella.mkdirs()
        val temp = File(cartella, file.name + ".tmp")
        temp.writeText(testo)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        val limite = System.currentTimeMillis() - MAX_GIORNI * 24 * 60 * 60 * 1000
        cartella.listFiles()?.filter { it.lastModified() < limite }?.forEach { it.delete() }
    }

    /** Il testo conservato e quando e' stato scritto, se non ha piu' di [maxMinuti]. */
    fun leggi(file: File, maxMinuti: Long): Pair<String, LocalDateTime>? {
        if (!file.exists()) return null
        val scritta = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault())
        if (scritta.isBefore(LocalDateTime.now().minusMinutes(maxMinuti))) return null
        return file.readText() to scritta
    }
}

/**
 * Una previsione conservata, riportata a oggi per l'app.
 *
 * **Non e' `agedTo` del widget, e la differenza conta.** Il widget fa partire le
 * ore da adesso; l'app conta l'ora scelta come posizione dentro [Forecast.hours]
 * e da' per scontato che quella lista cominci a mezzanotte di oggi (vedi
 * `UiState.nowIndex`). Qui i giorni passati escono, le ore ripartono dalla
 * mezzanotte di oggi, e "adesso" diventa l'ora della previsione che cade
 * adesso: una previsione per le 15 e' piu' vera, alle 15, della misura delle 9.
 *
 * Nulla se della previsione non resta niente da oggi in poi: meglio il vuoto
 * dichiarato di ieri mostrato come oggi.
 */
fun Forecast.riportataAOggi(adesso: LocalDateTime): Forecast? {
    val oggi = adesso.toLocalDate()
    val daOggi = allHours.filter { !it.time.toLocalDate().isBefore(oggi) }
    val giorni = days.filter { !it.date.isBefore(oggi) }
    if (daOggi.isEmpty() || giorni.isEmpty()) return null
    val ora = adesso.truncatedTo(ChronoUnit.HOURS)
    return copy(
        days = giorni,
        hours = daOggi.take(24),
        allHours = daOggi,
        current = daOggi.firstOrNull { it.time == ora }?.let(current::conOra) ?: current,
    )
}

/** "Adesso" preso da un'ora della previsione: la stessa scelta per app e widget. */
internal fun CurrentWeather.conOra(h: HourForecast): CurrentWeather = copy(
    temperature = h.temperature,
    apparent = h.apparent,
    weatherCode = h.weatherCode,
    isDay = h.isDay,
    humidity = h.humidity,
    dewPoint = h.dewPoint,
    precipitation = h.precipitation,
    windSpeed = h.windSpeed,
    windDirection = h.windDirection,
    windGusts = h.windGusts,
)
