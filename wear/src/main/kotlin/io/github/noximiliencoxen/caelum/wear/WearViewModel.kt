package io.github.noximiliencoxen.caelum.wear

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.noximiliencoxen.caelum.data.Forecast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cosa mostra l'orologio.
 *
 * La previsione arriva da [PrevisioneOrologio], la stessa strada di Tile e
 * complicazione: se la rete manca, si mostra l'ultima risposta buona.
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
            val risultato = PrevisioneOrologio.carica(getApplication(), preferisciRete = true)
            _stato.value = risultato?.let { Stato.Pronta(it.previsione, it.fahrenheit) } ?: Stato.Errore
            // Il dato nuovo vale anche per il quadrante: Tile e complicazione lo
            // leggono dalla scorta appena scritta.
            if (risultato?.dallaRete == true) avvisaQuadrante(getApplication())
        }
    }
}
