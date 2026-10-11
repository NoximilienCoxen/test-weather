package io.github.noximiliencoxen.caelum.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.SharedPreferences
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.ScortaPrevisioni
import io.github.noximiliencoxen.caelum.data.StatoSincronizzato
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import io.github.noximiliencoxen.caelum.lingua.Lingue
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
        data class Pronta(val previsione: Forecast, val fahrenheit: Boolean = false) : Stato
        data object Errore : Stato
    }

    private val _stato = MutableStateFlow<Stato>(Stato.Caricamento)
    val stato: StateFlow<Stato> = _stato.asStateFlow()

    // Quando il telefono manda uno stato nuovo si ricarica da solo. Il listener va
    // tenuto in un campo: SharedPreferences lo tiene con un riferimento debole.
    private val ascoltatore = SharedPreferences.OnSharedPreferenceChangeListener { _, chiave ->
        if (chiave == StatoOrologio.CHIAVE_JSON) aggiorna()
    }

    init {
        StatoOrologio.prefs(app).registerOnSharedPreferenceChangeListener(ascoltatore)
        aggiorna()
    }

    override fun onCleared() {
        StatoOrologio.prefs(getApplication()).unregisterOnSharedPreferenceChangeListener(ascoltatore)
    }

    fun aggiorna() {
        viewModelScope.launch {
            // Lo stato del telefono, o Forli' finche' non ne e' arrivato uno.
            val telefono: StatoSincronizzato? = withContext(Dispatchers.IO) { StatoOrologio.leggi(getApplication()) }
            telefono?.let { Lingue.impostaScelta(it.sceltaLingua) }
            val risultato = carica(telefono?.toPlace() ?: Place.FORLI)
            _stato.value = if (risultato is Stato.Pronta) {
                risultato.copy(fahrenheit = telefono?.inFahrenheit == true)
            } else {
                risultato
            }
        }
    }

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
