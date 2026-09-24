package io.github.noximiliencoxen.caelum.ui.sala

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.CardTheme
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.data.MoonPhase
import io.github.noximiliencoxen.caelum.ui.motion.VibrazioniDellaScena
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
import io.github.noximiliencoxen.caelum.ui.motion.sistemaSenzaAnimazioni
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaAriaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaLunaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaOggiScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaPioggiaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaSettimanaScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaUvScreen
import io.github.noximiliencoxen.caelum.ui.sala.rooms.SalaVentoScreen
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Caelum: sette sale in un carosello verticale, **davanti a un cielo solo**.
 *
 * ### Cos'e' cambiato rispetto alla galleria di carta
 *
 * Prima ogni sala si disegnava il proprio fondo, perche' quel fondo era una
 * pagina e una pagina appartiene a chi la scrive. Adesso il fondo e' il tempo:
 * il cielo, le colline, cio' che cade stanno **qui**, una volta sola, e le
 * sette sale sono pannelli che ci passano davanti. Scorrendo il carosello il
 * cielo non ricomincia - resta, e questo e' esattamente il punto della
 * direzione scelta nel prototipo.
 *
 * Ne segue che anche tutto il resto della cornice vive qui: l'intestazione con
 * gli avvisi, la colonna delle scorciatoie sul fianco, la barra delle ore e i
 * sette trattini del percorso. Sono gli stessi a ogni sala, e ripeterli sette
 * volte vorrebbe dire che alla prima rifinitura sei restano indietro.
 *
 * ### Dove vivono le animazioni
 *
 * Tutte qui, e in nessun altro posto. Le sale ricevono numeri **gia' smorzati**
 * - la tavolozza, la scena - e non sanno che esista una molla. E' cio' che
 * permette di cambiare il modo in cui un colore passa senza aprire sette file.
 */
@Composable
fun SalaShell(
    state: UiState,
    sky: SkyState,
    viewModel: WeatherViewModel,
    widthPx: Float,
    modifier: Modifier = Modifier,
) {
    val rooms = SalaRoom.entries
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = rooms.indexOf(state.room).coerceAtLeast(0),
        pageCount = { rooms.size },
    )

    // Si scrive nello stato solo sulla pagina posata, mai su quella sfiorata
    // durante un trascinamento.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            rooms.getOrNull(page)?.let(viewModel::showRoom)
        }
    }

    // L'aggancio di verifica automatica: `--ei sezione`, riletto per le sale.
    //
    // Legge la sala **dalla richiesta** e non da `state.room`, che il carosello
    // qui sopra riscrive da se' a ogni pagina posata: due scrittori sullo
    // stesso campo sono una gara, e la richiesta la perderebbe ogni volta che
    // la prima emissione - pagina zero - arriva prima che l'effetto la legga.
    LaunchedEffect(state.roomRequest) {
        val wanted = state.roomRequest ?: return@LaunchedEffect
        val page = rooms.indexOf(wanted)
        if (page >= 0 && page != pagerState.currentPage) pagerState.scrollToPage(page)
        viewModel.roomRequestHonoured()
    }

    val posizione = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    // Andare a una sala si scrive una volta sola: lo chiedono la colonna sul
    // fianco, i sette trattini, il collegamento di Sala I e i sei riquadri di
    // Sala II, e quattro copie della stessa riga divergono alla prima che
    // qualcuno tara.
    fun vaiA(sala: SalaRoom) {
        scope.launch { pagerState.animateScrollToPage(rooms.indexOf(sala)) }
    }

    // **Il gemello senza molla, per il dito che trascina la colonna.**
    //
    // Un tocco e' un salto e va raccontato: parte da dove sei, arriva dove hai
    // chiesto, e la molla e' il racconto. Un trascinamento no - il racconto e'
    // il dito, che sta gia' dicendo dove sta andando - e animare ogni sala
    // attraversata vorrebbe dire che il carosello insegue il dito con mezzo
    // secondo di ritardo e arriva dove era, non dov'e'.
    fun portaA(sala: SalaRoom) {
        scope.launch { pagerState.scrollToPage(rooms.indexOf(sala)) }
    }

    // Il tasto indietro torna alla prima sala prima di chiudere l'app: da sei
    // stanze sotto, uscire non e' quasi mai la risposta cercata.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    // **L'ora del giorno scelto, non l'ora di oggi.** Toccando una colonna
    // della settimana tutto il resto la segue - cielo, scena, avvisi, pannello -
    // che e' la richiesta con cui Sala I e' stata rifatta. `detailHour` la
    // risolve gia': stessa ora, giorno giusto.
    val hour = state.detailHour ?: state.hour
    val codice = state.forcedWeatherCode ?: hour?.weatherCode
    val condition = salaConditionOf(codice)
    val fase = remember(sky) { faseContinua(sky) }

    // ── La scena, in numeri che scorrono ─────────────────────────────────────
    //
    // Sette scalari al posto di `condition`, `nevica` e `notte`: fra sereno e
    // coperto ci sono tutte le nuvolosita' del mondo, e con l'enum comparivano
    // tutte insieme, in un fotogramma, ogni volta che il cielo cambiava idea.
    val bersaglio = remember(sky, condition, hour, state.forcedCloudCover) {
        scenaBersaglio(
            sky = sky,
            condition = condition,
            nevicaWmo = Wmo.family(codice) == Wmo.Family.NEVE,
            // Imposta prima, vera poi: la stessa regola del codice una riga
            // sopra. Senza, con la copertura che viene dal dato vero gli scatti
            // a codice imposto ritraevano tutti lo stesso cielo.
            coperturaOraria = state.forcedCloudCover ?: hour?.cloudCover,
            pioggiaMm = hour?.precipitation,
        )
    }
    // **Due interruttori, una richiesta sola.** Quello delle impostazioni di
    // Caelum c'era gia'; quello del telefono - "rimuovi animazioni", che su una
    // pagina web si chiamerebbe `prefers-reduced-motion` - non lo leggeva
    // nessuno. Chi lo aveva spento nel sistema apriva quest'app e trovava un
    // cielo che si muoveva comunque, e doveva scoprire che c'era una seconda
    // levetta da abbassare. Si sommano, non si sostituiscono.
    val ridotte = state.animazioniRidotte || sistemaSenzaAnimazioni()
    val ferme = ridotte || state.animazioniIstantanee

    // Il colpetto sotto il dito: uno solo per tutta la cornice. Lo chiedono la
    // barra delle ore e la colonna delle scorciatoie, e due `remember` per lo
    // stesso vibratore sarebbero due tarature da tenere in fase.
    val vibrazioni = rememberVibrazioniMeteo()

    val m = mollaScena(state.animazioniIstantanee, ridotte)
    val scena = Scena(
        sole = animateFloatAsState(bersaglio.sole, m, label = "sole").value,
        copertura = animateFloatAsState(bersaglio.copertura, m, label = "copertura").value,
        tempesta = animateFloatAsState(bersaglio.tempesta, m, label = "tempesta").value,
        bagnato = animateFloatAsState(bersaglio.bagnato, m, label = "bagnato").value,
        ghiaccio = animateFloatAsState(bersaglio.ghiaccio, m, label = "ghiaccio").value,
        neve = animateFloatAsState(bersaglio.neve, m, label = "neve").value,
        notte = animateFloatAsState(bersaglio.notte, m, label = "notte").value,
    )

    // Quanto e' chiuso il cielo: un numero solo, da cui dipendono la sfumatura,
    // le colline e l'arco. Viene da valori gia' animati, quindi non ha bisogno
    // di una molla sua.
    val chiusura = livelloCielo(scena.copertura, scena.tempesta)
    // **`remember` anche se la chiave e' animata**, e vale la pena dire
    // perche': mentre il cielo si muove i tre numeri cambiano a ogni
    // fotogramma e questo non guadagna niente. Serve a cielo **fermo**, che e'
    // come sta quasi sempre: li' una `List<Color>` nuova a ogni ricomposizione
    // bastava a impedire a `SalaCielo` di essere saltata, perche' un parametro
    // diverso per riferimento e' un parametro cambiato.
    val stops = remember(fase, chiusura, scena.neve) { cieloStops(fase, chiusura, scena.neve) }

    // ── Il tema, e il solo salto che resta ───────────────────────────────────
    //
    // Lo scalino di `temaScuro` non si tocca: e' la garanzia che nessun cielo
    // porti l'interfaccia nella fascia in cui nessun inchiostro regge. La cura
    // non e' appianarlo, e' attraversarlo nel tempo.
    val dkBersaglio = when (state.cardTheme) {
        CardTheme.CHIARO -> 0f
        CardTheme.SCURO -> 1f
        CardTheme.AUTO ->
            if (temaScuroPerTempesta(condition)) 1f else temaScuro(sky)
    }
    val dk by animateFloatAsState(
        targetValue = dkBersaglio,
        animationSpec = mollaCarta(state.animazioniIstantanee, ridotte),
        label = "tema",
    )
    val crepuscolo by animateFloatAsState(
        targetValue = crepuscolezza(fase),
        animationSpec = mollaCarta(state.animazioniIstantanee, ridotte),
        label = "crepuscolo",
    )
    val palette = remember(dk, crepuscolo, chiusura) { salaPalette(dk, crepuscolo, chiusura) }

    // ── L'orologio della scena ───────────────────────────────────────────────
    //
    // **E' un'eccezione dichiarata alla regola dei zero fotogrammi a schermo
    // fermo, ed e' piu' larga di quella di prima.** Prima si muoveva la sola
    // Sala I; adesso il cielo sta dietro tutte e sette, quindi quando si muove
    // si muove sempre. E' il prezzo della direzione scelta: un cielo fermo non
    // e' un cielo, e' uno sfondo.
    //
    // Dichiararla senza lasciare una via d'uscita sarebbe dichiararla a meta':
    // `animazioniRidotte` la spegne, e con lei le vibrazioni.
    val tempo = rememberTempoScena(attivo = !ferme)

    // Il telefono sente cio' che cade, con lo stesso orologio che lo disegna.
    VibrazioniDellaScena(
        scena = scena,
        tempo = tempo,
        attiva = !ferme,
    )

    // **La fase segue il giorno scelto**, non l'oggi del telefono: scorrendo
    // alla notte di giovedi' la luna deve essere quella di giovedi'. Ed e' una
    // sola per tutta l'app - il cielo, Sala IV, la cella di Sala I e il
    // riquadro di Sala II - perche' quattro letture della stessa data
    // divergono al primo che ne aggiusta una.
    val giornoLuna = state.detailDay?.date ?: LocalDate.now()
    val faseLunare = remember(giornoLuna) { MoonPhase.at(giornoLuna) }

    val avvisi = remember(state.shownAlerts, hour?.time) { state.shownAlerts.attiveA(hour?.time) }
    val giorno = state.detailDay ?: state.forecast?.days?.firstOrNull()

    // **Il giorno mostrato, scritto una volta e letto da tutte e sette.** La
    // striscia di Sala I e la scheda di Sala II lo dicevano gia'; le altre
    // cinque no, e da "La pioggia" o da "Il vento" l'unico modo di sapere se
    // quei numeri erano di oggi o di giovedi' era risalire di due schermate.
    // Passa alla barra delle ore, che e' l'unico pezzo di cornice che sta
    // sotto ogni sala.
    val etichettaGiorno = remember(state.selectedDay, state.forecast) {
        val scelto = settimanaDi(state.forecast).getOrNull(state.selectedDay)
        // **Corta, perche' divide una riga con l'ora e con la pillola del
        // ritorno al presente.** "giovedì 18 set" accanto a "13:00" spingeva
        // l'ora contro la pillola sugli schermi stretti; "gio 18" no.
        when {
            scelto == null -> "oggi"
            scelto.indice == 0 -> "oggi"
            else -> "${scelto.breve} ${scelto.giornoDelMese}"
        }
    }

    CompositionLocalProvider(LocalDidascalie provides state.captionStyle) {
        Box(modifier = modifier.fillMaxSize()) {
            SalaCielo(
                palette = palette,
                stops = stops,
                scena = scena,
                sky = sky,
                faseLunare = faseLunare,
                tempo = tempo,
                modifier = Modifier.fillMaxSize(),
                // Il cielo si prende lo schermo intero, ma sole, luna e nuvole
                // scendono sotto la barra di stato: nel prototipo sono misurati
                // dentro la cornice dell'app, che li' comincia a zero.
                insetAlto = WindowInsets.systemBars.asPaddingValues().calculateTopPadding(),
                // Il cielo risponde al dito e all'inclinazione, tranne dove non
                // deve: chi ha chiesto meno movimento non si aspetta un sensore
                // acceso, e la cattura vuole scatti ripetibili.
                interattivo = !ferme,
            )

            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                IntestazioneCaelum(
                    citta = state.place.name,
                    avvisi = avvisi,
                    palette = palette,
                    onImpostazioni = viewModel::openSettings,
                    onCitta = viewModel::openLocations,
                    modifier = Modifier.padding(start = 22.dp, end = 26.dp, top = 12.dp),
                )

                // Il carosello lascia libero il fianco destro: sotto la colonna
                // delle scorciatoie non deve finirci niente da leggere.
                // **Le sale si cambiano di lato, e la lettura tiene il
                // verticale tutto per se'.**
                //
                // Prima erano un pager **verticale**, e dentro ogni pagina il
                // pannello scorreva anch'esso in verticale: due cose che
                // vogliono lo stesso dito. Chi leggeva doveva arrivare in
                // fondo al contenuto prima che il carosello si muovesse, e da
                // fuori si vedeva cosi': "bisogna scorrere molto per passare
                // da un menu' all'altro". Non era la soglia - era l'asse.
                //
                // Separati, ognuno fa il suo mestiere senza chiedere permesso
                // all'altro, e il pannello puo' crescere quanto gli pare
                // perche' non ruba piu' niente a nessuno.
                //
                // La colonna delle scorciatoie resta verticale a destra, e non
                // e' un'incoerenza: quella non si scorre, si **tocca**. E'
                // un indice, e un indice sta in piedi di lato.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(end = 40.dp),
                    // La soglia di Compose e' mezza pagina. Un quinto basta: il
                    // gesto resta deliberato, ma non e' piu' un trasloco - e la
                    // molla che segue e' piu' rigida di quella predefinita,
                    // perche' l'attesa dopo il dito pesa quanto il dito.
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.2f,
                        snapAnimationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                ) { page ->
                    // **La scheda scorre quando non ci sta, e prima si faceva
                    // tagliare.** Il pannello e' ancorato in basso e si
                    // dimensiona sul contenuto: finche' il contenuto ci sta,
                    // niente cambia di un punto. Quando non ci sta - "La
                    // settimana" su uno schermo corto, o col corpo di sistema
                    // ingrandito - il `Column` veniva misurato all'altezza
                    // disponibile e i figli in eccesso, margine inferiore
                    // compreso, finivano fuori dal ritaglio. Si faceva
                    // tagliare in fondo, ed e' successo davvero (sezione 15.4).
                    //
                    // Il `Box` da' l'ancora in basso, il `verticalScroll` da'
                    // la via d'uscita. Lo stato sta dentro `key(page)` perche'
                    // ogni sala si ricordi il proprio: condiviso, aprendo una
                    // sala corta dopo una lunga si troverebbe scorrevole senza
                    // niente da scorrere.
                    //
                    // **Quel prezzo non si paga piu'.** Finche' il carosello
                    // era verticale, su una scheda lunga il dito scorreva
                    // prima la scheda e cambiava sala solo arrivato in fondo -
                    // nested scroll di Compose, nessun codice nostro, e la
                    // ragione per cui cambiare sala sembrava un lavoro. Adesso
                    // che le sale vanno di lato i due gesti non si toccano: si
                    // legge in giu' e si cambia sala di fianco, sempre, a
                    // qualunque altezza della scheda.
                    val scorrimento = key(page) { rememberScrollState() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 26.dp, end = 26.dp, bottom = 14.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .verticalScroll(scorrimento),
                        ) {
                            when (rooms.getOrNull(page)) {
                                SalaRoom.OGGI -> SalaOggiScreen(
                                    state = state,
                                    sky = sky,
                                    palette = palette,
                                    viewModel = viewModel,
                                    faseLunare = faseLunare,
                                    onVai = ::vaiA,
                                )
                                SalaRoom.SETTIMANA -> SalaSettimanaScreen(
                                    state = state,
                                    palette = palette,
                                    viewModel = viewModel,
                                    faseLunare = faseLunare,
                                    onVai = ::vaiA,
                                )
                                SalaRoom.PIOGGIA -> SalaPioggiaScreen(
                                    state = state,
                                    palette = palette,
                                    onSelectHour = viewModel::selectHour,
                                    onSelectDay = viewModel::selectDay,
                                )
                                SalaRoom.LUNA -> SalaLunaScreen(palette = palette, giorno = giornoLuna, movimento = !ferme)
                                SalaRoom.ARIA -> SalaAriaScreen(state = state, palette = palette)
                                SalaRoom.VENTO -> SalaVentoScreen(state = state, palette = palette)
                                SalaRoom.UV -> SalaUvScreen(
                                    state = state,
                                    palette = palette,
                                    onSelectHour = viewModel::selectHour,
                                    movimento = !ferme,
                                )
                                null -> Unit
                            }
                        }
                    }
                }

                BarraDelleOre(
                    // **Le ore del giorno mostrato, senza ripieghi.** Qui c'era
                    // un `ifEmpty { state.hours }`: con un giorno senza ore la
                    // barra dipingeva **oggi** sotto l'intestazione di un altro
                    // giorno. Un binario spento dice la verita'; quello pieno
                    // di un giorno sbagliato no.
                    hours = state.shownHours,
                    selected = state.selectedHour,
                    oraAttuale = state.nowIndex,
                    giorno = etichettaGiorno,
                    palette = palette,
                    alba = giorno?.sunrise,
                    tramonto = giorno?.sunset,
                    onSelect = viewModel::selectHour,
                    onTick = { if (!ridotte) vibrazioni.scatto() },
                    onTornaOra = viewModel::backToNow,
                    modifier = Modifier.padding(start = 26.dp, end = 62.dp),
                )
            }

            // **I sette trattini qui sotto non ci sono piu'.** Facevano lo
            // stesso mestiere di questa colonna - dire dove sei, e portartici -
            // a due bordi diversi dello schermo, e lo facevano **in
            // orizzontale**, promettendo che le schede si sfogliassero di lato
            // mentre si sfogliano in su e in giu'.
            //
            // Costavano quarantotto punti di bersaglio piu' dodici di margine:
            // sessanta punti che da qui in poi sono delle schede, che erano
            // strette. La colonna ha preso la cosa che i trattini facevano
            // meglio - seguire il dito in continuo - tramite `posizione`.
            ColonnaScorciatoie(
                corrente = rooms.getOrNull(pagerState.currentPage) ?: SalaRoom.OGGI,
                posizione = posizione,
                palette = palette,
                movimento = !ferme,
                onVai = ::vaiA,
                onPorta = ::portaA,
                onTick = { if (!ridotte) vibrazioni.scatto() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Dieci punti prima, sei adesso, e i dischi non si sono
                    // spostati: il bersaglio e' cresciuto di cinque punti per
                    // lato attorno al disco, e questo margine li restituisce.
                    // Cio' che cambia e' l'area sensibile, che ora comincia a
                    // sei punti dal vetro invece che a dieci.
                    .padding(end = 6.dp),
            )

            // ── L'ordine di questi due blocchi e' funzionale ─────────────────
            //
            // **Le localita' vanno composte dopo le impostazioni, e non e' una
            // questione di gusto.** In un `Box` l'ultimo composto sta sopra, e
            // da li' viene meta' del motivo: "Le localita'" si apre **dalle**
            // impostazioni, quindi deve entrare davanti a loro. Con l'ordine
            // opposto la lista si apriva sotto un pannello opaco e chi la
            // chiedeva non vedeva succedere niente.
            //
            // L'altra meta' e' il tasto indietro, ed e' la ragione per cui non
            // basta uno `zIndex`: `BackHandler` da' la precedenza **all'ultimo
            // registrato**, cioe' all'ordine di composizione, non
            // all'impilamento. Con `zIndex` si vedrebbe la cosa giusta e
            // l'indietro chiuderebbe le impostazioni per prime, lasciando la
            // lista orfana a schermo.
            //
            // Chi riordina questi due blocchi per pulizia riapre il difetto.
            val scorrimentoImpostazioni by animateFloatAsState(
                targetValue = if (state.settingsOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "impostazioni",
            )
            if (scorrimentoImpostazioni > 0.001f) {
                BackHandler(enabled = state.settingsOpen, onBack = viewModel::closeSettings)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset((-(1f - scorrimentoImpostazioni) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SalaImpostazioniScreen(
                        state = state,
                        palette = palette,
                        onToggleAnimazioni = viewModel::setAnimazioniRidotte,
                        onChooseTheme = viewModel::setCardTheme,
                        onChooseUnit = viewModel::setUnit,
                        onChooseWindUnit = viewModel::setWindUnit,
                        onChooseCaptionStyle = viewModel::setCaptionStyle,
                        onToggleAlert = viewModel::setAlertToggle,
                        onApriLocalita = viewModel::openLocations,
                        onAggiorna = viewModel::refresh,
                        onApriLegali = viewModel::openLegali,
                        onClose = viewModel::closeSettings,
                    )
                }
            }

            val scorrimentoLocalita by animateFloatAsState(
                targetValue = if (state.locationsOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "localita",
            )
            if (scorrimentoLocalita > 0.001f) {
                BackHandler(enabled = state.locationsOpen, onBack = viewModel::closeLocations)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(((1f - scorrimentoLocalita) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SalaLocalitaScreen(
                        state = state,
                        palette = palette,
                        onPick = viewModel::choosePlace,
                        // Due funzioni distinte, e non due nomi per lo stesso
                        // interruttore: toccare nei risultati una citta' gia'
                        // salvata la **cancellava**, in silenzio, mentre chi
                        // guardava aveva appena chiesto di aggiungerla.
                        onAdd = viewModel::addFavorite,
                        onRemove = viewModel::removeFavorite,
                        onSearch = viewModel::search,
                        onUseLocation = viewModel::useDeviceLocation,
                        onClose = viewModel::closeLocations,
                    )
                }
            }

            // **E le note legali vanno per ultime, per la stessa ragione delle
            // localita'.** Si aprono dalle impostazioni, quindi devono entrare
            // davanti a loro; e siccome `BackHandler` da' la precedenza
            // all'ultimo registrato, l'indietro deve trovare prima questa e poi
            // il pannello da cui e' stata chiesta. Vale la nota tre blocchi piu'
            // su: chi riordina per pulizia riapre il difetto.
            //
            // Con le localita' non si sovrappongono mai - si aprono da due
            // righe diverse della stessa schermata - quindi fra loro l'ordine
            // non conta: conta che stiano tutte e due dopo le impostazioni.
            val scorrimentoLegali by animateFloatAsState(
                targetValue = if (state.legaliOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "legali",
            )
            if (scorrimentoLegali > 0.001f) {
                BackHandler(enabled = state.legaliOpen, onBack = viewModel::closeLegali)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(((1f - scorrimentoLegali) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SalaLegaliScreen(palette = palette, onClose = viewModel::closeLegali)
                }
            }
        }
    }
}

/**
 * Gli avvisi in corso **all'ora mostrata**, il piu' grave per primo.
 *
 * Si filtrano sull'ora scelta e non sull'adesso: la barra delle ore sposta
 * tutta la schermata avanti e indietro nella giornata, e una pastiglia che
 * dicesse "allerta temporali" mentre si guarda una mattina serena starebbe
 * parlando di un altro momento.
 */
private fun List<WeatherAlert>.attiveA(momento: LocalDateTime?): List<WeatherAlert> {
    if (momento == null) return this
    return filter { avviso ->
        val dopoInizio = avviso.onset?.let { !momento.isBefore(it) } ?: true
        val primaDellaFine = avviso.expires?.let { !momento.isAfter(it) } ?: true
        dopoInizio && primaDellaFine
    }.sortedByDescending { it.level.weight }
}
