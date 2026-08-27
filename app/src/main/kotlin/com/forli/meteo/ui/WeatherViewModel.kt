package com.forli.meteo.ui

import android.app.Application
import com.forli.meteo.data.DeviceLocation
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Place
import com.forli.meteo.data.SunClock
import com.forli.meteo.data.UpdateCheck
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.Wind
import com.forli.meteo.data.Wmo
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
     * Vento imposto dall'esterno, in metri al secondo. Solo per la verifica.
     *
     * Separato da [forcedWeatherCode] perche' sono due domande diverse, e
     * tenerle insieme ha gia' ingannato una misura: imponendo il vento
     * **insieme** al codice, ogni stato di prova risultava ventoso, quindi
     * nessuno risultava fermo, quindi il conteggio dei fotogrammi a riposo non
     * misurava mai il riposo.
     */
    val forcedWindSpeed: Float? = null,
    val place: Place = Place.FORLI,
    val unit: TempUnit = TempUnit.CELSIUS,
    val settingsOpen: Boolean = false,
    /** Falso finche' il benvenuto non e' stato superato. */
    val welcomed: Boolean = true,
    /** Vero mentre si sta chiedendo la posizione al telefono. */
    val locating: Boolean = false,
    /** Perche' la posizione non e' arrivata. Nullo se e' andata bene. */
    val locationProblem: String? = null,
    /** Vero se la localita' mostrata l'ha trovata il telefono. */
    val located: Boolean = false,
    /**
     * Vero quando sul rilascio a tag fisso c'e' una build piu' recente di
     * questa. Falso anche in caso di dubbio: vedi [UpdateCheck].
     */
    val updateReady: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Place> = emptyList(),
    val searchError: String? = null,
) {
    val hours: List<HourForecast> get() = forecast?.hours.orEmpty()

    val hour: HourForecast? get() = hours.getOrNull(selectedHour)

    /**
     * Il codice che comanda la scena.
     *
     * Uno solo, e letto da un posto solo: quando l'imposizione della verifica e
     * il dato vero venivano consultati separatamente in punti diversi, bastava
     * dimenticarne uno perche' meta' schermata mostrasse il temporale imposto e
     * l'altra meta' il sereno vero.
     */
    val activeWeatherCode: Int? get() = forcedWeatherCode ?: hour?.weatherCode

    /**
     * Quanto si e' dentro l'ora dorata, da 0 a 1.
     *
     * Non si deduce da [skyAltitude]: quella dice quanto e' giorno, non quanti
     * minuti mancano al tramonto.
     */
    val goldenHour: Float
        get() {
            val moment = hour?.time ?: return 0f
            val day = forecast?.dayOf(moment) ?: return 0f
            return SunClock.goldenness(moment, day.sunrise, day.sunset)
        }

    /** Quanto e' fitta la nebbia all'ora mostrata. */
    val fogDensity: Float get() = Wmo.fogDensity(activeWeatherCode, skyAltitude)

    /**
     * Il vento dell'ora mostrata.
     *
     * Con un codice imposto dalla verifica il vento vero non c'entra piu'
     * niente: si sta guardando una scena costruita, e va inclinata anche lei,
     * altrimenti l'aggancio mostrerebbe l'unica neve al mondo che scende
     * perfettamente a piombo.
     */
    val wind: Wind
        get() = when {
            forcedWindSpeed != null -> Wind(speed = forcedWindSpeed, fromDegrees = 250f)
            // Con una condizione imposta il vento vero non c'entra piu' niente:
            // si sta guardando una scena costruita, e va inclinata anche lei,
            // altrimenti l'aggancio mostrerebbe l'unica neve al mondo che
            // scende perfettamente a piombo. Chi vuole la bonaccia la chiede.
            forcedWeatherCode != null -> Wind(speed = 6.5f, fromDegrees = 250f)
            else -> Wind.of(hour?.windSpeed, hour?.windDirection)
        }

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
        // Una volta sola, all'avvio, e in silenzio se non c'e' niente da dire.
        // Non e' un controllo periodico: l'app la si apre per sapere che tempo
        // fa, e ricontrollare a ogni ora scelta sarebbe una richiesta di rete
        // in piu' per un'informazione che cambia una volta al giorno.
        viewModelScope.launch {
            if (UpdateCheck.check() is UpdateCheck.Result.Available) {
                _state.update { it.copy(updateReady = true) }
            }
        }
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                val current = _state.value
                val moved = current.place != settings.place
                _state.update {
                    it.copy(
                        place = settings.place,
                        unit = settings.unit,
                        // L'aggancio di verifica vince: una volta saltato, il
                        // benvenuto non puo' tornare per un'emissione delle
                        // preferenze arrivata dopo.
                        welcomed = it.welcomed || settings.welcomed,
                        located = settings.located,
                    )
                }
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
     * Chiede al telefono dove si trova.
     *
     * Non lancia mai: [DeviceLocation] restituisce un esito anche quando tutto
     * va storto, e ogni esito ha una frase. Il pulsante che chiama questo metodo
     * puo' essere premuto senza permesso, senza servizi di localizzazione,
     * senza servizi Google e su un dispositivo che non ha mai visto un
     * satellite: in nessuno di quei casi l'app deve chiudersi.
     *
     * @param canAsk falso quando il permesso e' gia' stato negato e chi chiama
     *   non ha modo di richiederlo: allora si dice cosa fare invece di rimandare
     *   a una richiesta che il sistema non mostrerebbe piu'.
     */
    fun locateMe(canAsk: Boolean = true, onNeedsPermission: () -> Unit = {}) {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true, locationProblem = null) }
        viewModelScope.launch {
            val outcome = runCatching { DeviceLocation.current(getApplication()) }
                .getOrElse { DeviceLocation.Outcome.Unavailable }
            when (outcome) {
                is DeviceLocation.Outcome.Found -> {
                    pendingHour = null
                    prefs.setPlace(outcome.place, located = true)
                    prefs.setWelcomed()
                    _state.update { it.copy(locating = false, locationProblem = null) }
                }

                DeviceLocation.Outcome.NeedsPermission -> {
                    _state.update {
                        it.copy(
                            locating = false,
                            locationProblem = if (canAsk) {
                                null
                            } else {
                                "PERMESSO NEGATO. SI PUÒ CONCEDERE " +
                                    "DALLE IMPOSTAZIONI DI ANDROID."
                            },
                        )
                    }
                    if (canAsk) onNeedsPermission()
                }

                DeviceLocation.Outcome.Unavailable -> _state.update {
                    it.copy(
                        locating = false,
                        locationProblem = "LOCALIZZAZIONE SPENTA O NON DISPONIBILE " +
                            "SU QUESTO DISPOSITIVO.",
                    )
                }

                DeviceLocation.Outcome.Timeout -> _state.update {
                    it.copy(
                        locating = false,
                        locationProblem = "IL TELEFONO NON HA TROVATO LA POSIZIONE. " +
                            "RIPROVA, O SCEGLI UNA CITTÀ.",
                    )
                }
            }
        }
    }

    /**
     * L'avviso di aggiornamento e' stato visto.
     *
     * Non viene ricordato oltre la sessione, ed e' voluto: chi lo chiude non
     * sta dicendo "mai piu'", sta dicendo "adesso no". Riaprendo l'app dopo
     * aver aggiornato l'avviso non torna comunque, perche' [UpdateCheck] non lo
     * troverebbe piu'.
     */
    fun dismissUpdate() = _state.update { it.copy(updateReady = false) }

    /** Il benvenuto e' stato superato: non si ripropone. */
    fun dismissWelcome() {
        viewModelScope.launch { prefs.setWelcomed() }
    }

    /**
     * Aggancio per la verifica automatica: salta il benvenuto senza ricordarlo.
     *
     * Serve perche' il benvenuto, comparendo al primo avvio, si mette davanti a
     * **ogni** scatto dell'emulatore: la CI fotografava dodici volte la stessa
     * domanda invece della scena. Non passa da [prefs] di proposito - segnare
     * come "gia' visto" un benvenuto che nessuno ha visto sarebbe uno stato
     * scritto sul disco da un aggancio di prova, e resterebbe li' anche dopo.
     */
    fun skipWelcome() = _state.update { it.copy(welcomed = true) }

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

    /** Aggancio per la cattura automatica: impone il vento, zero compreso. */
    fun forceWind(metresPerSecond: Float?) {
        _state.update { it.copy(forcedWindSpeed = metresPerSecond) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        const val FIRST_RETRY_MS = 1_200L
        const val SEARCH_DEBOUNCE_MS = 320L
    }
}
