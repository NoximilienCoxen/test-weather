package com.forli.meteo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * L'orientamento della scena, comandato dal dito.
 *
 * Uno solo per tutta la schermata: la cifra e la scultura sopra di lei sono due
 * oggetti dello stesso mondo, guardati dallo stesso punto. Se ruotassero per
 * conto proprio si vedrebbero due animazioni, non un movimento.
 *
 * Il valore non viene letto in composizione ma dentro il disegno, quindi ruotare
 * ridipinge senza ricomporre nulla.
 *
 * Quando il dito non tocca, la scena respira: un'oscillazione lenta (±4°, ~4s)
 * che da' vita all'oggetto senza causare ricomposizioni. Il drag ha priorita'
 * assoluta e azzera il contributo dell'idle.
 */
@Stable
class SceneRotation internal constructor(private val scope: CoroutineScope) {

    private val animated = Animatable(0f)

    /** Quanto il dito ha girato in tutto, senza limiti. */
    private var raw = 0f

    /** Vera mentre il dito sta trascinando: l'idle si azzera. */
    @Volatile private var draggingNow = false

    /**
     * L'offset dell'idle breathing, letto dentro il draw.
     * Zero mentre il dito e' attivo, oscillazione sinusoidale a riposo.
     */
    @Volatile var breathingOffset: Float = 0f
        private set

    /** Angolo attorno all'asse verticale, in gradi. */
    val yawDeg: Float get() = animated.value

    internal fun begin() {
        raw = animated.value
        draggingNow = true
        breathingOffset = 0f
    }

    internal fun drag(deltaPx: Float) {
        raw -= deltaPx * DEGREES_PER_PIXEL
        val target = raw
        scope.launch { animated.snapTo(target) }
    }

    internal fun releaseAsync(velocityPx: Float) {
        scope.launch {
            draggingNow = false
            release(velocityPx)
        }
    }

    internal suspend fun release(velocityPx: Float) {
        val launchSpeed = (-velocityPx * DEGREES_PER_PIXEL)
            .coerceIn(-MAX_LAUNCH_SPEED, MAX_LAUNCH_SPEED)
        val projected = animated.value + launchSpeed * COAST
        val target = (projected / FULL_TURN).roundToInt() * FULL_TURN
        animated.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 55f),
            initialVelocity = launchSpeed,
        )
        animated.snapTo(0f)
        raw = 0f
        draggingNow = false
    }

    /**
     * Avvia il loop dell'idle breathing.
     *
     * Gira per sempre su un coroutine scope collegato alla composizione:
     * quando la schermata sparisce il loop si cancella da solo.
     * Non usa `rememberInfiniteTransition` perche' legge solo dentro il draw
     * (nessuna ricomposizione), e perche' il drag deve interromperlo
     * istantaneamente senza aspettare il prossimo frame di composizione.
     */
    internal fun startBreathing(loopScope: CoroutineScope) {
        loopScope.launch {
            var origin = 0L
            while (true) {
                withFrameNanos { now ->
                    if (origin == 0L) origin = now
                    if (!draggingNow) {
                        val t = (now - origin) / 1_000_000_000.0
                        breathingOffset = (BREATH_AMPLITUDE * sin(TWO_PI * t / BREATH_PERIOD)).toFloat()
                    }
                }
            }
        }
    }

    private companion object {
        const val DEGREES_PER_PIXEL = 0.22f
        const val FULL_TURN = 360f
        const val COAST = 0.28f
        const val MAX_LAUNCH_SPEED = 1900f

        /** Ampiezza dell'oscillazione idle in gradi. */
        const val BREATH_AMPLITUDE = 4.0
        /** Periodo dell'oscillazione in secondi. */
        const val BREATH_PERIOD = 4.2
        val TWO_PI = 2.0 * Math.PI
    }
}

@Composable
fun rememberSceneRotation(): SceneRotation {
    val scope = rememberCoroutineScope()
    val rotation = remember(scope) { SceneRotation(scope) }
    // Avvia il loop dell'idle breathing legato al ciclo di vita del composable.
    LaunchedEffect(rotation) { rotation.startBreathing(this) }
    return rotation
}

/**
 * Il gesto che ruota la scena.
 *
 * Solo orizzontale: il gesto piu' importante dell'app e' il trascinamento verso
 * l'alto che apre il dettaglio. Un riconoscitore che accetta qualunque direzione
 * se lo mangerebbe.
 */
@Composable
fun Modifier.rotatesScene(rotation: SceneRotation): Modifier = draggable(
    state = rememberDraggableState { delta -> rotation.drag(delta) },
    orientation = Orientation.Horizontal,
    onDragStarted = { rotation.begin() },
    onDragStopped = { velocity -> rotation.release(velocity) },
)
