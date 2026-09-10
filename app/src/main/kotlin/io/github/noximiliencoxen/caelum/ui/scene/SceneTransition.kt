package io.github.noximiliencoxen.caelum.ui.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Quale scena c'e', quale sta arrivando, e a che punto e' il passaggio.
 *
 * **Il caso difficile non e' il cambio, e' il cambio interrotto.** Scorrendo la
 * barra delle ventiquattro ore il codice meteo cambia a ogni ora, quindi una
 * trascinata decisa puo' chiedere sei scene in mezzo secondo. Se ogni richiesta
 * facesse ripartire una dissolvenza da zero, si vedrebbe una poltiglia di
 * scene mai arrivate.
 *
 * Qui una richiesta nuova **chiude di colpo quella in corso**: la scena che
 * stava arrivando diventa quella che c'e', e la dissolvenza nuova parte da li'.
 * Il risultato e' che scorrendo veloce si vedono le scene passare secche - che
 * e' onesto, perche' a quella velocita' nessuna transizione si vedrebbe comunque
 * - e appena il dito si ferma l'ultima arriva con la sua dissolvenza intera.
 *
 * L'alternativa - accodare le transizioni - farebbe finire la scena giusta
 * qualche secondo dopo che il dito si e' fermato, che e' il difetto peggiore:
 * un'app che insegue invece di rispondere.
 */
@Stable
class SceneTransition internal constructor(initial: SceneKind) {

    /** La scena che se ne sta andando. Nulla quando non c'e' un passaggio. */
    var outgoing: SceneKind? by mutableStateOf(null)
        private set

    /** La scena in scena, o quella che sta arrivando durante un passaggio. */
    var incoming: SceneKind by mutableStateOf(initial)
        private set

    internal val progress = Animatable(0f)

    /** Le lastre da tenere caricate: una, o due durante un passaggio. */
    val needed: List<SceneKind> get() = listOfNotNull(outgoing, incoming)

    internal suspend fun moveTo(kind: SceneKind) {
        if (kind == incoming && outgoing == null) return
        if (outgoing != null) {
            // Una dissolvenza gia' in corso non si somma: si chiude.
            progress.snapTo(1f)
            outgoing = null
        }
        if (kind == incoming) return
        outgoing = incoming
        incoming = kind
        progress.snapTo(0f)
        progress.animateTo(1f, tween(TRANSITION_MS, easing = FastOutSlowInEasing))
        outgoing = null
    }
}

/**
 * Novecento millisecondi, e non trecento.
 *
 * Una dissolvenza da interfaccia dura un terzo di secondo perche' deve togliersi
 * di mezzo. Questa invece **e' il contenuto**: e' il tempo che cambia, e va
 * guardata. Sfalsata per profondita' come e', a trecento millisecondi il fondo e
 * il primo piano finirebbero praticamente insieme e lo sfalsamento non si
 * leggerebbe affatto.
 */
private const val TRANSITION_MS = 900

@Composable
fun rememberSceneTransition(kind: SceneKind): SceneTransition {
    val state = remember { SceneTransition(kind) }
    LaunchedEffect(kind) { state.moveTo(kind) }
    return state
}
