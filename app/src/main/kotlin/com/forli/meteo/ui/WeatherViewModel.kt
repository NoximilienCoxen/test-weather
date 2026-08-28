package com.forli.meteo.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forli.meteo.data.DeviceLocation
import com.forli.meteo.data.Forecast
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Place
import com.forli.meteo.data.SunClock
import com.forli.meteo.data.WeatherModel
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.key
import com.forli.meteo.prefs.SettingsPrefs
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.home.nearestHourIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

data class UiState(
    val loading: Boolean = true,
    /**
     * Vero mentre si ricarica **avendo gia' qualcosa in mano**.
     *
     * Distinto da [loading] perche' le due situazioni non si somigliano: un
     * primo carico non ha niente da mostrare e lo deve dire, una ricarica ha
     * una schermata intera di dati validi e non deve toglierli di mezzo per
     * annunciare che ne sta cercando di piu' freschi.
     */
    val refreshing: Boolean = false,
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
    /** Motore numerico scelto per la previsione. */
    val model: WeatherModel = WeatherModel.AUTO,
    /** Localita' salvate a parte dalla scelta corrente. */
    val favorites: List<Place> = emptyList(),
    /** Vero quando il posto lo decide il telefono invece di una scelta a mano. */
    val followsLocation: Boolean = false,
    /** Vero mentre si sta chiedendo dove siamo. */
    val locating: Boolean = false,
    /**
     * Vero quando l'ultimo tentativo non ha prodotto un posto: permesso
     * negato, o nessun rilevamento in tempo utile. **Non e' un errore**, e'
     * una risposta: si resta dove si era e lo si dice, senza riprovare da soli.
     */
    val locationUnavailable: Boolean = false,
    /**
     * Falso finche' il benvenuto non ha fatto il suo lavoro: e' la schermata
     * che chiede dove sei, e prima non c'era **nessun momento** in cui l'app lo
     * chiedesse. Chi non entrava nelle impostazioni restava per sempre
     * sull'ultima localita' impostata senza sapere che ce n'era un'altra.
     */
    val welcomed: Boolean = true,
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
        get() {
            val current = forecast ?: return 0
            val hours = current.hours
            if (hours.isEmpty()) return 0
            // Le ore sono contigue e a passo di un'ora: l'indice e' una
            // sottrazione, non una ricerca. Scorrerle tutte costava una
            // `Duration` allocata per ognuna, e questa proprieta' viene letta
            // piu' volte a ogni ricomposizione della schermata - cioe' a ogni
            // ora scorsa sulla barra.
            val minutes = Duration.between(hours.first().time, current.nowThere()).toMinutes()
            return Math.floorDiv(minutes + 30L, 60L).toInt().coerceIn(0, hours.lastIndex)
        }

    /**
     * Da quando il dato in mano e' quello che e'.
     *
     * Nullo finche' non ne esiste uno. Serve a dire in alto quanto e' vecchio:
     * un'app meteo che mostra ieri sera con la stessa faccia di adesso e'
     * peggio di un'app che ammette di non sapere.
     */
    val fetchedAt: LocalDateTime? get() = forecast?.fetchedAt

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

    /**
     * A che punto del viaggio sta l'astro, da quando sorge a quando tramonta.
     *
     * Serve accanto all'altezza e non al posto suo: l'altezza dice **quanto e'
     * alto**, questa da **che parte sta andando**. Con la sola altezza il sole
     * salirebbe e ridiscenderebbe dallo stesso lato, perche' alle otto e alle
     * sedici vale lo stesso numero.
     */
    val skyJourney: Float
        get() {
            val moment = hour?.time ?: return 0.5f
            val day = forecast?.dayOf(moment)
            return SunClock.journey(moment, day?.sunrise, day?.sunset)
        }
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = SettingsPrefs(app)

    private var loading: Job? = null
    private var searchJob: Job? = null
    private var locating: Job? = null

    /**
     * Ora richiesta prima che i dati arrivino. Serve alla verifica automatica:
     * l'intent puo' chiedere un'ora precisa mentre la lista e' ancora vuota, e
     * senza ricordarla la richiesta andrebbe persa.
     */
    private var pendingHour: Int? = null

    /** Vero da quando la posizione e' stata chiesta all'avvio: una volta basta. */
    private var started = false

    /**
     * Vero quando l'aggancio di verifica ha chiesto di rivedere il benvenuto.
     *
     * Una bandiera a parte e non un valore mescolato a quello delle preferenze:
     * queste emettono a ogni cambiamento, anche di tutt'altro, e una condizione
     * che le intreccia rimetterebbe il benvenuto davanti a chi lo ha appena
     * chiuso solo perche' nel frattempo ha cambiato unita' di misura.
     */
    private var welcomeForced = false

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                val current = _state.value
                val moved = current.place != settings.place
                val modelChanged = current.model != settings.model
                val firstRead = !started
                started = true
                _state.update {
                    it.copy(
                        place = settings.place,
                        unit = settings.unit,
                        model = settings.model,
                        favorites = settings.favorites,
                        followsLocation = settings.followsLocation,
                        welcomed = settings.welcomed && !welcomeForced,
                    )
                }
                // Cambiare unita' non deve costare una richiesta: la conversione
                // e' solo scrittura. Cambiare posto o modello invece cambia
                // tutto, perche' i numeri arrivano da un motore diverso.
                if (moved || modelChanged || current.forecast == null) refresh()

                // All'avvio, se il posto lo decide il telefono, lo si richiede
                // una volta. Il posto salvato resta valido nel frattempo: la
                // schermata ha subito qualcosa da mostrare invece di aspettare
                // un satellite davanti al vuoto.
                if (firstRead && settings.followsLocation) locate(explicit = false)
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
        // Un primo carico non ha niente da mostrare e lo dichiara; una ricarica
        // lascia la schermata dov'e' e cambia solo il segno in alto.
        val hasData = _state.value.forecast != null
        _state.update { it.copy(loading = !hasData, refreshing = hasData, error = null) }
        loading?.cancel()
        val place = _state.value.place
        val model = _state.value.model
        loading = viewModelScope.launch {
            var wait = FIRST_RETRY_MS
            val repository = WeatherRepository(place, model)
            repeat(MAX_ATTEMPTS) { attempt ->
                val outcome = repository.load()
                outcome
                    .onSuccess { forecast ->
                        // L'unico log dell'app, e non serve a chi sviluppa:
                        // serve alla cattura in CI, che finora aspettava **a
                        // tempo** che i dati arrivassero. Un'attesa a tempo e'
                        // una scommessa sulla rete del runner, e la scommessa
                        // si perde: otto secondi non bastavano, quattordici
                        // nemmeno, e a diciannove uno scatto su undici e'
                        // uscito lo stesso "IN ATTESA DEI DATI". Con una riga
                        // qui l'attesa smette di essere una durata e diventa
                        // una condizione.
                        Log.i(TAG, "previsione pronta: ${forecast.hours.size} ore")
                        _state.update { current ->
                            val last = (forecast.hours.size - 1).coerceAtLeast(0)
                            val hour = when {
                                // L'aggancio di verifica vince su tutto.
                                pendingHour != null -> pendingHour!!.coerceIn(0, last)
                                // Su una **ricarica** l'ora scelta resta quella:
                                // chi stava guardando le sei di sera non deve
                                // ritrovarsi sbalzato ad adesso solo perche' e'
                                // arrivata una risposta dalla rete.
                                current.forecast != null -> current.selectedHour.coerceIn(0, last)
                                // All'apertura invece si mostra l'ora corrente,
                                // non la prima disponibile: e' cio' che ci si
                                // aspetta di vedere. Ed e' l'ora della
                                // localita', non quella dell'orologio di chi
                                // guarda.
                                else -> nearestHourIndex(forecast.hours, forecast.nowThere())
                            }
                            current.copy(
                                loading = false,
                                refreshing = false,
                                forecast = forecast,
                                error = null,
                                selectedHour = hour,
                            )
                        }
                    }
                    .onFailure { failure ->
                        val lastAttempt = attempt == MAX_ATTEMPTS - 1
                        _state.update {
                            // Una ricarica fallita non cancella quello che c'e'
                            // gia': si tiene il dato vecchio e si continua a
                            // dire quanto e' vecchio. E' l'unica risposta utile
                            // a chi e' senza rete.
                            it.copy(
                                loading = !lastAttempt && it.forecast == null,
                                refreshing = !lastAttempt && it.forecast != null,
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

    /**
     * Ricarica se quello che si ha in mano ha passato la sua eta'.
     *
     * La chiama il ritorno in primo piano. Senza, `refresh()` partiva solo
     * all'avvio e al cambio di localita' e **nient'altro la richiamava mai**:
     * un'app lasciata aperta ieri sera mostrava ieri sera, senza modo di
     * accorgersene ne' di ricaricare se non chiudendola.
     */
    fun refreshIfStale(maxAge: Duration = STALE_AFTER) {
        val fetched = _state.value.fetchedAt
        if (fetched == null || Duration.between(fetched, LocalDateTime.now()) >= maxAge) {
            refresh()
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

    fun setModel(model: WeatherModel) {
        viewModelScope.launch { prefs.setModel(model) }
    }

    /** Aggiunge o toglie la localita' dai preferiti, a seconda che ci sia gia'. */
    fun toggleFavorite(place: Place) {
        viewModelScope.launch { prefs.toggleFavorite(place) }
    }

    fun isFavorite(place: Place): Boolean =
        _state.value.favorites.any { it.key == place.key }

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

    /** Il benvenuto ha finito: da qui in poi si apre sulla schermata vera. */
    fun dismissWelcome() {
        welcomeForced = false
        _state.update { it.copy(welcomed = true) }
        viewModelScope.launch { prefs.setWelcomed() }
    }

    /** Aggancio per la cattura automatica: rimostra il benvenuto. */
    fun showWelcome() {
        welcomeForced = true
        _state.update { it.copy(welcomed = false) }
    }

    /**
     * Chiede al telefono dove siamo e ci si trasferisce.
     *
     * La chiama il benvenuto, o la schermata delle impostazioni, dopo aver
     * ottenuto il permesso: un ViewModel non puo' chiederlo, e non deve
     * provarci.
     */
    fun useDeviceLocation() = locate(explicit = true)

    private fun locate(explicit: Boolean) {
        locating?.cancel()
        _state.update { it.copy(locating = true, locationUnavailable = false) }
        locating = viewModelScope.launch {
            // Tutto qui dentro parla col sistema operativo o col disco - il
            // gestore di posizione, il geocoder, e infine la scrittura su
            // DataStore. Nessuno di questi e' garantito: un file delle
            // preferenze corrotto, per dire, fa fallire `prefs.setPlace` con
            // un'eccezione che altrimenti risalirebbe non presa fino a far
            // cadere l'app. E' la stessa filosofia di "permesso negato non e'
            // un errore" applicata a tutta la catena, non solo al permesso.
            try {
                val found = DeviceLocation.current(getApplication<Application>())
                if (found == null) {
                    // Permesso negato, posizione spenta, o nessun rilevamento in
                    // tempo utile. Si resta dove si era: e' l'unica risposta utile,
                    // e riprovare da soli sarebbe insistere.
                    // Solo quando l'ha chiesto qualcuno lo si dice: un tentativo
                    // all'avvio che non riesce non deve mettere un avviso davanti a
                    // chi non ha chiesto niente.
                    _state.update { it.copy(locating = false, locationUnavailable = explicit) }
                    return@launch
                }
                _state.update { it.copy(locating = false, locationUnavailable = false) }
                // Da qui in poi comanda il flusso delle preferenze, come per una
                // localita' scelta a mano: una sola strada per cambiare posto.
                pendingHour = null
                prefs.setPlace(found, following = true)
            } catch (e: CancellationException) {
                // Un tentativo nuovo ha appena cancellato questo (vedi
                // `locating?.cancel()` sopra): non e' un fallimento, e va
                // rilanciata, non inghiottita - altrimenti la cancellazione
                // strutturata smette di funzionare.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Localizzazione non riuscita", e)
                _state.update { it.copy(locating = false, locationUnavailable = explicit) }
            }
        }
    }

    /** Aggancio per la cattura automatica: blocca la scena a un angolo. */
    fun forceYaw(degrees: Float?) {
        _state.update { it.copy(forcedYawDeg = degrees) }
    }

    private companion object {
        /**
         * Oltre questa eta' il dato si ricarica da solo tornando in primo
         * piano. Venti minuti: la previsione di Open-Meteo si muove per ore,
         * ma la barra deve almeno riguardare il giorno giusto.
         */
        val STALE_AFTER: Duration = Duration.ofMinutes(20)

        /**
         * L'etichetta del log. Il filtro di `capture.sh` cerca gia' "meteo"
         * fra le righe che tiene, quindi questa riga finisce anche nel
         * logcat allegato agli scatti senza doverlo cambiare.
         */
        const val TAG = "meteo"

        const val MAX_ATTEMPTS = 4
        const val FIRST_RETRY_MS = 1_200L
        const val SEARCH_DEBOUNCE_MS = 320L
    }
}
