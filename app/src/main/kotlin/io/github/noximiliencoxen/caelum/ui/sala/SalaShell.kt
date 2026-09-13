package io.github.noximiliencoxen.caelum.ui.sala

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.motion.VibrazioniDellaScena
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
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
    val bersaglio = remember(sky, condition, hour) {
        scenaBersaglio(
            sky = sky,
            condition = condition,
            nevica = Wmo.family(codice) == Wmo.Family.NEVE,
            coperturaOraria = hour?.cloudCover,
            pioggiaMm = hour?.precipitation,
        )
    }
    val m = mollaScena(state.animazioniIstantanee, state.animazioniRidotte)
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
    val stops = cieloStops(fase, chiusura, scena.neve)

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
        animationSpec = mollaCarta(state.animazioniIstantanee, state.animazioniRidotte),
        label = "tema",
    )
    val crepuscolo by animateFloatAsState(
        targetValue = crepuscolezza(fase),
        animationSpec = mollaCarta(state.animazioniIstantanee, state.animazioniRidotte),
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
    val tempo = rememberTempoScena(
        attivo = !state.animazioniRidotte && !state.animazioniIstantanee,
    )

    // Il telefono sente cio' che cade, con lo stesso orologio che lo disegna.
    VibrazioniDellaScena(
        scena = scena,
        tempo = tempo,
        attiva = !state.animazioniRidotte && !state.animazioniIstantanee,
    )

    // La fase e' quella vera di stanotte, la stessa che mostra Sala IV: le due
    // non possono raccontare due lune diverse nella stessa notte.
    val faseLunare = remember { MoonPhase.at(LocalDate.now()) }

    val avvisi = remember(state.shownAlerts, hour?.time) { state.shownAlerts.attiveA(hour?.time) }
    val giorno = state.detailDay ?: state.forecast?.days?.firstOrNull()

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
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(end = 40.dp),
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 26.dp, end = 26.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        when (rooms.getOrNull(page)) {
                            SalaRoom.OGGI -> SalaOggiScreen(
                                state = state,
                                sky = sky,
                                palette = palette,
                                viewModel = viewModel,
                                onApriSettimana = { vaiA(SalaRoom.SETTIMANA) },
                            )
                            SalaRoom.SETTIMANA -> SalaSettimanaScreen(
                                state = state,
                                palette = palette,
                                viewModel = viewModel,
                                onVai = ::vaiA,
                            )
                            SalaRoom.PIOGGIA -> SalaPioggiaScreen(
                                state = state,
                                palette = palette,
                                onSelectHour = viewModel::selectHour,
                            )
                            SalaRoom.LUNA -> SalaLunaScreen(palette = palette, fase = faseLunare)
                            SalaRoom.ARIA -> SalaAriaScreen(state = state, palette = palette)
                            SalaRoom.VENTO -> SalaVentoScreen(state = state, palette = palette)
                            SalaRoom.UV -> SalaUvScreen(
                                state = state,
                                palette = palette,
                                onSelectHour = viewModel::selectHour,
                            )
                            null -> Unit
                        }
                    }
                }

                val vibrazioni = rememberVibrazioniMeteo()
                BarraDelleOre(
                    // Le ore del giorno mostrato: con un giorno futuro scelto,
                    // la barra dipingerebbe altrimenti la giornata di oggi.
                    hours = state.shownHours.ifEmpty { state.hours },
                    selected = state.selectedHour,
                    oraAttuale = state.nowIndex,
                    palette = palette,
                    alba = giorno?.sunrise,
                    tramonto = giorno?.sunset,
                    onSelect = viewModel::selectHour,
                    onTick = { if (!state.animazioniRidotte) vibrazioni.scatto() },
                    onTornaOra = viewModel::backToNow,
                    modifier = Modifier.padding(start = 26.dp, end = 62.dp),
                )
                PuntiSala(
                    posizione = posizione,
                    palette = palette,
                    onVai = { i -> rooms.getOrNull(i)?.let(::vaiA) },
                    modifier = Modifier.padding(start = 26.dp, end = 62.dp, bottom = 12.dp),
                )
            }

            ColonnaScorciatoie(
                corrente = rooms.getOrNull(pagerState.currentPage) ?: SalaRoom.OGGI,
                palette = palette,
                onVai = ::vaiA,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
            )

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
                        onClose = viewModel::closeSettings,
                    )
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
