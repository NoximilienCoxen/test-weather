package com.forli.meteo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Place
import com.forli.meteo.data.SunClock
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.prefs.SettingsPrefs
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.home.nearestHourIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Indice dell'ora mostrata dalla schermata principale. */
    val selectedHour: Int = 0,
    /**
     * Codice meteo imposto dall'esterno, solo per la verifica automatica.
     * Nullo in uso normale: la schermata usa quello dell'ora scelta.
     */
    val forcedWeatherCode: Int? = null,
    /**
     * Angolo della scena imposto dall'esterno, in gradi, solo per la verifica
     * automatica. Nullo in uso normale: comanda il dito.
     *
     * Serve perche' i difetti che si vedono girando si vedono **girando**, e un
     * gesto simulato non arriva dove serve: per portare la cifra di taglio
     * servono quattrocento pixel di trascinamento, per vederla da dietro piu'
     * di ottocento, e uno schermo e' largo mille. Senza questo aggancio il
     * quarto di giro - che e' esattamente dove le matrici degenerano e le
     * pareti si scavalcano - non era fotografabile.
     */
    val forcedYawDeg: Float? = null,
    val place: Place = Place.FORLI,
    val unit: TempUnit = TempUnit.CELSIUS,
    val settingsOpen: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Place> = emptyList(),
    val searchError: String? = null,
) {
    val hours: List<HourForecast> get() = forecast?.hours.orEmpty()

    val hour: HourForecast? get() = hours.getOrNull(selectedHour)

    /** L'ora vera nella localita' mostrata, come indice nella barra. */
    val nowIndex: Int
        get() = forecast?.let { nearestHourIndex(it.hours, it.nowThere()) } ?: 0

    /**
     * Quanto e' alto il sole all'ora scelta.
     *
     * E' il valore da cui discende tutto il cielo: colore del fondo, giallo o
     * rosso del sole, comparsa della luna. Uno solo, cosi' si puo' animare fra
     * un'ora e l'altra senza che le tre cose si contraddicano a meta' strada.
     */
    val skyAltitude: Float
        get() {
            val moment = hour?.time ?: return 0.62f
            val day = forecast?.dayOf(moment)
            return SunClock.altitude(
                moment = moment,
                sunrise = day?.sunrise,
                sunset = day?.sunset,
                fallbackIsDay = hour?.isDay ?: true,
            )
        }
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = SettingsPrefs(app)

    private var loading: Job? = null
    private var searchJob: Job? = null

    /**
     * Ora richiesta prima che i dati arrivino. Serve alla verifica automatica:
     * l'intent puo' chiedere un'ora precisa mentre la lista e' ancora vuota, e
     * senza ricordarla la richiesta andrebbe persa.
     */
    private var pendingHour: Int? = null
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                val current = _state.value
                val moved = current.place != settings.place
                _state.update { it.copy(place = settings.place, unit = settings.unit) }
                // Cambiare unita' non deve costare una richiesta: la conversione
                // e' solo scrittura. Cambiare posto invece cambia tutto.
                if (moved || current.forecast == null) refresh()
            }
        }
    }

    /**
     * Ricarica con ritentativi a intervallo crescente.
     *
     * Senza, bastava aprire l'app in un momento di rete assente per restare
     * bloccati sui trattini finche' non la si chiudeva: la richiesta partiva una
     * volta sola e nessuno la ritentava. Capita davvero, all'avvio del telefono
     * o uscendo da una galleria.
     */
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        loading?.cancel()
        val place = _state.value.place
        loading = viewModelScope.launch {
            var wait = FIRST_RETRY_MS
            val repository = WeatherRepository(place)
            repeat(MAX_ATTEMPTS) { attempt ->
                val outcome = repository.load()
                outcome
                    .onSuccess { forecast ->
                        // All'apertura la schermata mostra l'ora corrente, non
                        // la prima disponibile: e' cio' che ci si aspetta di
                        // vedere. E l'ora corrente e' quella della localita',
                        // non quella dell'orologio di chi guarda.
                        val now = pendingHour
                            ?.coerceIn(0, (forecast.hours.size - 1).coerceAtLeast(0))
                            ?: nearestHourIndex(forecast.hours, forecast.nowThere())
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
                        val lastAttempt = attempt == MAX_ATTEMPTS - 1
                        _state.update {
                            it.copy(
                                loading = !lastAttempt,
                                error = if (lastAttempt) {
                                    failure.message ?: "Errore di rete"
                                } else {
                                    null
                                },
                            )
                        }
                    }
                if (outcome.isSuccess) return@launch
                delay(wait)
                wait *= 2
            }
        }
    }

    /** Riporta la schermata all'ora vera, quella segnata sulla barra. */
    fun backToNow() {
        pendingHour = null
        _state.update { it.copy(selectedHour = it.nowIndex) }
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

    fun openSettings() = _state.update { it.copy(settingsOpen = true) }

    fun closeSettings() =
        _state.update { it.copy(settingsOpen = false, query = "", results = emptyList()) }

    fun setUnit(unit: TempUnit) {
        viewModelScope.launch { prefs.setUnit(unit) }
    }

    fun choosePlace(place: Place) {
        // L'ora ricordata apparteneva al posto di prima. Tenerla significherebbe
        // aprire Singapore fermi sull'ora di Forli'.
        pendingHour = null
        viewModelScope.launch { prefs.setPlace(place) }
    }

    /**
     * Ricerca con attesa: un carattere digitato non e' una domanda, e mandare
     * una richiesta per ognuno riempirebbe la lista di risposte a query gia'
     * superate, che arrivano in ordine sparso.
     */
    fun search(query: String) {
        _state.update { it.copy(query = query, searchError = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searching = true) }
            WeatherRepository.search(query)
                .onSuccess { found ->
                    _state.update { it.copy(searching = false, results = found, searchError = null) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            searching = false,
                            results = emptyList(),
                            searchError = failure.message ?: "Ricerca non riuscita",
                        )
                    }
                }
        }
    }

    /** Aggancio per la cattura automatica: fissa l'ora anche prima dei dati. */
    fun requestHour(index: Int) {
        pendingHour = index
        if (_state.value.forecast != null) selectHour(index)
    }

    /** Aggancio per la cattura automatica: impone la condizione mostrata. */
    fun forceWeatherCode(code: Int?) {
        _state.update { it.copy(forcedWeatherCode = code) }
    }

    /** Aggancio per la cattura automatica: blocca la scena a un angolo. */
    fun forceYaw(degrees: Float?) {
        _state.update { it.copy(forcedYawDeg = degrees) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        const val FIRST_RETRY_MS = 1_200L
        const val SEARCH_DEBOUNCE_MS = 320L
    }
}
