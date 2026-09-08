package io.github.noximiliencoxen.caelum.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.common.rememberMeteoLayout
import io.github.noximiliencoxen.caelum.ui.home.HomeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Il feed: una sezione per schermata, e ci si passa scorrendo dal basso verso
 * l'alto.
 *
 * **Era una schermata sola con tutto il resto sovrapposto.** Le sei grandezze
 * vivevano dentro un foglio che saliva dal basso, in un carosello orizzontale:
 * due gesti di profondita' sotto la schermata che si apre per prima. Adesso
 * sono la navigazione, e la profondita' e' zero - la pioggia sta uno
 * scorrimento sotto la temperatura, non dentro qualcosa che va aperto.
 *
 * **Il gesto verticale era libero, e non per caso.** La regola di questa app
 * divide gli assi da sempre: orizzontale gira la scena, verticale apre. Il
 * feed prende l'asse che il foglio aveva; la rotazione della cifra, la barra
 * delle ore e la scelta del giorno restano orizzontali e non si contendono
 * niente. E' la stessa spartizione di prima, con un contenuto diverso dentro.
 *
 * Tre regole ereditate dal vecchio carosello, che erano costate un giro
 * ciascuna e valgono identiche qui:
 *
 * - **il carosello e' la sorgente di verita'**, e si scrive nello stato solo su
 *   `settledPage`: un trascinamento lasciato a meta' e tornato indietro non e'
 *   una scelta, e non deve lasciare traccia;
 * - si legge **`currentPage`** per cio' che si vede, perche' la pagina posata
 *   cambia troppo tardi e la colonna resterebbe indietro per tutto il gesto;
 * - cio' che si muove col dito passa per **lambda**, letta dentro il disegno:
 *   `currentPageOffsetFraction` cambia a ogni fotogramma, e letta in
 *   composizione ricomporrebbe l'albero sessanta volte al secondo.
 *
 * **La prima scheda e' la schermata di sempre**, intatta: scultura, cifra,
 * barra delle ventiquattro ore, striscia della settimana, orologio. Le altre
 * cinque sono ancora segnaposto - si decide una sezione alla volta cosa
 * ospitano, che e' il modo di lavorare di questo progetto.
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

    val pagerState = rememberPagerState(
        initialPage = sections.indexOf(state.section).coerceAtLeast(0),
        pageCount = { sections.size },
    )

    // Lo stato riceve la sezione **posata**, non quella sfiorata. Un
    // trascinamento annullato non e' una scelta: se scrivesse comunque, l'app
    // riaperta si troverebbe su una scheda che nessuno ha voluto.
    //
    // **Si scrive sempre, senza confrontare con `state.section`.** Il confronto
    // ci sarebbe stato bene, ma questo effetto e' chiavato sul solo
    // `pagerState` e quindi non riparte mai: lo `state` che vedrebbe e' quello
    // della composizione in cui e' nato, cioe' congelato. Con un valore vecchio
    // il confronto risponde a caso, e la volta in cui risponde "uguale" mentre
    // non lo e' la scheda posata non finisce nello stato affatto. Scrivere e
    // basta e' corretto e non costa niente: uno `StateFlow` che riceve un
    // valore uguale al proprio non emette.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val next = sections.getOrNull(page) ?: return@collect
            viewModel.showSection(next)
        }
    }

    // L'aggancio della cattura automatica: `--ei sezione`. Senza animazione,
    // perche' chi scatta vuole la scheda **subito**, non fra trecento
    // millisecondi che nessuno gli garantisce di aspettare.
    LaunchedEffect(state.sectionRequest) {
        if (state.sectionRequest == 0) return@LaunchedEffect
        val page = sections.indexOf(state.section)
        if (page >= 0 && page != pagerState.currentPage) pagerState.scrollToPage(page)
    }

    val position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

    // Il tasto indietro riporta alla prima scheda invece di chiudere l'app: da
    // sei schede sotto, uscire non e' quasi mai la risposta cercata. Sulla
    // prima non fa niente e l'app si chiude come sempre.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val pull = remember(scope) { PullToRefresh(scope) }
        // Booleano derivato e non lettura diretta: il tiro si muove a ogni
        // fotogramma del dito, e leggerlo qui ricomporrebbe tutto l'albero per
        // rispondere a una domanda la cui risposta cambia due volte in tutto il
        // gesto.
        val armed by remember { derivedStateOf { pull.armed } }

        // Il ponte fra il carosello e il tiro. **Sopra** il pager: prende cio'
        // che il pager non consuma, cioe' esattamente il dito che va in giu'
        // quando sopra non c'e' piu' nessuna scheda.
        val edge = remember(pull, pagerState, haptics) {
            PullNestedScroll(
                pull = pull,
                atTop = { pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction == 0f },
                onAsked = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refresh()
                },
            )
        }

        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(edge)
                .graphicsLayer {
                    // Il tiro sposta il feed di meno di quanto vada il dito:
                    // e' la resistenza che dice che si sta tirando qualcosa,
                    // non scorrendo una pagina. Letto qui dentro, sposta senza
                    // ricomporre.
                    translationY = pull.offset * PULL_DRAG
                },
        ) { page ->
            when (sections.getOrNull(page)) {
                // La prima scheda e' la schermata di sempre. L'inserto delle
                // barre di sistema sta qui e non sul carosello: il cielo deve
                // dipingere da bordo a bordo, e le schede scorrono sotto le
                // barre invece di fermarsi prima.
                FeedSection.TEMPERATURA -> HomeScreen(
                    state = state,
                    sky = sky,
                    tilt = tilt,
                    onSelectHour = viewModel::selectHour,
                    onBackToNow = viewModel::backToNow,
                    onOpenSettings = viewModel::openSettings,
                    // Una colonna della settimana sceglie il giorno, e basta.
                    // Il giorno e' un asse: sceglierlo qui cambia cio' che
                    // raccontano tutte le schede sotto.
                    onOpenDay = viewModel::selectDay,
                    onOpenAlerts = viewModel::openAlerts,
                    onDismissAlerts = viewModel::collapseAlerts,
                    // Un gesto solo per due effetti: il pallino rimette la
                    // fascia e apre il bollettino. Chi lo tocca vuole leggere
                    // l'avviso, e trovarselo di nuovo per esteso tornando
                    // indietro e' la risposta che non richiede di cercare come
                    // si fa.
                    onReopenAlerts = {
                        viewModel.expandAlerts()
                        viewModel.openAlerts()
                    },
                    onRefresh = viewModel::refresh,
                    onSetWeek = viewModel::setWeekMode,
                    pullArmed = armed,
                    alive = pagerState.currentPage == 0,
                    // **A piena larghezza, e non ristretta per far posto alla
                    // colonna.** Ristretta lo era, e in uno scatto si vedeva:
                    // il nome della localita', la scultura, la cifra e la barra
                    // delle ore finivano tutti quarantaquattro punti a sinistra
                    // del centro dello schermo. Questa schermata ha una regola
                    // esplicita sul punto - i 48dp vuoti a destra del nome
                    // esistono apposta perche' resti "al centro dello schermo e
                    // non al centro di quel che avanza" - e il margine la
                    // violava in blocco.
                    //
                    // La colonna galleggia invece nel margine che c'e' gia': sta
                    // a meta' altezza, dove questa scheda ha la scultura e la
                    // cifra, che sono centrate e non arrivano al bordo. Le
                    // schede del feed il margine se lo tengono, perche' li'
                    // titolo e numeri vanno davvero da bordo a bordo.
                    modifier = Modifier.systemBarsPadding(),
                )

                null -> Unit

                else -> SectionCard(
                    section = sections[page],
                    state = state,
                    tilt = tilt,
                    layout = layout,
                    // `currentPage` e non `settledPage`: la scheda che si vede
                    // e' quella corrente, e la posata cambia troppo tardi - la
                    // pioggia della vasca comincerebbe a cadere solo dopo che
                    // il dito si e' staccato. E' la stessa lettura, sulla
                    // stessa riga, del flag della prima scheda qui sopra.
                    alive = pagerState.currentPage == page,
                    modifier = Modifier.systemBarsPadding(),
                )
            }
        }

        // La colonna sta **sopra** il carosello e fuori da esso: e' l'unica cosa
        // in scena che non scorre, ed e' cio' che la rende un punto di
        // riferimento invece di un settimo contenuto.
        FeedRail(
            sections = sections,
            position = position,
            onPick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .systemBarsPadding(),
        )
    }
}

/**
 * Il tiro verso il basso che ricarica i dati.
 *
 * **E' quello che c'era, meno il foglio.** Prima viveva dentro `SheetGesture`
 * insieme all'apertura del dettaglio: il foglio non c'e' piu', il tiro si', ed
 * e' l'unico modo a mano di chiedere dati nuovi.
 *
 * Stessa forma di `SceneRotation`, e per la stessa ragione: **il dito scrive il
 * valore sul posto**, in un `MutableFloatState`, e solo il rilascio anima. Un
 * `Animatable` che riceve uno `snapTo` per ogni delta annulla la molla che sta
 * girando, perche' il dispatcher della composizione consegna al fotogramma e
 * non subito - da fuori si vedeva il movimento piantarsi a meta' corsa, tanto
 * piu' spesso quanto piu' il gesto era stato deciso.
 */
@Stable
private class PullToRefresh(private val scope: CoroutineScope) {

    /** Quanto si e' tirato, in pixel di dito. */
    private val pulled = mutableFloatStateOf(0f)

    private var settling: Job? = null

    val offset: Float get() = pulled.floatValue

    /** Vero quando il tiro basta a valere una ricarica, e si puo' lasciare. */
    val armed: Boolean get() = pulled.floatValue >= PULL_TRIGGER

    fun begin() {
        settling?.cancel()
        settling = null
    }

    /** Torna quanto del delta si e' preso davvero. */
    fun drag(deltaPx: Float): Float {
        val before = pulled.floatValue
        // Con un tetto: oltre un certo punto non e' piu' un gesto, e' un
        // trascinamento.
        pulled.floatValue = (before + deltaPx).coerceIn(0f, PULL_LIMIT)
        return pulled.floatValue - before
    }

    /** Torna vero se il gesto e' arrivato abbastanza in giu' da chiedere i dati. */
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
 * Il ponte fra il carosello e il tiro per ricaricare.
 *
 * Le due meta' del gesto verticale non sono in concorrenza, sono in fila: il
 * carosello scorre finche' ha schede, e **solo l'avanzo** tira. Sulla prima
 * scheda, il dito che va giu' non ha piu' niente sopra da mostrare, e quello che
 * resta chiede i dati.
 *
 * **L'avanzo dev'essere quello di un dito.** `NestedScrollSource` arriva a ogni
 * richiamo e va letto: uno slancio che si esaurisce e l'elastico di fine corsa
 * che si rilassa entrerebbero qui indistinguibili da una mano, e basterebbe una
 * scorsa decisa perche' l'app chiedesse dati alla rete da sola. E' costata una
 * volta gia', sul foglio; non si ripaga.
 *
 * [atTop] e non il solo `currentPage == 0`: durante un trascinamento la prima
 * pagina e' ancora "corrente" mentre si sta gia' scoprendo la seconda, e senza
 * il controllo sullo scostamento si tirerebbe mentre si scorre.
 */
@Stable
private class PullNestedScroll(
    private val pull: PullToRefresh,
    private val atTop: () -> Boolean,
    private val onAsked: () -> Unit,
) : NestedScrollConnection {

    /** Se **questo** gesto ha davvero tirato: se no, non tocca a lui assestare. */
    private var moved = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!source.isFinger) return Offset.Zero
        val delta = available.y

        // **Prima** del carosello, non dopo. La strada dell'avanzo - lasciar
        // scorrere e prendere cio' che resta - dipende da cosa l'effetto di
        // sovrascorrimento decide di trattenere per la sua stiratura, e
        // sarebbe un comportamento ereditato invece che deciso. Sulla prima
        // scheda, sopra, non c'e' niente da mostrare: il dito che va giu' e'
        // gia' tutto del tiro, e prenderselo qui non toglie niente a nessuno.
        if (delta > 0f) {
            if (!atTop()) return Offset.Zero
            pull.begin()
            moved = true
            return Offset(0f, pull.drag(delta))
        }

        // Verso l'alto: chi ha tirato e risale rida' indietro quello che ha
        // preso **prima** che il carosello si muova, se no si comincerebbe a
        // scorrere con la schermata ancora spostata in giu'.
        if (pull.offset <= 0f) return Offset.Zero
        return Offset(0f, pull.drag(-min(pull.offset, -delta)))
    }

    override suspend fun onPreFling(available: Velocity): Velocity = settle()

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = settle()

    private fun settle(): Velocity {
        if (!moved && pull.offset <= 0f) return Velocity.Zero
        moved = false
        if (pull.release()) onAsked()
        // La velocita' non si restituisce: il tiro l'ha consumata tutta, e
        // lasciarla passare farebbe partire il carosello nell'istante in cui il
        // dito si stacca da una ricarica.
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

/** Quanto dito serve per chiedere una ricarica, e quanto se ne accetta in tutto. */
private const val PULL_TRIGGER = 190f
private const val PULL_LIMIT = 300f

/** Quanto del tiro finisce davvero sullo schermo: il resto e' resistenza. */
private const val PULL_DRAG = 0.42f
