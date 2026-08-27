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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.forli.meteo.data.SkyState
import com.forli.meteo.ui.home.HomeScreen
import com.forli.meteo.ui.motion.rememberDeviceTilt
import com.forli.meteo.ui.settings.SettingsScreen
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
    val sky = remember(altitude) { SkyState.of(altitude) }
    val colors = remember(sky) { skyColors(sky) }

    MeteoTheme(colors = colors) {
        // Un solo ascoltatore del sensore per tutta l'app, e il valore resta
        // uno stato: letto dentro il disegno invece che in composizione, il
        // sensore fa ridipingere e non ricomporre.
        val tilt = rememberDeviceTilt()
        val scope = rememberCoroutineScope()
        val sheet = remember { Animatable(0f) }
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
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

            // Lo stesso arrivo del trascinamento riuscito, ma partito da un
            // tocco invece che da un dito che scorre: il foglio sale intero,
            // non a meta' corsa.
            fun openDetail() {
                scope.launch { sheet.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            }

            HomeScreen(
                state = state,
                sky = sky,
                tilt = tilt,
                onSelectHour = viewModel::selectHour,
                onBackToNow = viewModel::backToNow,
                onOpenSettings = viewModel::openSettings,
                onOpenTemperatureDetail = ::openDetail,
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
                        onClose = viewModel::closeSettings,
                    )
                }
            }
        }
    }
}

/** Oltre questa velocita' il gesto decide da solo, senza guardare la posizione. */
private const val SNAP_VELOCITY = 800f
