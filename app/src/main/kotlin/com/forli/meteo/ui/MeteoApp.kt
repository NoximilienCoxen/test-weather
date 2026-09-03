package com.forli.meteo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleEventObserver
import com.forli.meteo.data.SkyState
import com.forli.meteo.ui.alerts.AlertsSheet
import com.forli.meteo.ui.home.HomeScreen
import com.forli.meteo.ui.temperature.DayDetailScreen
import com.forli.meteo.ui.temperature.TemperatureDetailScreen
import com.forli.meteo.ui.motion.findLifecycleOwner
import com.forli.meteo.ui.motion.rememberDeviceTilt
import com.forli.meteo.ui.settings.SettingsScreen
import com.forli.meteo.ui.welcome.WelcomeScreen
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoTheme
import com.forli.meteo.ui.theme.relativeLuminance
import com.forli.meteo.ui.theme.skyColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * L'app si apre sulla schermata essenziale. Il dettaglio non e' una schermata
 * diversa: e' un foglio che sale seguendo il dito, reversibile a meta' corsa.
 * Dietro, la principale arretra e si smorza, cosi' resti nello stesso mondo
 * invece di essere trasportato altrove.
 *
 * Le impostazioni entrano invece da sinistra, da dove sta il loro pulsante.
 */
@Composable
fun MeteoApp(viewModel: WeatherViewModel) {
    val state by viewModel.state.collectAsState()

    // Un solo numero anima tutto il cielo, e da quello si ricavano insieme il
    // fondo, il colore del sole e la comparsa della luna. Animando le tre cose
    // separatamente ci sarebbero istanti in cui non sono d'accordo fra loro:
    // sole gia' sparito e cielo ancora di giorno.
    val altitude by animateFloatAsState(
        targetValue = state.skyAltitude,
        animationSpec = spring(stiffness = 110f),
        label = "cielo",
    )
    // Anche il viaggio si anima, se no scorrendo le ore l'astro salterebbe da
    // una posizione all'altra invece di attraversare il cielo.
    val journey by animateFloatAsState(
        targetValue = state.skyJourney,
        animationSpec = spring(stiffness = 110f),
        label = "viaggio",
    )
    // Da che parte della giornata si guarda: decide se l'orizzonte e' il rosa
    // dell'alba o l'arancio del tramonto. Si muove con la stessa molla degli
    // altri due, se no attraversando il mezzogiorno il cielo cambierebbe
    // tavolozza di scatto invece che di passaggio.
    val evening by animateFloatAsState(
        targetValue = state.skyEvening,
        animationSpec = spring(stiffness = 110f),
        label = "sera",
    )
    // Quanto e' coperto. Sta fuori da SkyState perche' non e' astronomia, ma si
    // anima insieme al resto: passando da un'ora serena a una piovosa il cielo
    // deve ingrigirsi, non cambiare.
    val cloudiness by animateFloatAsState(
        targetValue = state.skyCloudiness,
        animationSpec = spring(stiffness = 110f),
        label = "nuvolosita",
    )
    val sky = remember(altitude, journey, evening) { SkyState.of(altitude, journey, evening) }
    val colors = remember(sky, cloudiness) { skyColors(sky, cloudiness) }
    // Tre fermate e non due: quella di mezzo e' il tono piatto che tutto il
    // resto dell'app chiama "il fondo", e deve comparire davvero sullo schermo -
    // se no i testi e la barra sarebbero tarati su un cielo che non si vede.
    val skyBrush = remember(colors) {
        Brush.verticalGradient(
            0f to colors.skyZenith,
            0.5f to colors.background,
            1f to colors.skyHorizon,
        )
    }

    MeteoTheme(colors = colors) {
        // Un solo ascoltatore del sensore per tutta l'app, e il valore resta
        // uno stato: letto dentro il disegno invece che in composizione, il
        // sensore fa ridipingere e non ricomporre.
        val tilt = rememberDeviceTilt()
        val scope = rememberCoroutineScope()

        val sheet = remember(scope) { SheetGesture(scope) }
        val density = LocalDensity.current
        val haptics = LocalHapticFeedback.current

        // I dati si ricaricano tornando in primo piano, se hanno passato la
        // loro eta'. La composizione resta viva anche in sottofondo, quindi il
        // suo ciclo di vita non basta: serve quello dell'attivita'.
        val context = LocalContext.current
        val model by rememberUpdatedState(viewModel)
        DisposableEffect(context) {
            val owner = context.findLifecycleOwner()
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) model.refreshIfStale()
            }
            owner?.lifecycle?.addObserver(observer)
            onDispose { owner?.lifecycle?.removeObserver(observer) }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(skyBrush),
        ) {
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

            // Booleani derivati e non letture dirette: il foglio si muove a
            // ogni fotogramma del dito, e leggerne il valore qui in
            // composizione ricomporrebbe tutto l'albero sessanta volte al
            // secondo per rispondere a una domanda la cui risposta cambia due
            // volte in tutto il gesto.
            val sheetVisible by remember { derivedStateOf { sheet.open > 0.001f } }
            val pullArmed by remember { derivedStateOf { sheet.armed } }
            // Vero quando il foglio copre abbastanza schermo da decidere lui
            // il colore sotto le barre di sistema. Derivato come gli altri: il
            // foglio si muove a ogni fotogramma del dito, e questa risposta
            // cambia due volte in tutto il gesto.
            val sheetCovers by remember { derivedStateOf { sheet.open > 0.5f } }

            // Le icone delle barre di sistema seguono cio' che hanno sotto. Con
            // le barre trasparenti e un fondo che va dall'azzurro di
            // mezzogiorno all'indaco della notte, lasciarle fisse vuol dire
            // che per meta' giornata
            // sono invisibili: e' il motivo per cui negli scatti la barra di
            // navigazione appariva bianca sotto un'app scura.
            val panelled = state.settingsOpen || state.dayDetail != null || sheetCovers
            SystemBarIcons(
                behindStatusBar = if (panelled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    colors.skyZenith
                },
                behindNavigationBar = if (panelled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    colors.skyHorizon
                },
            )

            fun release(velocity: Float) {
                if (sheet.release(velocity)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refresh()
                }
            }

            // Al primo avvio l'app chiede dove sei, invece di dare per scontato
            // un posto che nessuno ha scelto. Il foglio del dettaglio non entra
            // in scena finche' il benvenuto non ha finito: non c'e' ancora
            // niente da approfondire.
            if (!state.welcomed) {
                WelcomeScreen(
                    state = state,
                    tilt = tilt,
                    onFindMe = viewModel::useDeviceLocation,
                    onChooseByHand = {
                        viewModel.dismissWelcome()
                        viewModel.openSettings()
                    },
                    onDone = viewModel::dismissWelcome,
                    modifier = Modifier.systemBarsPadding(),
                )
            } else HomeScreen(
                state = state,
                sky = sky,
                tilt = tilt,
                onSelectHour = viewModel::selectHour,
                onBackToNow = viewModel::backToNow,
                onOpenSettings = viewModel::openSettings,
                onOpenTemperatureDetail = sheet::openFully,
                onOpenAlerts = viewModel::openAlerts,
                onRefresh = viewModel::refresh,
                pullArmed = pullArmed,
                modifier = Modifier
                    .systemBarsPadding()
                    .graphicsLayer {
                        val open = sheet.open
                        alpha = 1f - open * 0.85f
                        scaleX = 1f - open * 0.06f
                        scaleY = 1f - open * 0.06f
                        // Il tiro per ricaricare sposta la schermata di meno di
                        // quanto vada il dito: e' la resistenza che dice che si
                        // sta tirando qualcosa, non scorrendo una pagina.
                        translationY = sheet.pull * 0.42f
                    }
                    .draggable(
                        state = rememberDraggableState { delta -> sheet.drag(delta, heightPx) },
                        orientation = Orientation.Vertical,
                        onDragStarted = { sheet.begin() },
                        onDragStopped = { velocity -> release(velocity) },
                    ),
            )

            if (sheetVisible) {
                // Il contenuto del dettaglio scorre, e un `draggable` sul
                // contenitore non puo' convivere con uno scorrimento interno:
                // il figlio riceve il tocco per primo e il foglio non si
                // chiuderebbe mai piu'. Con il nesting il gesto e' uno solo e
                // sono i due a spartirselo - prima il contenuto, e il foglio
                // solo su quello che avanza.
                val sheetScroll = remember(sheet, heightPx) {
                    SheetNestedScroll(sheet, heightPx)
                }
                BackHandler(enabled = sheetVisible, onBack = sheet::closeFully)
                // Una `Surface` e non un `Box` col fondo dipinto: cosi' il
                // colore di contenuto scende ai figli invece di restare quello
                // di riposo di Material, che e' nero. E la tinta e' quella del
                // tema, non una costante ricopiata in quattro file - i testi
                // che ci stanno sopra sono calcolati su di essa
                // (vedi ui/theme/Contrast.kt), quindi pannello e scritte non
                // possono piu' divergere.
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, ((1f - sheet.open) * heightPx).roundToInt()) }
                        .nestedScroll(sheetScroll),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    TemperatureDetailScreen(
                        state = state,
                        viewModel = viewModel,
                        tilt = tilt,
                        onBack = sheet::closeFully,
                        // L'inserto delle barre di sistema sta sul **contenuto**
                        // e non sulla superficie: cosi' il pannello dipinge da
                        // bordo a bordo. Con l'inserto sulla superficie il
                        // foglio si fermava prima delle barre, e sopra e sotto
                        // restava una striscia di cielo - a mezzogiorno una
                        // banda grigio chiaro attorno a un foglio scuro.
                        modifier = Modifier.systemBarsPadding(),
                    )
                }
            }

            // Il dettaglio di un giorno entra da destra: si scende dentro
            // qualcosa, e il verso lo racconta. Le impostazioni entrano da
            // sinistra perche' li' sta il loro pulsante.
            val dayOpen = state.dayDetail != null
            val dayShift by animateFloatAsState(
                targetValue = if (dayOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "giorno",
            )
            if (dayShift > 0.001f) {
                BackHandler(enabled = dayOpen, onBack = viewModel::closeDayDetail)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(((1f - dayShift) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    DayDetailScreen(
                        state = state,
                        viewModel = viewModel,
                        onBack = viewModel::closeDayDetail,
                        modifier = Modifier.systemBarsPadding(),
                    )
                }
            }

            // Le allerte entrano da destra come il dettaglio di un giorno: si
            // scende dentro qualcosa di piu' specifico, e il verso lo racconta.
            // Stanno **dopo** il dettaglio nella pila perche' la fascia si puo'
            // toccare anche da li', e un foglio che si apre sotto quello da cui
            // e' stato aperto non si vedrebbe.
            val alertsShift by animateFloatAsState(
                targetValue = if (state.alertsOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "allerte",
            )
            if (alertsShift > 0.001f) {
                BackHandler(enabled = state.alertsOpen, onBack = viewModel::closeAlerts)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(((1f - alertsShift) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    AlertsSheet(
                        alerts = state.shownAlerts,
                        unavailable = state.alertsUnavailable,
                        onBack = viewModel::closeAlerts,
                        modifier = Modifier.systemBarsPadding(),
                    )
                }
            }

            val settings by animateFloatAsState(
                targetValue = if (state.settingsOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "impostazioni",
            )
            if (settings > 0.001f) {
                BackHandler(enabled = state.settingsOpen, onBack = viewModel::closeSettings)
                // **Opaco.** Era nero all'ottantacinque per cento, e la
                // schermata sotto traspariva: negli scatti si legge la cifra
                // della temperatura in mezzo al testo delle impostazioni. Un
                // velo non e' uno sfondo, e un testo che poggia su un velo non
                // ha un contrasto: ne ha uno diverso a ogni pixel.
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset((-(1f - settings) * widthPx).roundToInt(), 0) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SettingsScreen(
                        state = state,
                        onQuery = viewModel::search,
                        onChoosePlace = viewModel::choosePlace,
                        onChooseUnit = viewModel::setUnit,
                        onChooseModel = viewModel::setModel,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onUseLocation = viewModel::useDeviceLocation,
                        onClose = viewModel::closeSettings,
                        modifier = Modifier.systemBarsPadding(),
                    )
                }
            }
        }
    }
}

/**
 * Il gesto verticale della schermata: il foglio del dettaglio che sale, e il
 * tiro verso il basso che ricarica.
 *
 * Stessa forma di `SceneRotation`, e per la stessa ragione. Prima il foglio era
 * un `Animatable` e ogni delta apriva una coroutine per il proprio `snapTo`: il
 * dispatcher della composizione consegna **al fotogramma**, quindi l'ultimo
 * `snapTo` prima del rilascio poteva atterrare dopo l'avvio della molla e
 * annullarla, lasciando il foglio piantato a meta' corsa (trappola #22). Il
 * dito scrive il valore sul posto; solo il rilascio anima, e la molla sta in un
 * `Job` che il gesto successivo cancella.
 *
 * **Il tiro verso il basso e' quello che prima veniva buttato via.** Con il
 * foglio chiuso, lo scorrimento in giu' finiva dentro un `coerceIn` e non
 * succedeva niente: e' anche il motivo per cui non c'era alcun modo di
 * ricaricare i dati a mano.
 */
@Stable
private class SheetGesture(private val scope: CoroutineScope) {

    /** Da 0 (chiuso) a 1 (dettaglio aperto). */
    private val raised = mutableFloatStateOf(0f)

    /** Quanto si e' tirato oltre la chiusura, in pixel di dito. */
    private val pulled = mutableFloatStateOf(0f)

    private var settling: Job? = null

    val open: Float get() = raised.floatValue
    val pull: Float get() = pulled.floatValue

    /** Vero quando il tiro basta a valere una ricarica, e si puo' lasciare. */
    val armed: Boolean get() = pulled.floatValue >= PULL_TRIGGER

    fun begin() {
        settling?.cancel()
        settling = null
    }

    fun drag(deltaPx: Float, heightPx: Float) {
        val next = raised.floatValue - deltaPx / heightPx
        if (next >= 0f) {
            raised.floatValue = next.coerceAtMost(1f)
            pulled.floatValue = 0f
        } else {
            // Il foglio e' gia' chiuso: quello che avanza tira giu' la
            // schermata invece di andare perso. Con un tetto, perche' oltre un
            // certo punto non e' piu' un gesto, e' un trascinamento.
            raised.floatValue = 0f
            pulled.floatValue = (pulled.floatValue - next * heightPx).coerceAtMost(PULL_LIMIT)
        }
    }

    /**
     * Apre il foglio del dettaglio senza passare dal trascinamento: lo chiama
     * il tocco sulla cifra della temperatura, che non ha ne' un delta ne' una
     * velocita' da darle in pasto a `release`.
     */
    fun openFully() = glideTo(1f)

    /** Chiude il foglio: la freccia in alto a sinistra e il tasto indietro. */
    fun closeFully() = glideTo(0f)

    private fun glideTo(target: Float) {
        settling?.cancel()
        settling = scope.launch {
            animate(
                initialValue = raised.floatValue,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            ) { value, _ -> raised.floatValue = value }
            settling = null
        }
    }

    /**
     * Il trascinamento che arriva da **dentro** il foglio.
     *
     * Muove solo il foglio e mai il tiro per ricaricare: quello appartiene alla
     * schermata principale, e un avanzo di scorrimento nato dentro il dettaglio
     * non deve poter chiedere dati alla rete. Senza questa porta separata,
     * arrivando in fondo alla corsa il valore scivolava in `pulled` e la
     * schermata sotto si ritrovava armata per una ricarica che nessuno aveva
     * chiesto.
     */
    fun dragSheet(deltaPx: Float, heightPx: Float) {
        raised.floatValue = (raised.floatValue - deltaPx / heightPx).coerceIn(0f, 1f)
    }

    /** Torna vero se il gesto e' arrivato abbastanza in giu' da chiedere i dati. */
    fun release(velocityPx: Float): Boolean {
        val asked = armed
        val target = when {
            velocityPx < -SNAP_VELOCITY -> 1f
            velocityPx > SNAP_VELOCITY -> 0f
            else -> if (raised.floatValue > 0.4f) 1f else 0f
        }
        settling?.cancel()
        settling = scope.launch {
            // Le due corse si escludono: si tira solo a foglio chiuso, e il
            // foglio sale solo quando non si sta tirando.
            if (pulled.floatValue > 0f) {
                animate(
                    initialValue = pulled.floatValue,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.70f, stiffness = 520f),
                ) { value, _ -> pulled.floatValue = value }
            } else {
                animate(
                    initialValue = raised.floatValue,
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                ) { value, _ -> raised.floatValue = value }
            }
            settling = null
        }
        return asked
    }
}

/**
 * Il ponte fra lo scorrimento del dettaglio e il foglio che lo contiene.
 *
 * Le due meta' del gesto verticale non sono in concorrenza, sono in fila: il
 * contenuto scorre finche' ha strada, e solo l'avanzo muove il foglio. Da fermo
 * in cima, il dito che va giu' non ha piu' contenuto da scorrere e quello che
 * resta chiude; da foglio socchiuso, il dito che va su lo rialza **prima** di
 * toccare il contenuto, altrimenti si scorrerebbe dentro una schermata che non
 * e' ancora arrivata al suo posto.
 */
@Stable
private class SheetNestedScroll(
    private val sheet: SheetGesture,
    private val heightPx: Float,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (delta >= 0f || sheet.open >= 1f) return Offset.Zero
        sheet.begin()
        sheet.dragSheet(delta, heightPx)
        return Offset(0f, delta)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = available.y
        if (delta <= 0f) return Offset.Zero
        sheet.begin()
        sheet.dragSheet(delta, heightPx)
        return Offset(0f, delta)
    }

    override suspend fun onPreFling(available: Velocity): Velocity = settle(available)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        settle(available)

    private fun settle(available: Velocity): Velocity {
        if (sheet.open >= 1f) return Velocity.Zero
        sheet.release(available.y)
        return available
    }
}

/**
 * Chiare o scure, le icone delle barre di sistema.
 *
 * Si decide dalla luminanza di cio' che sta sotto, con la stessa formula che
 * decide i colori del testo: sopra un fondo chiaro icone scure, sopra uno scuro
 * icone chiare. La soglia e' la meta' della scala percettiva, non lo 0,5 del
 * canale: un giallo pieno ha luminanza 0,93 e vuole icone nere, un blu pieno
 * 0,07 e le vuole bianche, e i due sono altrettanto "saturi".
 */
@Composable
private fun SystemBarIcons(behindStatusBar: Color, behindNavigationBar: Color) {
    val view = LocalView.current
    // Le due barre si decidono separatamente da quando il fondo e' una
    // sfumatura: in alto c'e' lo zenit e in fondo l'orizzonte, e al crepuscolo
    // uno dei due e' scuro mentre l'altro e' chiaro. Un boolean solo per
    // entrambe sbaglia sempre una delle due, per un'ora buona al giorno.
    val lightStatus = behindStatusBar.relativeLuminance() > 0.35f
    val lightNavigation = behindNavigationBar.relativeLuminance() > 0.35f
    DisposableEffect(view, lightStatus, lightNavigation) {
        val window = (view.context.findActivity())?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightStatus
                isAppearanceLightNavigationBars = lightNavigation
            }
        }
        onDispose { }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Oltre questa velocita' il gesto decide da solo, senza guardare la posizione. */
private const val SNAP_VELOCITY = 800f

/** Quanto dito serve per chiedere una ricarica, e quanto se ne accetta in tutto. */
private const val PULL_TRIGGER = 190f
private const val PULL_LIMIT = 300f
