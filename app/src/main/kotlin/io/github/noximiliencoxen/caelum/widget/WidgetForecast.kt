package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * La previsione per un widget, con l'ultima buona di scorta.
 *
 * **Il widget restava su "--" dopo qualche giorno senza aprire l'app.** Non era
 * la rete del telefono: era quella concessa all'app. Un'app che non si apre da
 * tempo scivola nei gruppi di standby di Android in cui il lavoro in
 * background perde la rete, e un firewall come NetGuard puo' toglierla del
 * tutto a schermo spento. Il widget si svegliava, la richiesta falliva, e il
 * disegno "senza dati" sostituiva quello buono di un'ora prima.
 *
 * Adesso ogni risposta buona resta su disco, una per posto, e quando la rete
 * manca il widget ridisegna quella - portata avanti all'ora di adesso da
 * [agedTo], cosi' non mostra come "oggi" un giorno gia' passato. Solo se non
 * c'e' niente di utilizzabile resta il disegno senza dati.
 */
internal object WidgetForecast {

    /** Oltre questa eta' una previsione conservata non dice piu' niente di utile. */
    private const val MAX_AGE_DAYS = 7L

    /** Sotto questa eta' la scorta vale come una risposta appena arrivata. */
    private const val FRESCA_MINUTI = 25L

    suspend fun load(context: Context, place: Place): Forecast? {
        val repository = WeatherRepository(place)
        val file = fileFor(context, place)

        // **Una scorta fresca basta, e non si chiede alla rete.** La riempie
        // `AggiornaWidgetWorker`, che ha la rete garantita; chiederla di nuovo
        // qui, da un ridisegno senza rete, vorrebbe dire aspettare un
        // fallimento per poi usare la stessa scorta.
        leggi(file, repository, maxMinuti = FRESCA_MINUTI)?.let { return it }

        val fresh = repository.loadWithBody().getOrNull()
        if (fresh != null) {
            val (forecast, body) = fresh
            withContext(Dispatchers.IO) { runCatching { keep(file, body) } }
            return forecast
        }

        // La rete non c'era: si chiede un aggiornamento appena torna, e
        // intanto si usa la scorta, anche se non e' fresca.
        runCatching { AggiornaWidgetWorker.appenaPossibile(context) }
        return leggi(file, repository, maxMinuti = MAX_AGE_DAYS * 24 * 60)
    }

    /** Scarica e conserva, senza leggere: lo usa il lavoro in background. */
    suspend fun scarica(context: Context, place: Place): Boolean {
        val (_, body) = WeatherRepository(place).loadWithBody().getOrNull() ?: return false
        withContext(Dispatchers.IO) { runCatching { keep(fileFor(context, place), body) } }
        return true
    }

    /** La scorta, se c'e' e non ha piu' di [maxMinuti], portata all'ora di adesso. */
    private suspend fun leggi(file: File, repository: WeatherRepository, maxMinuti: Long): Forecast? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) return@runCatching null
                val savedAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(file.lastModified()),
                    ZoneId.systemDefault(),
                )
                if (savedAt.isBefore(LocalDateTime.now().minusMinutes(maxMinuti))) return@runCatching null
                val saved = repository.parse(file.readText()).copy(fetchedAt = savedAt)
                saved.agedTo(LocalDateTime.now(ZoneOffset.ofTotalSeconds(saved.utcOffsetSeconds)))
            }.getOrNull()
        }

    /**
     * Scrive su un file accanto e poi lo rinomina: un widget interrotto a meta'
     * scrittura non deve lasciare una previsione troncata al posto di quella
     * buona. E intanto butta via quelle troppo vecchie, di posti che nessun
     * widget guarda piu'.
     */
    private fun keep(file: File, body: String) {
        val dir = file.parentFile ?: return
        dir.mkdirs()
        val temp = File(dir, file.name + ".tmp")
        temp.writeText(body)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        val limit = System.currentTimeMillis() - MAX_AGE_DAYS * 24 * 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    // `noBackupFilesDir`: e' una scorta, non un dato da portare su un altro telefono.
    private fun fileFor(context: Context, place: Place): File = File(
        File(context.noBackupFilesDir, "widget-previsioni"),
        String.format(Locale.ROOT, "%.4f_%.4f.json", place.latitude, place.longitude),
    )
}

/**
 * Una previsione conservata, riportata all'ora [now] (nell'ora del posto).
 *
 * I giorni gia' passati escono, le ore passate pure, e "adesso" diventa l'ora
 * della previsione oraria che cade adesso invece del valore misurato quando la
 * risposta e' arrivata: una previsione per le 15 e' piu' vera, alle 15, di una
 * misura delle 9. Se l'ora di adesso non c'e', "adesso" resta com'era.
 */
internal fun Forecast.agedTo(now: LocalDateTime): Forecast {
    val today = now.toLocalDate()
    val hour = now.truncatedTo(ChronoUnit.HOURS)
    val ahead = allHours.filter { !it.time.isBefore(hour) }
    val thisHour = ahead.firstOrNull()?.takeIf { it.time == hour }

    return copy(
        days = days.filter { !it.date.isBefore(today) },
        hours = ahead.take(24),
        allHours = ahead,
        current = thisHour?.let { h ->
            current.copy(
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
        } ?: current,
    )
}
