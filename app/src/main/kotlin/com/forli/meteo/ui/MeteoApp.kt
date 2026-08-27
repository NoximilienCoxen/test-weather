package com.forli.meteo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.SkyState
import com.forli.meteo.ui.home.HomeScreen
import com.forli.meteo.ui.settings.SettingsScreen
import com.forli.meteo.ui.welcome.WelcomeScreen
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoTheme
import com.forli.meteo.ui.theme.skyColors
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
    // La luce dorata e' un secondo numero perche' non si ricava dal primo:
    // l'altezza del sole dice quanto e' giorno, non quanti minuti mancano al
    // tramonto, e d'inverno alle latitudini alte il sole sta basso per ore
    // senza che il cielo sia mai arancione.
    val golden by animateFloatAsState(
        targetValue = state.goldenHour,
        animationSpec = spring(stiffness = 110f),
        label = "oradorata",
    )
    // La nebbia scolora il cielo, quindi entra nella tavolozza. Anche questa
    // animata: comparire di colpo passando da un'ora all'altra si legge come un
    // difetto, non come meteo.
    val fog by animateFloatAsState(
        targetValue = state.fogDensity,
        animationSpec = spring(stiffness = 90f),
        label = "nebbia",
    )
    val sky = remember(altitude, golden) { SkyState.of(altitude, golden) }
    val colors = remember(sky, fog) { skyColors(sky, fog) }

    MeteoTheme(colors = colors) {
        val scope = rememberCoroutineScope()
        val sheet = remember { Animatable(0f) }
        val density = LocalDensity.current

        // Il cielo e' un gradiente, non una tinta piatta.
        //
        // Le tre fermate non sono equidistanti: il colore vivo deve occupare la
        // parte alta dove sta la scultura, e cedere in fretta verso il fondo
        // dove sta il testo. Con due sole fermate il centro dello schermo
        // finiva a meta' strada fra le due, cioe' proprio nel tono medio da cui
        // si veniva.
        val backdrop = remember(colors.skyTop, colors.skyBottom) {
            Brush.verticalGradient(
                0f to colors.skyTop,
                0.42f to lerp(colors.skyTop, colors.skyBottom, 0.45f),
                1f to colors.skyBottom,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backdrop),
        ) {
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

            fun drag(delta: Float) {
                scope.launch {
                    sheet.snapTo((sheet.value - delta / heightPx).coerceIn(0f, 1f))
                }
            }

            suspend fun settle(velocity: Float) {
                val target = when {
                    velocity < -SNAP_VELOCITY -> 1f
                    velocity > SNAP_VELOCITY -> 0f
                    else -> if (sheet.value > 0.4f) 1f else 0f
                }
                sheet.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 380f))
            }

            HomeScreen(
                state = state,
                sky = sky,
                fog = fog,
                // Sotto il foglio del dettaglio o dietro le impostazioni la
                // scena non si vede: farla respirare li' vorrebbe dire chiedere
                // fotogrammi per un oggetto coperto.
                visible = !state.settingsOpen && sheet.value < 0.98f,
                onSelectHour = viewModel::selectHour,
                onBackToNow = viewModel::backToNow,
                onOpenSettings = viewModel::openSettings,
                modifier = Modifier
                    .systemBarsPadding()
                    .graphicsLayer {
                        val open = sheet.value
                        alpha = 1f - open * 0.85f
                        scaleX = 1f - open * 0.06f
                        scaleY = 1f - open * 0.06f
                    }
                    .draggable(
                        state = rememberDraggableState { delta -> drag(delta) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
            )

            // Sopra la schermata principale ma sotto il foglio e le
            // impostazioni: e' un avviso, non una pagina, e non deve seguirti
            // dove sei andato.
            if (state.updateReady && sheet.value < 0.05f && !state.settingsOpen &&
                state.welcomed
            ) {
                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    // In alto, appena sotto la riga della localita': li' sopra
                    // c'e' cielo vuoto. In fondo avrebbe coperto la barra delle
                    // ore e l'ora mostrata, cioe' proprio le due cose per cui
                    // si e' aperta l'app.
                    UpdateNotice(
                        onDismiss = viewModel::dismissUpdate,
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopCenter)
                            .padding(top = 42.dp),
                    )
                }
            }

            if (sheet.value > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, ((1f - sheet.value) * heightPx).roundToInt()) }
                        .background(colors.background)
                        .systemBarsPadding()
                        .draggable(
                            state = rememberDraggableState { delta -> drag(delta) },
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> settle(velocity) },
                        ),
                ) {
                    DetailScreen(state = state, viewModel = viewModel)
                }
            }

            // Il benvenuto sta sopra tutto e non e' scorrevole: e' una domanda,
            // e finche' non ha una risposta non c'e' niente sotto che abbia
            // senso guardare. Sta pero' *dentro* lo stesso tema, cosi' il cielo
            // dell'ora corrente si vede gia' da qui.
            if (!state.welcomed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backdrop)
                        .systemBarsPadding(),
                ) {
                    WelcomeScreen(
                        locating = state.locating,
                        problem = state.locationProblem,
                        onLocate = viewModel::locateMe,
                        onChoosePlace = viewModel::choosePlace,
                        onSkip = viewModel::dismissWelcome,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset((-(1f - settings) * widthPx).roundToInt(), 0) }
                        .background(colors.background)
                        .systemBarsPadding(),
                ) {
                    // I campi uno per uno e non l'intero stato: cosi' la
                    // schermata puo' saltare la ricomposizione quando cambia
                    // qualcosa che non la riguarda, che e' quasi sempre.
                    SettingsScreen(
                        place = state.place,
                        unit = state.unit,
                        favourites = state.favourites,
                        query = state.query,
                        searching = state.searching,
                        results = state.results,
                        searchError = state.searchError,
                        locating = state.locating,
                        locationProblem = state.locationProblem,
                        fetchedAt = state.forecast?.fetchedAt,
                        onQuery = viewModel::search,
                        onChoosePlace = viewModel::choosePlace,
                        onChooseUnit = viewModel::setUnit,
                        onLocate = viewModel::locateMe,
                        onAddFavourite = viewModel::addFavourite,
                        onRemoveFavourite = viewModel::removeFavourite,
                        onClose = viewModel::closeSettings,
                    )
                }
            }
        }
    }
}

/** Oltre questa velocita' il gesto decide da solo, senza guardare la posizione. */
private const val SNAP_VELOCITY = 800f
