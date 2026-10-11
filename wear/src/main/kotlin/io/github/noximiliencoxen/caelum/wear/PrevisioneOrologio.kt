package io.github.noximiliencoxen.caelum.wear

import android.content.Context
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.ScortaPrevisioni
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import io.github.noximiliencoxen.caelum.data.riportataAOggi
import io.github.noximiliencoxen.caelum.lingua.Lingue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime

/**
 * La previsione dell'orologio, per chiunque la chieda: l'app, la Tile, la
 * complicazione e il lavoro periodico.
 *
 * Una strada sola, cosi' i quattro non possono mostrare quattro tempi diversi.
 * La localita', le unita' e la lingua sono quelle mandate dal telefono
 * ([StatoOrologio]); finche' non e' arrivato niente, Forli'.
 */
object PrevisioneOrologio {

    data class Risultato(
        val previsione: Forecast,
        val fahrenheit: Boolean,
        /** Vera se la previsione e' arrivata adesso dalla rete, e non dal disco. */
        val dallaRete: Boolean,
    )

    /**
     * Una scorta piu' giovane di cosi' basta a Tile e complicazione, che il
     * sistema interroga spesso: niente rete a ogni sguardo al quadrante.
     */
    const val FRESCA_MINUTI = 30L

    /**
     * @param preferisciRete vero per l'app aperta e per il lavoro periodico,
     *   che vogliono il dato nuovo; falso per Tile e complicazione, che si
     *   accontentano di una scorta di meno di [FRESCA_MINUTI] minuti.
     * @return null solo se mancano sia la rete sia una scorta di meno di una
     *   settimana.
     */
    suspend fun carica(contesto: Context, preferisciRete: Boolean): Risultato? {
        val app = contesto.applicationContext
        val telefono = withContext(Dispatchers.IO) { StatoOrologio.leggi(app) }
        telefono?.let { Lingue.impostaScelta(it.sceltaLingua) }
        val fahrenheit = telefono?.inFahrenheit == true
        val place = telefono?.toPlace() ?: Place.FORLI
        val repository = WeatherRepository(place)
        val file = ScortaPrevisioni.file(app.noBackupFilesDir, place)

        if (!preferisciRete) {
            dalDisco(repository, file, FRESCA_MINUTI)?.let { return Risultato(it, fahrenheit, dallaRete = false) }
        }

        repository.loadWithBody().getOrNull()?.let { (previsione, testo) ->
            withContext(Dispatchers.IO) { runCatching { ScortaPrevisioni.conserva(file, testo) } }
            return Risultato(previsione, fahrenheit, dallaRete = true)
        }

        // Rete assente: l'ultima risposta buona, fino a una settimana fa.
        return dalDisco(repository, file, ScortaPrevisioni.MAX_GIORNI * 24 * 60)
            ?.let { Risultato(it, fahrenheit, dallaRete = false) }
    }

    private suspend fun dalDisco(repository: WeatherRepository, file: java.io.File, maxMinuti: Long): Forecast? =
        withContext(Dispatchers.IO) {
            runCatching {
                val (testo, scritta) = ScortaPrevisioni.leggi(file, maxMinuti) ?: return@runCatching null
                val letta = repository.parse(testo).copy(fetchedAt = scritta)
                // Una scorta di ore fa ha un "adesso" di ore fa: la previsione
                // per quest'ora e' piu' vera della misura di allora.
                if (Duration.between(scritta, LocalDateTime.now()).toMinutes() >= 60) {
                    letta.riportataAOggi(letta.nowThere())
                } else {
                    letta
                }
            }.getOrNull()
        }
}
