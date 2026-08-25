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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.forli.meteo.data.SkyState
import com.forli.meteo.ui.home.HomeScreen
import com.forli.meteo.ui.motion.findLifecycleOwner
import com.forli.meteo.ui.motion.rememberDeviceTilt
import com.forli.meteo.ui.settings.SettingsScreen
import com.forli.meteo.ui.welcome.WelcomeScreen
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoTheme
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
    val sky = remember(altitude) { SkyState.of(altitude) }
    val colors = remember(sky) { skyColors(sky) }

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
                .background(colors.background),
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, ((1f - sheet.open) * heightPx).roundToInt()) }
                        .background(colors.background)
                        .systemBarsPadding()
                        .draggable(
                            state = rememberDraggableState { delta -> sheet.drag(delta, heightPx) },
                            orientation = Orientation.Vertical,
                            onDragStarted = { sheet.begin() },
                            onDragStopped = { velocity -> release(velocity) },
                        ),
                ) {
                    DetailScreen(state = state, viewModel = viewModel, tilt = tilt)
                }
            }

            val settings by animateFloatAsState(
                targetValue = if (state.settingsOpen) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                label = "impostazioni",
            )
            if (settings > 0.001f) {
                BackHandler(enabled = state.settingsOpen, onBack = viewModel::closeSettings)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset((-(1f - settings) * widthPx).roundToInt(), 0) }
                        .background(colors.background)
                        .systemBarsPadding(),
                ) {
                    SettingsScreen(
                        state = state,
                        onQuery = viewModel::search,
                        onChoosePlace = viewModel::choosePlace,
                        onChooseUnit = viewModel::setUnit,
                        onUseLocation = viewModel::useDeviceLocation,
                        onClose = viewModel::closeSettings,
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

/** Oltre questa velocita' il gesto decide da solo, senza guardare la posizione. */
private const val SNAP_VELOCITY = 800f

/** Quanto dito serve per chiedere una ricarica, e quanto se ne accetta in tutto. */
private const val PULL_TRIGGER = 190f
private const val PULL_LIMIT = 300f
