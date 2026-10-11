package io.github.noximiliencoxen.caelum.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.ScortaPrevisioni
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cosa mostra l'orologio.
 *
 * La previsione la scarica l'orologio da solo ([WeatherRepository], lo stesso
 * codice del telefono nel modulo `core`) e la tiene su disco come fa l'app: se
 * la rete manca, si mostra l'ultima risposta buona.
 */
class WearViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Stato {
        data object Caricamento : Stato
        data class Pronta(val previsione: Forecast) : Stato
        data object Errore : Stato
    }

    private val _stato = MutableStateFlow<Stato>(Stato.Caricamento)
    val stato: StateFlow<Stato> = _stato.asStateFlow()

    init {
        aggiorna()
    }

    fun aggiorna() {
        viewModelScope.launch { _stato.value = carica(localita()) }
    }

    // Fino alla sincronizzazione col telefono (fase 3) la localita' e' fissa.
    private fun localita(): Place = Place.FORLI

    private suspend fun carica(place: Place): Stato {
        val repository = WeatherRepository(place)
        val file = ScortaPrevisioni.file(getApplication<Application>().noBackupFilesDir, place)

        repository.loadWithBody().getOrNull()?.let { (previsione, testo) ->
            withContext(Dispatchers.IO) { runCatching { ScortaPrevisioni.conserva(file, testo) } }
            return Stato.Pronta(previsione)
        }

        // Rete assente: l'ultima risposta buona, fino a una settimana fa.
        val scorta = withContext(Dispatchers.IO) {
            runCatching {
                val (testo, scritta) = ScortaPrevisioni.leggi(file, ScortaPrevisioni.MAX_GIORNI * 24 * 60)
                    ?: return@runCatching null
                repository.parse(testo).copy(fetchedAt = scritta)
            }.getOrNull()
        }
        return scorta?.let { Stato.Pronta(it) } ?: Stato.Errore
    }
}
