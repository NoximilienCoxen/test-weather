package io.github.noximiliencoxen.caelum.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.common.EditorialHeader
import io.github.noximiliencoxen.caelum.ui.common.rememberMeteoLayout
import io.github.noximiliencoxen.caelum.ui.scene.WeatherDiorama
import io.github.noximiliencoxen.caelum.ui.scene.sceneOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.min

/**
 * Il feed: una scena dipinta in cima, e sotto tutto il resto in una colonna
 * sola.
 *
 * **Era un carosello di sei schermate piene.** Si scorreva dal basso verso
 * l'alto e si passava dalla temperatura alla pioggia, una pagina per grandezza.
 * Adesso e' **una colonna**: la scena occupa il primo schermo e si ritira in una
 * fascia mentre le informazioni le scorrono sotto. Le sei grandezze restano
 * sei, ma sono sezioni di un documento invece che pagine di un libro.
 *
 * **Cade la spartizione degli assi**, che era una regola dichiarata di questa
 * app dalla prima riga: orizzontale gira la scena, verticale apre. Adesso il
 * verticale **scorre**, e non c'e' piu' niente da aprire. Chi torna su questo
 * file sappia che non e' una svista: e' il prezzo pagato per avere una
 * schermata sola che si legge dall'alto in basso invece di sei che si
 * sfogliano, ed e' cio' che rende la scena in cima un'immagine di apertura e
 * non la copertina di una delle sei.
 *
 * **La contrazione non ha un suo stato**, e va capito prima di toccare questo
 * file. Si potrebbe tenere un numero "quanto e' chiusa la scena" e muoverlo con
 * un `NestedScrollConnection`, ma allora due cose descriverebbero la stessa
 * posizione - quel numero e lo scorrimento della lista - e prima o poi non
 * sarebbero d'accordo. Qui la contrazione si **ricava** da dove sta la lista, e
 * l'accordo e' garantito per costruzione. La lista ha in cima un margine alto
 * quanto la scena aperta, quindi il primo blocco parte esattamente dal bordo di
 * sotto della fascia e ci resta attaccato per tutta la corsa.
 *
 * **Il tiro per ricaricare e' passato sull'avanzo**, e rovescia una scelta
 * documentata. Prima si prendeva il dito **prima** del carosello, in
 * `onPreScroll`, perche' sulla prima pagina sopra non c'era niente da mostrare e
 * prenderselo non toglieva niente a nessuno. Adesso sopra c'e' una lista che
 * puo' essere scorsa: prendere il dito prima di lei vorrebbe dire che non si
 * puo' piu' risalire. Quindi il tiro riceve **cio' che la lista non ha usato**,
 * che e' esattamente il dito che scende quando si e' gia' in cima. Resta invece
 * intatta la guardia sul `NestedScrollSource` (trappola #35): un avanzo di
 * slancio non e' un dito, e un'app non chiede dati alla rete perche' una molla
 * ha finito di tornare a posto.
 */
@Composable
fun FeedScreen(
    state: UiState,
    sky: SkyState,
    tilt: State<Offset>,
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
) {
    val sections = FeedSection.entries
    val scope = rememberCoroutineScope()
    val layout = rememberMeteoLayout()
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // La sezione posata finisce nello stato, come prima: e' da li' che si
    // riparte alla prossima apertura. Adesso "posata" vuol dire "quella in cima
    // alla colonna", non "quella su cui il carosello si e' fermato".
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            sections.getOrNull(index)?.let(viewModel::showSection)
        }
    }

    // L'aggancio della cattura automatica, `--ei sezione`, che con il carosello
    // aveva smesso di funzionare: tutte e sei le schede fotografate uscivano
    // uguali alla prima. Qui e' uno scorrimento, che e' un'operazione che una
    // lista sa sempre fare, e non dipende piu' da quando il carosello nasce.
    LaunchedEffect(state.sectionRequest) {
        if (state.sectionRequest == 0) return@LaunchedEffect
        val index = sections.indexOf(state.section)
        if (index >= 0) listState.scrollToItem(index)
    }

    BackHandler(enabled = listState.firstVisibleItemIndex != 0) {
        scope.launch { listState.animateScrollToItem(0) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val openPx = with(density) { (maxHeight * SCENE_SHARE).toPx() }
        val bandPx = with(density) { SCENE_BAND.toPx() }
        val range = (openPx - bandPx).coerceAtLeast(1f)
        val openDp = with(density) { openPx.toDp() }

        // Quanto si e' ritirata: si legge dalla lista, non si tiene da parte.
        val collapse by remember(range) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) range
                else min(listState.firstVisibleItemScrollOffset.toFloat(), range)
            }
        }

        val pull = remember(scope) { PullToRefresh(scope) }
        val armed by remember { derivedStateOf { pull.armed } }
        val edge = remember(pull, listState, haptics) {
            PullNestedScroll(
                pull = pull,
                atTop = {
                    listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                },
                onAsked = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refresh()
                },
            )
        }

        Box(modifier = Modifier.fillMaxSize().nestedScroll(edge)) {
            // ── La colonna ─────────────────────────────────────────────────
            //
            // Il margine in cima e' **fisso** e alto quanto la scena aperta: se
            // seguisse la contrazione, ogni fotogramma dello scorrimento
            // rimisurerebbe la lista intera per spostare di un pixel una cosa
            // che si sposta gia' da sola.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = pull.offset * PULL_DRAG },
                contentPadding = PaddingValues(top = openDp, bottom = 28.dp),
            ) {
                sections.forEach { section ->
                    item(key = section.name) {
                        SectionBlock(
                            section = section,
                            state = state,
                            tilt = tilt,
                            layout = layout,
                            onSelectHour = viewModel::selectHour,
                            onSelectDay = viewModel::selectDay,
                            onBackToNow = viewModel::backToNow,
                            onSetWeek = viewModel::setWeekMode,
                            onOpenAlerts = viewModel::openAlerts,
                            onDismissAlerts = viewModel::collapseAlerts,
                            onReopenAlerts = {
                                viewModel.expandAlerts()
                                viewModel.openAlerts()
                            },
                        )
                    }
                }
            }

            // ── La scena, sopra la colonna ─────────────────────────────────
            //
            // Sopra e non dietro: contraendosi deve **coprire** cio' che le
            // passa sotto, se no le righe della prima sezione si vedrebbero
            // salire attraverso il dipinto.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        // **Si ritaglia, non si schiaccia.** Scalando in
                        // verticale il dipinto si deformerebbe, e una collina
                        // schiacciata si vede subito. Qui il contenuto si misura
                        // sempre all'altezza aperta e il riquadro ne dichiara
                        // meno: quello che sparisce e' il terreno in fondo, e
                        // resta il cielo. Che e' anche cio' che si vuole vedere
                        // in una fascia alta centotrenta punti.
                        val full = openPx.toInt().coerceAtLeast(1)
                        val shown = (openPx - collapse).coerceIn(bandPx, openPx).toInt()
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = full, maxHeight = full)
                        )
                        layout(placeable.width, shown) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { translationY = pull.offset * PULL_DRAG },
            ) {
                WeatherDiorama(
                    kind = sceneOf(state.forcedWeatherCode ?: state.pageHour?.weatherCode),
                    sky = sky,
                    tilt = tilt,
                    windSpeed = state.pageHour?.windSpeed,
                    precipitationMm = state.pageHour?.precipitation,
                    // Il freno della scena viva: ferma appena si apre un
                    // pannello sopra, dove nessuno la sta piu' guardando.
                    alive = !state.settingsOpen && !state.alertsOpen,
                    modifier = Modifier.fillMaxSize(),
                )

                // **Sopra un dipinto il contrasto non si calcola, si
                // costruisce.** La regola dell'app - il colore del testo si
                // ricava dal fondo - vale finche' il fondo e' un colore. Qui
                // sotto c'e' un'immagine qualunque, e nessuna formula puo'
                // garantire una riga di testo sopra una nuvola bianca. La
                // garanzia la da' questa velatura: nera al 45% in cima, il
                // bianco ci sta sopra a piu' di sette a uno **comunque sia il
                // dipinto**.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SCRIM)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                            )
                        ),
                )
            }

            // ── La testata, sopra la scena ─────────────────────────────────
            val stale = rememberFreshness(state.fetchedAt)
            EditorialHeader(
                kicker = feedKicker(state, armed, stale),
                leading = { SettingsButton(Color.White) { viewModel.openSettings() } },
                title = Wmo.condition(state.forcedWeatherCode ?: state.pageHour?.weatherCode),
                // Bianco e non calcolato: sotto c'e' un'immagine, non un
                // colore, e a garantirlo e' la velatura qui sopra.
                accent = Color.White,
                muted = Color.White.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 2.dp),
            )

            // La colonna di icone resta il punto di riferimento che era: non
            // scorre, e adesso porta la lista alla sezione invece di animare un
            // carosello.
            FeedRail(
                sections = sections,
                position = {
                    val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    if (first == null || first.size <= 0) 0f
                    else first.index + (-first.offset).toFloat() / first.size
                },
                onPick = { index -> scope.launch { listState.animateScrollToItem(index) } },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .systemBarsPadding(),
            )
        }
    }
}

/**
 * Di chi, di quando, e cosa sta facendo l'app.
 *
 * Una riga sola sopra il titolo, come nei riferimenti: `NOCETO · OGGI · 15:00`.
 * Quando c'e' qualcosa di piu' urgente da dire - si sta tirando per ricaricare,
 * si sta ricaricando, il dato e' vecchio - **prende il posto del momento** e non
 * si aggiunge: una riga che cresce sposta il titolo, e il titolo sta sopra un
 * dipinto dove ogni spostamento si nota.
 */
private fun feedKicker(state: UiState, armed: Boolean, stale: String?): String {
    val place = state.place.name.uppercase()
    val day = when (state.selectedDay) {
        0 -> "OGGI"
        1 -> "DOMANI"
        else -> state.forecast?.days?.getOrNull(state.selectedDay)?.label ?: "--"
    }
    val moment = when {
        armed -> "RILASCIA"
        state.refreshing -> "AGGIORNO"
        // Un dato vecchio ha la precedenza sull'ora: sapere **quando** e' stato
        // preso conta piu' che sapere di quale ora parla, e sono la stessa riga.
        stale != null -> stale
        else -> state.detailHour?.time?.let {
            runCatching { it.format(KICKER_CLOCK) }.getOrNull()
        }
    }
    return listOfNotNull(place, day, moment).joinToString("  ·  ")
}

private val KICKER_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Quanto della schermata prende la scena da aperta.
 *
 * Cinquantotto centesimi, che e' anche dove i dipinti mettono l'orizzonte: da
 * aperta si vede tutto il cielo e la linea di terra, e sotto comincia la prima
 * sezione. Piu' alta la scena diventa un fondale e la previsione sparisce sotto
 * la piega; piu' bassa non e' piu' un'immagine di apertura, e' un'illustrazione.
 */
private const val SCENE_SHARE = 0.58f

/** La fascia che resta in cima a colonna scorsa. */
private val SCENE_BAND = 132.dp

/** L'altezza della velatura che garantisce la testata sul dipinto. */
private val SCRIM = 160.dp

/**
 * Il tiro verso il basso che ricarica i dati.
 *
 * **E' quello di sempre.** Cambia solo da che parte gli arriva il dito - dopo la
 * lista invece che prima del carosello - e il perche' sta nel KDoc di
 * [FeedScreen]. Stessa forma di `SceneRotation`, e per la stessa ragione: il
 * dito scrive il valore sul posto, in un `MutableFloatState`, e solo il rilascio
 * anima. Un `Animatable` che riceve uno `snapTo` per ogni delta annulla la molla
 * che sta girando, perche' il dispatcher della composizione consegna al
 * fotogramma e non subito - da fuori si vedeva il movimento piantarsi a meta'
 * corsa, tanto piu' spesso quanto piu' il gesto era stato deciso.
 */
@Stable
private class PullToRefresh(private val scope: CoroutineScope) {

    private val pulled = mutableFloatStateOf(0f)
    private var settling: Job? = null

    val offset: Float get() = pulled.floatValue
    val armed: Boolean get() = pulled.floatValue >= PULL_TRIGGER

    fun begin() {
        settling?.cancel()
        settling = null
    }

    fun drag(deltaPx: Float): Float {
        val before = pulled.floatValue
        pulled.floatValue = (before + deltaPx).coerceIn(0f, PULL_LIMIT)
        return pulled.floatValue - before
    }

    fun release(): Boolean {
        val asked = armed
        settling?.cancel()
        settling = scope.launch {
            animate(
                initialValue = pulled.floatValue,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.70f, stiffness = 520f),
            ) { value, _ -> pulled.floatValue = value }
            settling = null
        }
        return asked
    }
}

/**
 * Il ponte fra la colonna e il tiro per ricaricare.
 *
 * Le due meta' del gesto verticale non sono in concorrenza, sono in fila: la
 * lista scorre finche' ha contenuto, e **solo l'avanzo** tira. Da qui
 * `onPostScroll`: si riceve cio' che la lista non ha consumato, cioe'
 * esattamente il dito che va giu' quando si e' gia' in cima.
 *
 * Verso l'alto invece si interviene **prima**, e solo per restituire quello che
 * si era preso: chi ha tirato e risale deve rimettere a posto la schermata
 * prima che la lista cominci a scorrere, se no si scorrerebbe con tutto ancora
 * spostato in giu'.
 *
 * [atTop] e non un booleano tenuto da parte: la lista sa dove sta, e chiederglielo
 * nell'istante in cui serve e' l'unica risposta che non puo' essere vecchia.
 */
@Stable
private class PullNestedScroll(
    private val pull: PullToRefresh,
    private val atTop: () -> Boolean,
    private val onAsked: () -> Unit,
) : NestedScrollConnection {

    private var moved = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!source.isFinger) return Offset.Zero
        val delta = available.y
        if (delta >= 0f || pull.offset <= 0f) return Offset.Zero
        // Si restituisce, non si prende: verso l'alto la lista ha la
        // precedenza appena la schermata e' tornata al suo posto.
        return Offset(0f, pull.drag(-min(pull.offset, -delta)))
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!source.isFinger) return Offset.Zero
        val delta = available.y
        if (delta <= 0f || !atTop()) return Offset.Zero
        pull.begin()
        moved = true
        return Offset(0f, pull.drag(delta))
    }

    override suspend fun onPreFling(available: Velocity): Velocity = settle()

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = settle()

    private fun settle(): Velocity {
        if (!moved && pull.offset <= 0f) return Velocity.Zero
        moved = false
        if (pull.release()) onAsked()
        return Velocity.Zero
    }
}

/**
 * Un avanzo di scorrimento **non e' un dito**.
 *
 * Uno slancio che si esaurisce, e l'elastico di fine corsa che si rilassa,
 * arrivano qui indistinguibili da una mano. Un'app non chiede dati alla rete
 * perche' una molla ha finito di tornare a posto.
 */
private val NestedScrollSource.isFinger: Boolean
    get() = this == NestedScrollSource.UserInput

private const val PULL_TRIGGER = 190f
private const val PULL_LIMIT = 300f
private const val PULL_DRAG = 0.42f
