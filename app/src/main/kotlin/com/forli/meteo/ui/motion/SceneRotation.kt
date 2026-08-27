package com.forli.meteo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * L'orientamento della scena, comandato dal dito.
 *
 * Uno solo per tutta la schermata: la cifra e la scultura sopra di lei sono due
 * oggetti dello stesso mondo, guardati dallo stesso punto. Se ruotassero per
 * conto proprio si vedrebbero due animazioni, non un movimento.
 *
 * Il valore non viene letto in composizione ma dentro il disegno, quindi ruotare
 * ridipinge senza ricomporre nulla.
 */
@Stable
class SceneRotation internal constructor(
    private val scope: CoroutineScope,
    /**
     * Chiamato quando la molla si riappoggia dopo **almeno** un giro intero.
     *
     * Non a ogni rilascio: la molla punta sempre al giro intero piu' vicino, e
     * un lancio piano riporta l'oggetto dov'era senza aver girato niente. Se il
     * segnale partisse anche li', suonerebbe a ogni sfioramento della cifra -
     * che e' il gesto piu' frequente dell'app - e da segnale diventerebbe
     * fastidio.
     */
    private val onFullTurn: () -> Unit = {},
) {

    private val animated = Animatable(0f)

    /** Quanto il dito ha girato in tutto, senza limiti. */
    private var raw = 0f

    /** Angolo attorno all'asse verticale, in gradi. */
    val yawDeg: Float get() = animated.value

    internal fun begin() {
        raw = animated.value
    }

    internal fun drag(deltaPx: Float) {
        // Il segno e' negativo, e non e' un dettaglio: la superficie che si
        // tocca deve andare dove va il dito. Con il segno positivo, tirando
        // verso destra la cifra girava verso sinistra, come una manopola vista
        // da dietro.
        raw -= deltaPx * DEGREES_PER_PIXEL
        // Il valore grezzo assorbe il delta prima del lancio, quindi due
        // trascinamenti ravvicinati non possono arrivare in ordine sbagliato:
        // ognuno porta gia' con se' la posizione finale, non un incremento.
        val target = raw
        scope.launch { animated.snapTo(target) }
    }

    internal suspend fun release(velocityPx: Float) {
        val launchSpeed = (-velocityPx * DEGREES_PER_PIXEL)
            .coerceIn(-MAX_LAUNCH_SPEED, MAX_LAUNCH_SPEED)

        // Dove finirebbe se la si lasciasse scorrere, e da li' il giro intero
        // piu' vicino. Un lancio piano riporta l'oggetto dov'era; uno deciso lo
        // fa girare su se stesso una volta o due e lo lascia nella stessa posa.
        // In entrambi i casi torna a posto, ma quanto gira lo decide la mano.
        val projected = animated.value + launchSpeed * COAST
        val turns = (projected / FULL_TURN).roundToInt()
        val target = turns * FULL_TURN

        animated.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 55f),
            initialVelocity = launchSpeed,
        )
        // Il segnale parte **dopo** la molla, non al rilascio: e' il momento in
        // cui l'oggetto si riappoggia che si deve sentire, e da fermo. Al
        // rilascio la cifra sta ancora girando, e un colpo li' arriverebbe a
        // meta' corsa su un oggetto in volo.
        if (turns != 0) onFullTurn()
        // Un giro intero e' indistinguibile da nessun giro: riportare il conto a
        // zero non si vede e impedisce all'angolo di crescere senza fine.
        animated.snapTo(0f)
        raw = 0f
    }

    private companion object {
        /**
         * Poco piu' di un centimetro di dito per dieci gradi. Piu' lento e la
         * cifra sembra incollata al vetro, piu' veloce e il minimo tremolio la
         * fa girare.
         */
        const val DEGREES_PER_PIXEL = 0.22f

        const val FULL_TURN = 360f

        /** Per quanto tempo, in secondi, si immagina che il lancio scorra. */
        const val COAST = 0.28f

        /** Gradi al secondo. Oltre, un colpo di dito diventa una trottola. */
        const val MAX_LAUNCH_SPEED = 1900f
    }
}

@Composable
fun rememberSceneRotation(onFullTurn: () -> Unit = {}): SceneRotation {
    val scope = rememberCoroutineScope()
    // Il richiamo viene tenuto aggiornato invece che catturato: la lambda
    // arriva nuova a ogni composizione, e legarla dentro il `remember`
    // significherebbe chiamare per sempre quella della prima volta.
    val live by rememberUpdatedState(onFullTurn)
    return remember(scope) { SceneRotation(scope) { live() } }
}

/**
 * Il gesto che ruota la scena.
 *
 * Solo orizzontale, e non e' un dettaglio: il gesto piu' importante dell'app e'
 * il trascinamento verso l'alto che apre il dettaglio. Un riconoscitore che
 * accetta qualunque direzione se lo mangerebbe, e il gesto decorativo
 * bloccherebbe quello utile.
 */
@Composable
fun Modifier.rotatesScene(rotation: SceneRotation): Modifier = draggable(
    state = rememberDraggableState { delta -> rotation.drag(delta) },
    orientation = Orientation.Horizontal,
    onDragStarted = { rotation.begin() },
    onDragStopped = { velocity -> rotation.release(velocity) },
)
