package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Il giro attorno all'asse verticale, uguale in ogni sala che ne ha uno.
 *
 * Sta in un file suo perche' lo usano la scultura di Sala I e la luna di Sala
 * IV, e due copie della stessa molla divergono al primo che ne tara una.
 *
 * **Il verso e' opposto al dito, e non e' un capriccio.** Si trascina un
 * oggetto, non una telecamera: portando il dito a sinistra l'oggetto deve
 * girare come se lo si spingesse da davanti, cioe' mostrare il fianco destro.
 * Il verso di prima era quello della telecamera e si sentiva al contrario.
 *
 * **Si puo' lanciare.** `detectHorizontalDragGestures` da' lo spostamento ma
 * non la velocita', quindi al rilascio l'oggetto si fermava di colpo per poi
 * tornare indietro: nessun peso, nessuna inerzia. `draggable` la velocita' la
 * porta, e con lei il lancio diventa un decadimento vero seguito dal ritorno.
 *
 * @param giro l'angolo, in gradi. Chi lo disegna lo legge **dentro il disegno**:
 *   e' un gesto continuo, e letto in composizione ricomporrebbe l'albero a ogni
 *   fotogramma del dito invece di ridipingere e basta.
 * @param sensibilita quanti gradi vale un pixel di dito.
 */
@Composable
fun Modifier.giroConLancio(
    giro: Animatable<Float, AnimationVector1D>,
    sensibilita: Float = 0.30f,
): Modifier {
    val scope = rememberCoroutineScope()
    val stato = rememberDraggableState { delta ->
        scope.launch { giro.snapTo(giro.value - delta * sensibilita) }
    }
    return this.draggable(
        state = stato,
        orientation = Orientation.Horizontal,
        onDragStopped = { velocita ->
            // Prima il lancio si esaurisce da solo, poi la posa iniziale se la
            // riprende una molla lenta. Sono due animazioni in fila e non una
            // sola perche' fanno due cose diverse: l'inerzia di cio' che e'
            // stato spinto, e il richiamo di cio' che ha una posa giusta.
            giro.animateDecay(
                initialVelocity = -velocita * sensibilita,
                animationSpec = exponentialDecay(frictionMultiplier = 1.1f),
            )
            giro.animateTo(
                targetValue = 0f,
                // Molle basse: il ritorno e' un rilassarsi, non uno scatto.
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 45f),
            )
        },
    )
}

/** L'angolo di giro di una sala, azzerato a ogni ingresso nella stanza. */
@Composable
fun rememberGiro(): Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }
