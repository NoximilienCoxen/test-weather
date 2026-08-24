package com.forli.meteo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.prefs.ThemeMode
import com.forli.meteo.prefs.ThemePrefs
import com.forli.meteo.ui.home.nearestHourIndex
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val error: String? = null,
    val forecast: Forecast? = null,
    /** Indice del giorno selezionato nella striscia in fondo. 0 = oggi. */
    val selectedDay: Int = 0,
    /** false = GIORNO (valori correnti), true = SETTIMANA (valori del giorno). */
    val weekMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    /** Indice dell'ora mostrata dalla schermata principale. */
    val selectedHour: Int = 0,
)

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WeatherRepository()
    private val prefs = ThemePrefs(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.mode.collect { mode -> _state.update { it.copy(themeMode = mode) } }
        }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.load()
                .onSuccess { forecast ->
                    // All'apertura la schermata mostra l'ora corrente, non la
                    // prima disponibile: e' cio' che ci si aspetta di vedere.
                    val now = nearestHourIndex(forecast.hours, LocalDateTime.now())
                    _state.update {
                        it.copy(
                            loading = false,
                            forecast = forecast,
                            error = null,
                            selectedHour = now,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loading = false, error = failure.message ?: "Errore di rete")
                    }
                }
        }
    }

    fun selectDay(index: Int) {
        _state.update { current ->
            val last = (current.forecast?.days?.size ?: 1) - 1
            current.copy(selectedDay = index.coerceIn(0, maxOf(last, 0)))
        }
    }

    fun selectHour(index: Int) {
        _state.update { current ->
            val last = (current.forecast?.hours?.size ?: 1) - 1
            current.copy(selectedHour = index.coerceIn(0, maxOf(last, 0)))
        }
    }

    fun setWeekMode(week: Boolean) = _state.update { it.copy(weekMode = week) }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setMode(mode) }
    }
}
