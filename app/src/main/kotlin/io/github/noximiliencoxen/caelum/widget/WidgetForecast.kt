package io.github.noximiliencoxen.caelum.widget

import android.content.Context
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.ScortaPrevisioni
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import io.github.noximiliencoxen.caelum.data.conOra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
 *
 * La scorta e' la stessa dell'app ([ScortaPrevisioni]): quando l'app e' stata
 * aperta da poco, il widget trova gia' la previsione fresca e non chiede niente.
 */
internal object WidgetForecast {

    /** Sotto questa eta' la scorta vale come una risposta appena arrivata. */
    private const val FRESCA_MINUTI = 25L

    suspend fun load(context: Context, place: Place): Forecast? {
        val repository = WeatherRepository(place)
        val file = ScortaPrevisioni.file(context.noBackupFilesDir, place)

        // **Una scorta fresca basta, e non si chiede alla rete.** La riempie
        // `AggiornaWidgetWorker`, che ha la rete garantita, o l'app aperta da
        // poco; chiederla di nuovo qui, da un ridisegno senza rete, vorrebbe
        // dire aspettare un fallimento per poi usare la stessa scorta.
        leggi(file, repository, maxMinuti = FRESCA_MINUTI)?.let { return it }

        val fresh = repository.loadWithBody().getOrNull()
        if (fresh != null) {
            val (forecast, body) = fresh
            withContext(Dispatchers.IO) { runCatching { ScortaPrevisioni.conserva(file, body) } }
            return forecast
        }

        // La rete non c'era: si chiede un aggiornamento appena torna, e
        // intanto si usa la scorta, anche se non e' fresca.
        runCatching { AggiornaWidgetWorker.appenaPossibile(context) }
        return leggi(file, repository, maxMinuti = ScortaPrevisioni.MAX_GIORNI * 24 * 60)
    }

    /** Scarica e conserva, senza leggere: lo usa il lavoro in background. */
    suspend fun scarica(context: Context, place: Place): Boolean {
        val (_, body) = WeatherRepository(place).loadWithBody().getOrNull() ?: return false
        withContext(Dispatchers.IO) {
            runCatching { ScortaPrevisioni.conserva(ScortaPrevisioni.file(context.noBackupFilesDir, place), body) }
        }
        return true
    }

    /** La scorta, se c'e' e non ha piu' di [maxMinuti], portata all'ora di adesso. */
    private suspend fun leggi(file: File, repository: WeatherRepository, maxMinuti: Long): Forecast? =
        withContext(Dispatchers.IO) {
            runCatching {
                val (testo, scritta) = ScortaPrevisioni.leggi(file, maxMinuti) ?: return@runCatching null
                val saved = repository.parse(testo).copy(fetchedAt = scritta)
                saved.agedTo(LocalDateTime.now(ZoneOffset.ofTotalSeconds(saved.utcOffsetSeconds)))
            }.getOrNull()
        }
}

/**
 * Una previsione conservata, riportata all'ora [now] (nell'ora del posto).
 *
 * I giorni gia' passati escono, le ore passate pure, e "adesso" diventa l'ora
 * della previsione oraria che cade adesso invece del valore misurato quando la
 * risposta e' arrivata: una previsione per le 15 e' piu' vera, alle 15, di una
 * misura delle 9. Se l'ora di adesso non c'e', "adesso" resta com'era.
 *
 * L'app ha la sua versione, `riportataAOggi`: le sue ore partono da mezzanotte,
 * non da adesso.
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
        current = thisHour?.let(current::conOra) ?: current,
    )
}
