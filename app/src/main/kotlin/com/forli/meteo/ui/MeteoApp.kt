package com.forli.meteo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.forli.meteo.prefs.ThemeMode
import com.forli.meteo.ui.home.HomeScreen
import com.forli.meteo.ui.motion.rememberDeviceTilt
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * L'app si apre sulla schermata essenziale. Il dettaglio non e' una schermata
 * diversa: e' un foglio che sale seguendo il dito, reversibile a meta' corsa.
 * Dietro, la principale arretra e si smorza, cosi' resti nello stesso mondo
 * invece di essere trasportato altrove.
 */
@Composable
fun MeteoApp(viewModel: WeatherViewModel) {
    val state by viewModel.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.CHIARO -> false
        ThemeMode.SCURO -> true
    }

    MeteoTheme(dark = dark) {
        val colors = LocalMeteoColors.current
        // Un solo ascoltatore del sensore per tutta l'app.
        val tilt by rememberDeviceTilt()
        val scope = rememberCoroutineScope()
        val sheet = remember { Animatable(0f) }
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

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
                tilt = tilt,
                onSelectHour = viewModel::selectHour,
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
        }
    }
}

/** Oltre questa velocita' il gesto decide da solo, senza guardare la posizione. */
private const val SNAP_VELOCITY = 800f
