package io.github.noximiliencoxen.caelum.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.noximiliencoxen.caelum.data.AirQuality
import io.github.noximiliencoxen.caelum.data.AlertKind
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.AirQualityRepository
import io.github.noximiliencoxen.caelum.data.DeviceLocation
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.SunClock
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.WeatherAlertsRepository
import io.github.noximiliencoxen.caelum.data.WeatherModel
import io.github.noximiliencoxen.caelum.data.WeatherRepository
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.derivedAlerts
import io.github.noximiliencoxen.caelum.data.mergeAlerts
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.prefs.SettingsPrefs
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.home.nearestHourIndex
import io.github.noximiliencoxen.caelum.ui.temperature.DetailMode
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
    /**
     * La qualita' dell'aria adesso, o nulla se non e' (ancora) arrivata.
     *
     * Sta su un altro host e arriva per conto suo, dopo la previsione: e' un
     * arricchimento, non un dato senza il quale la schermata non ha senso.
     * `AirQualityRepository` esisteva gia' e finora lo interrogava soltanto un
     * widget - in app quei numeri non si vedevano da nessuna parte.
     */
    val air: AirQuality? = null,
    /** Vero quando l'ultima richiesta di qualita' dell'aria non e' riuscita. */
    val airUnavailable: Boolean = false,
    /**
     * Le allerte in corso per la localita' mostrata, la piu' grave per prima.
     *
     * Come [air], sono un arricchimento che arriva dopo la previsione e per
     * conto suo. A differenza di [air] hanno **due sorgenti**: i bollettini
     * ufficiali di MeteoAlarm dove ci sono, e le soglie calcolate sui dati gia'
     * scaricati dove non ci sono. Quale delle due lo dice ogni allerta con il
     * proprio `official`, perche' il peso delle due affermazioni e' diverso.
     */
    val alerts: List<WeatherAlert> = emptyList(),
    /**
     * Vero quando il feed ufficiale non ha risposto **e la localita' sarebbe
     * coperta**.
     *
     * Distinto dal caso "fuori copertura", che non e' un guasto: in Nuova
     * Zelanda MeteoAlarm non deve rispondere, e dirlo come se fosse un errore
     * insegnerebbe a ignorare l'avviso quando invece e' vero.
     */
    val alertsUnavailable: Boolean = false,
    /** Vero mentre e' aperto il foglio con i bollettini per esteso. */
    val alertsOpen: Boolean = false,
    /**
     * Le allerte per cui la fascia e' gia' stata ridotta a pallino, e il peso
     * del livello peggiore fra quelle. Arrivano dalle impostazioni come le
     * altre scelte, e insieme decidono [alertsCollapsed].
     */
    val dismissedAlertIds: Set<String> = emptySet(),
    val dismissedAlertWeight: Int = 0,
    /**
     * Allerta imposta dall'esterno, solo per la verifica automatica.
     *
     * Sta accanto a [forcedWeatherCode] e [forcedYawDeg] e si applica **in
     * lettura**, come loro: scriverla dentro [alerts] non sarebbe bastato,
     * perche' il primo caricamento che arriva sovrascrive quella lista con le
     * allerte vere e lo scatto uscirebbe senza fascia. Al lettore serve che
     * resti finche' l'app e' viva.
     */
    val forcedAlert: WeatherAlert? = null,
    /** Indice del giorno selezionato nella striscia in fondo. 0 = oggi. */
    val selectedDay: Int = 0,
    /** false = GIORNO (valori correnti), true = SETTIMANA (valori del giorno). */
    val weekMode: Boolean = false,
    /** Quale grandezza mostra la schermata di dettaglio. */
    val detailMode: DetailMode = DetailMode.TEMPERATURA,
    /**
     * Il giorno aperto nel dettaglio, o nullo se quella schermata e' chiusa.
     *
     * Distinto da [selectedDay] apposta: `selectedDay` dice **quale giorno si
     * sta guardando** e sopravvive alla chiusura, questo dice **se la schermata
     * e' in scena**. Con un campo solo, tornare indietro avrebbe voluto dire
     * dimenticare anche il giorno.
     */
    val dayDetail: Int? = null,
    /** false = EFFETTIVA, true = PERCEPITI, nel dettaglio del giorno. */
    val feelsLike: Boolean = false,
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
    /**
     * Le allerte da mettere in scena: quella imposta se c'e', se no le vere.
     *
     * Le schermate leggono **questa** e non [alerts], cosi' l'aggancio di
     * verifica non ha bisogno che nessuno se ne ricordi.
     */
    val shownAlerts: List<WeatherAlert>
        get() = forcedAlert?.let { listOf(it) } ?: alerts

    /**
     * Vero quando la fascia va disegnata come un pallino invece che per esteso.
     *
     * La regola sta qui e non nelle impostazioni perche' dipende da **cosa c'e'
     * adesso**, non solo da cosa e' stato chiuso: la fascia resta ridotta se e
     * solo se ogni allerta in scena era gia' fra quelle chiuse **e** la
     * peggiore di adesso non e' piu' grave della peggiore di allora.
     *
     * Due conseguenze volute:
     * - un'allerta **nuova** riapre la fascia, anche se le vecchie erano state
     *   chiuse. Un avviso che compare mentre il pallino e' chiuso resterebbe
     *   altrimenti un puntino in un angolo, ed e' esattamente il caso in cui
     *   avvisare conta;
     * - un **peggioramento** riapre la fascia pur senza allerte nuove: la
     *   gialla di stamattina che diventa arancione ha lo stesso identificativo
     *   e non e' piu' la stessa notizia.
     *
     * Una che **scade**, invece, non la riapre: la condizione e' per
     * inclusione, non per uguaglianza degli insiemi. Se ne restano due su tre
     * gia' viste, non e' successo niente di nuovo.
     */
    val alertsCollapsed: Boolean
        get() {
            val shown = shownAlerts
            if (shown.isEmpty()) return false
            val worst = shown.maxOf { it.level.weight }
            return worst <= dismissedAlertWeight && shown.all { it.id in dismissedAlertIds }
        }

    val hours: List<HourForecast> get() = forecast?.hours.orEmpty()

    val hour: HourForecast? get() = hours.getOrNull(selectedHour)

    /**
     * L'ora da mostrare nel dettaglio: quella scelta, ma **sul giorno scelto**.
     *
     * [hours] copre solo oggi e [selectedHour] conta su quella lista, quindi
     * con un giorno diverso da oggi il dettaglio mostrava i valori di oggi
     * sotto un'intestazione che annunciava un altro giorno. Qui si va a
     * prendere la stessa ora sul giorno giusto; nulla se quel giorno non ha
     * quell'ora, che e' meglio di un numero preso altrove.
     */
    val detailHour: HourForecast?
        get() {
            val current = forecast ?: return null
            if (selectedDay == 0) return hour
            val date = current.days.getOrNull(selectedDay)?.date ?: return hour
            val clock = hour?.time?.hour ?: return null
            return current.hourOn(date, clock)
        }

    /** Il giorno aperto dal dettaglio, dentro i limiti di cio' che esiste. */
    val detailDay: io.github.noximiliencoxen.caelum.data.DayForecast?
        get() = forecast?.days?.getOrNull(selectedDay)

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

    /**
     * Se l'ora scelta guarda verso il mattino o verso la sera.
     *
     * Alba e tramonto hanno la stessa altezza del sole e colori diversi: uno e'
     * rosa e freddo, l'altro arancio e caldo. Senza questo valore il cielo non
     * ha modo di sapere quale dei due sta dipingendo.
     */
    val skyEvening: Float
        get() {
            val moment = hour?.time ?: return 0.5f
            val day = forecast?.dayOf(moment)
            return SunClock.eveningness(moment, day?.sunrise, day?.sunset)
        }

    /**
     * Quanto e' coperto il cielo all'ora scelta.
     *
     * Sta accanto ai valori del sole e non dentro di loro perche' non e'
     * astronomia: il sole sta dov'e' anche sotto le nuvole. Serve al fondo, che
     * deve poter smettere di essere azzurro quando non c'e' niente di azzurro
     * da mostrare.
     *
     * **Legge [forcedWeatherCode] per primo**, come fa gia' la scultura. Senza,
     * lo scatto di verifica del coperto usciva con la nuvola giusta sopra un
     * cielo azzurro: la scultura obbediva all'ora imposta e il fondo no, cioe'
     * proprio la regola che quello scatto doveva dimostrare non si vedeva.
     */
    val skyCloudiness: Float
        get() = Wmo.cloudiness(forcedWeatherCode ?: hour?.weatherCode)
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

    /**
     * Giorno di cui aprire il dettaglio, chiesto prima che i dati arrivino.
     *
     * Stessa ragione di [pendingHour]: l'intent arriva all'avvio, la previsione
     * qualche secondo dopo, e `openDayDetail` senza dati stringerebbe l'indice
     * a zero - aprirebbe sempre oggi, qualunque giorno si sia chiesto.
     */
    private var pendingDay: Int? = null

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
                        dismissedAlertIds = settings.dismissedAlertIds,
                        dismissedAlertWeight = settings.dismissedAlertWeight,
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
        // L'aria misurata appartiene al posto da cui viene: tenerla mentre si
        // carica un'altra citta' vorrebbe dire attribuire a Bergen le polveri
        // di Forli' per il tempo di una richiesta.
        if (_state.value.forecast?.place?.key != place.key) {
            // Le allerte seguono la stessa regola dell'aria, e per un motivo
            // piu' serio: un'allerta rossa lasciata in scena mentre si carica
            // un'altra citta' dice a chi guarda che il pericolo e' dove si
            // trova lui.
            _state.update {
                it.copy(
                    air = null,
                    airUnavailable = false,
                    alerts = emptyList(),
                    alertsUnavailable = false,
                )
            }
        }
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
                                val lastDay = (forecast.days.size - 1).coerceAtLeast(0)
                                val wantedDay = pendingDay?.coerceIn(0, lastDay)
                                current.copy(
                                    loading = false,
                                    refreshing = false,
                                    forecast = forecast,
                                    error = null,
                                    selectedHour = hour,
                                    selectedDay = wantedDay ?: current.selectedDay,
                                    dayDetail = wantedDay ?: current.dayDetail,
                                )
                            }

                            // La qualita' dell'aria vive su un altro host: si
                            // chiede a parte e senza far aspettare nessuno. Se
                            // non arriva, la pagina ARIA lo dichiara invece di
                            // mostrare una colonna di trattini muti.
                            viewModelScope.launch {
                                AirQualityRepository(place).load()
                                    .onSuccess { air ->
                                        _state.update { it.copy(air = air, airUnavailable = false) }
                                    }
                                    .onFailure {
                                        _state.update { it.copy(airUnavailable = true) }
                                    }
                            }

                            // Le allerte: prima i bollettini ufficiali, e le
                            // soglie a coprire cio' che quelli non dicono.
                            //
                            // Le derivate si calcolano **subito e in ogni
                            // caso**, perche' sono gratis - i numeri sono gia'
                            // qui - e perche' cosi' la fascia compare senza
                            // aspettare una risposta di rete. Quando
                            // l'ufficiale arriva, rimpiazza le derivate dello
                            // stesso fenomeno.
                            val derived = derivedAlerts(forecast)
                            _state.update { it.copy(alerts = derived) }
                            viewModelScope.launch {
                                WeatherAlertsRepository(place).load()
                                    .onSuccess { official ->
                                        _state.update {
                                            it.copy(
                                                alerts = mergeAlerts(official, derived),
                                                alertsUnavailable = false,
                                            )
                                        }
                                    }
                                    .onFailure { failure ->
                                        // Fuori copertura non e' un guasto: si
                                        // resta sulle derivate senza dire che
                                        // qualcosa e' andato storto, perche'
                                        // non e' andato storto niente.
                                        val broken =
                                            failure !is WeatherAlertsRepository.OutOfCoverage
                                        _state.update {
                                            it.copy(
                                                alerts = derived,
                                                alertsUnavailable = broken,
                                            )
                                        }
                                    }
                            }

                            // La Norma storica arriva dopo, in background, e
                            // aggiorna i giorni gia' visibili senza bloccare la
                            // schermata. Se fallisce non succede nulla: il grafico
                            // la mostra solo quando c'e'.
                            viewModelScope.launch {
                                WeatherRepository.loadNorm(place, forecast.days)
                                    .onSuccess { norms ->
                                        if (norms.isEmpty()) return@onSuccess
                                        _state.update { current ->
                                            val f = current.forecast ?: return@update current
                                            current.copy(
                                                forecast = f.copy(
                                                    days = f.days.map { day ->
                                                        day.copy(normTemp = norms[day.date])
                                                    },
                                                ),
                                            )
                                        }
                                    }
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

    fun setDetailMode(mode: DetailMode) = _state.update { it.copy(detailMode = mode) }

    /**
     * Un'allerta finta, solo per la verifica automatica.
     *
     * Sta accanto a `forceWeatherCode` e `forceYaw` e per la stessa ragione: i
     * difetti di un riquadro che compare col maltempo si vedono **col
     * maltempo**, e aspettare che la Protezione Civile emetta un avviso su
     * Forli' non e' un piano di verifica. Il ricarico successivo la sostituisce
     * con quelle vere, che e' il comportamento giusto - non e' uno stato in cui
     * l'app possa restare bloccata.
     */
    fun forceAlert(level: Int) {
        val chosen = when (level) {
            1 -> AlertLevel.GIALLA
            2 -> AlertLevel.ARANCIONE
            else -> AlertLevel.ROSSA
        }
        _state.update {
            it.copy(
                forcedAlert =
                    WeatherAlert(
                        id = "prova-${chosen.name}",
                        level = chosen,
                        kind = AlertKind.TEMPORALI,
                        headline = "Temporali forti dal pomeriggio",
                        description = "Rovesci e temporali sparsi, localmente intensi, " +
                            "con possibili grandinate e forti raffiche di vento.",
                        instruction = "Evitare i sottopassi e i corsi d'acqua.",
                        areaDesc = it.place.name,
                        source = "Aggancio di verifica",
                        official = true,
                    ),
            )
        }
    }

    fun openAlerts() = _state.update { it.copy(alertsOpen = true) }

    fun closeAlerts() = _state.update { it.copy(alertsOpen = false) }

    /**
     * Riduce la fascia dell'allerta al pallino.
     *
     * Si salvano gli identificativi di **cio' che c'e' adesso**, non un
     * booleano: la regola che decide se la fascia torna intera e'
     * [UiState.alertsCollapsed], e ha bisogno di sapere cosa e' stato chiuso.
     */
    fun collapseAlerts() {
        val shown = _state.value.shownAlerts
        if (shown.isEmpty()) return
        val ids = shown.map { it.id }.toSet()
        val weight = shown.maxOf { it.level.weight }
        viewModelScope.launch { prefs.dismissAlerts(ids, weight) }
    }

    /**
     * Rimette la fascia intera. E' cio' che fa toccare il pallino, insieme ad
     * aprire il bollettino: un gesto solo, e chi torna indietro ritrova la riga
     * dov'era invece di dover cercare come farla riapparire.
     */
    fun expandAlerts() {
        viewModelScope.launch { prefs.restoreAlertBar() }
    }

    /**
     * Apre il dettaglio di un giorno preciso, toccandolo nella card della
     * settimana. Porta con se' anche `selectedDay`, cosi' le linguette in cima
     * alla schermata nuova si aprono gia' sul giorno toccato.
     */
    fun openDayDetail(index: Int) {
        _state.update { current ->
            val last = (current.forecast?.days?.size ?: 1) - 1
            val day = index.coerceIn(0, maxOf(last, 0))
            current.copy(selectedDay = day, dayDetail = day)
        }
    }

    fun closeDayDetail() {
        pendingDay = null
        _state.update { it.copy(dayDetail = null) }
    }

    fun setFeelsLike(feels: Boolean) = _state.update { it.copy(feelsLike = feels) }

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

    /**
     * Aggancio per la cattura automatica: apre il dettaglio di un giorno.
     *
     * Esiste perche' raggiungerlo col dito **non si puo' fare in modo
     * affidabile**: la settimana sta in coda a una pagina che scorre, quindi
     * bisogna prima scorrere, e la trascinata lunga necessaria a farlo fa
     * morire l'emulatore della CI (vedi CONTESTO, trappola sull'emulatore).
     * E' lo stesso motivo per cui esiste l'aggancio sul giro: certi stati col
     * dito, li', non si raggiungono.
     */
    fun requestDayDetail(index: Int) {
        pendingDay = index
        if (_state.value.forecast != null) openDayDetail(index)
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
