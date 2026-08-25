package com.forli.meteo.ui.motion

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 *
 * **Il trascinamento scrive l'angolo subito, sul posto.** Prima passava da un
 * `Animatable` e ogni delta apriva una coroutine per il proprio `snapTo`. Il
 * dispatcher della composizione consegna al fotogramma, non subito: l'ultimo
 * `snapTo` prima del rilascio finiva quindi *dopo* l'avvio della molla, e un
 * `Animatable` che riceve un `snapTo` annulla l'animazione in corso. Da fuori
 * si vedeva la cifra partire e piantarsi a meta' giro senza tornare a posto -
 * ed era tanto piu' probabile quanto piu' veloce era stato il gesto, cioe'
 * proprio quando il lancio contava. Con un valore scritto direttamente non c'e'
 * piu' una coda da cui possa uscire qualcosa in ritardo, e la molla e' l'unica
 * cosa in grado di muovere l'angolo dopo il rilascio.
 */
@Stable
class SceneRotation internal constructor(private val scope: CoroutineScope) {

    /** Angolo attorno all'asse verticale, in gradi. Letto nel disegno. */
    private val yaw = mutableFloatStateOf(0f)

    /** La molla del rilascio, se ne sta girando una. */
    private var settling: Job? = null

    val yawDeg: Float get() = yaw.floatValue

    internal fun begin() {
        // Il dito riprende il comando: qualunque molla in corso ha finito il
        // suo compito. Fermarla qui, e non lasciare che sia il valore nuovo a
        // contendersi l'angolo con lei, e' cio' che rende il gesto sempre
        // vincente sull'animazione.
        settling?.cancel()
        settling = null
    }

    internal fun drag(deltaPx: Float) {
        // Il segno e' negativo, e non e' un dettaglio: la superficie che si
        // tocca deve andare dove va il dito. Con il segno positivo, tirando
        // verso destra la cifra girava verso sinistra, come una manopola vista
        // da dietro.
        yaw.floatValue -= deltaPx * DEGREES_PER_PIXEL
    }

    internal fun release(velocityPx: Float) {
        val launchSpeed = (-velocityPx * DEGREES_PER_PIXEL)
            .coerceIn(-MAX_LAUNCH_SPEED, MAX_LAUNCH_SPEED)

        // Dove finirebbe se la si lasciasse scorrere, e da li' il giro intero
        // piu' vicino. Un lancio piano riporta l'oggetto dov'era; uno deciso lo
        // fa girare su se stesso una volta o due e lo lascia nella stessa posa.
        // In entrambi i casi torna a posto, ma quanto gira lo decide la mano.
        val from = yaw.floatValue
        val projected = from + launchSpeed * COAST
        val target = (projected / FULL_TURN).roundToInt() * FULL_TURN

        settling?.cancel()
        settling = scope.launch {
            animate(
                initialValue = from,
                targetValue = target,
                initialVelocity = launchSpeed,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = 55f,
                    // Mezzo decimo di grado: senza una soglia dichiarata la
                    // molla resta formalmente viva a strascicare centesimi di
                    // grado, e ogni fotogramma sprecato li' e' un fotogramma in
                    // cui la scena si ridisegna per niente.
                    visibilityThreshold = 0.05f,
                ),
            ) { value, _ -> yaw.floatValue = value }

            // Un giro intero e' indistinguibile da nessun giro: riportare il
            // conto a zero non si vede e impedisce all'angolo di crescere senza
            // fine.
            yaw.floatValue = 0f
            settling = null
        }
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
fun rememberSceneRotation(): SceneRotation {
    val scope = rememberCoroutineScope()
    return remember(scope) { SceneRotation(scope) }
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
