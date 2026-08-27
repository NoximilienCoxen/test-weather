package com.forli.meteo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    /**
     * Il respiro: l'oscillazione lenta che l'oggetto ha quando nessuno lo tocca.
     *
     * E' uno stato di Compose, ma viene letto **dentro il disegno** insieme al
     * resto dell'orientamento: respirare ridipinge e non ricompone.
     */
    private val breath = mutableFloatStateOf(0f)

    /**
     * Angolo imposto dall'aggancio di verifica, in gradi.
     *
     * Serve a fotografare la cifra a un angolo preciso: senza, l'unico modo di
     * guardarla a ottanta gradi e' tenere il dito fermo li' e sperare che lo
     * scatto arrivi in tempo, e due esecuzioni non producono mai la stessa
     * immagine - cioe' non si puo' confrontare un prima con un dopo.
     */
    private val forced = mutableStateOf<Float?>(null)

    internal fun force(degrees: Float?) {
        if (forced.value != degrees) forced.value = degrees
    }

    /**
     * Quanto il respiro conta adesso, da 0 a 1.
     *
     * Non e' uno stato osservabile: lo legge e lo scrive solo il battito, che
     * gira sul filo principale. Va a zero appena il dito tocca e ci resta
     * finche' la molla non ha finito, poi risale piano.
     */
    private var idle = 1f
    private var touching = false

    /**
     * Angolo attorno all'asse verticale, in gradi.
     *
     * Il comando del dito e il respiro si sommano, e non e' un dettaglio: cosi'
     * il dito ha **priorita' assoluta** per costruzione invece che per una
     * regola scritta da qualche parte. Mentre si trascina, il respiro vale zero
     * e quello che si vede e' esattamente dove sta il dito.
     */
    val yawDeg: Float get() = forced.value ?: (animated.value + breath.floatValue)

    internal fun begin() {
        raw = animated.value
        touching = true
    }

    /** Avanza il respiro di un fotogramma. Chiamato solo dal battito. */
    internal fun breathe(seconds: Float, delta: Float) {
        // Il peso scende in fretta e risale piano: interrompere il respiro deve
        // essere immediato, riprenderlo no, altrimenti al rilascio l'oggetto
        // riceve due movimenti insieme e sembra che gli scappi di mano.
        idle = if (touching) {
            (idle - delta * FADE_OUT).coerceAtLeast(0f)
        } else {
            (idle + delta * FADE_IN).coerceAtMost(1f)
        }
        // Due seni incommensurabili fra loro: con uno solo si riconosce il
        // periodo, e un oggetto che oscilla a tempo non respira, fa il
        // metronomo.
        val wave = sin(seconds * BREATH_RATE) * 0.72f +
            sin(seconds * BREATH_RATE * 0.41f + 1.3f) * 0.28f
        val next = wave * BREATH_DEGREES * idle

        // Zona morta, come per il sensore che c'era prima e per lo stesso
        // motivo. Il respiro si muove al massimo di due gradi al secondo, cioe'
        // tre centesimi di grado per fotogramma, e vicino alle inversioni molto
        // meno: scrivere ogni battito fa ridisegnare l'intera scena per uno
        // spostamento che non copre un pixel. Sotto la soglia il valore di
        // prima e' altrettanto vero, e la scena resta ferma finche' non lo e'
        // piu'.
        if (abs(next - breath.floatValue) > BREATH_DEADBAND) breath.floatValue = next
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
        // Solo adesso il respiro puo' tornare: durante la molla lo si
        // vedrebbe combattere contro il rientro.
        touching = false
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

        /**
         * Ampiezza del respiro, in gradi.
         *
         * Tre gradi e mezzo: abbastanza perche' la faccia si accorci quel tanto
         * che basta a vedere che l'oggetto e' li' e non stampato, troppo pochi
         * perche' qualcuno lo chiami movimento. Sopra i cinque comincia a
         * sembrare che l'app stia facendo qualcosa.
         */
        const val BREATH_DEGREES = 3.5f

        /** Radianti al secondo: un ciclo ogni undici secondi circa. */
        const val BREATH_RATE = 0.57f

        /** Al tocco il respiro sparisce in un decimo di secondo. */
        const val FADE_OUT = 10f

        /** Al rilascio torna in un secondo e mezzo. */
        const val FADE_IN = 0.66f

        /**
         * Sotto questo scostamento, in gradi, non si riscrive.
         *
         * Sei centesimi di grado sono meno del due per cento dell'ampiezza: a
         * schermo, sulla cifra piu' grande, valgono una frazione di pixel.
         */
        const val BREATH_DEADBAND = 0.06f
    }
}

/**
 * @param breathing falso quando la scena non si vede - dietro le impostazioni,
 *   o col foglio del dettaglio alzato - e allora il battito si spegne del tutto
 *   e l'app torna a disegnare zero fotogrammi.
 */
@Composable
fun rememberSceneRotation(
    breathing: Boolean = true,
    forcedYawDeg: Float? = null,
    onFullTurn: () -> Unit = {},
): SceneRotation {
    val scope = rememberCoroutineScope()
    // Il richiamo viene tenuto aggiornato invece che catturato: la lambda
    // arriva nuova a ogni composizione, e legarla dentro il `remember`
    // significherebbe chiamare per sempre quella della prima volta.
    val live by rememberUpdatedState(onFullTurn)
    val rotation = remember(scope) { SceneRotation(scope) { live() } }
    SideEffect { rotation.force(forcedYawDeg) }

    // Il battito del respiro. Scrive un solo numero, che il disegno legge:
    // nessuna ricomposizione, e il lavoro per fotogramma e' un seno.
    LaunchedEffect(rotation, breathing, forcedYawDeg != null) {
        // Con l'angolo imposto il respiro si spegne: uno scatto deve essere
        // ripetibile, e un oggetto che oscilla non lo e'.
        if (!breathing || forcedYawDeg != null) {
            rotation.breathe(0f, 0f)
            return@LaunchedEffect
        }
        var origin = 0L
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (origin == 0L) {
                    origin = now
                    previous = now
                }
                val delta = ((now - previous) / 1e9f).coerceIn(0f, 0.05f)
                previous = now
                rotation.breathe((now - origin) / 1e9f, delta)
            }
        }
    }
    return rotation
}

/**
 * Il gesto della scena: uno solo, che decide da che parte va.
 *
 * ## Perche' non sono due
 *
 * Prima erano due `draggable` innestati, uno orizzontale sulla scena per la
 * rotazione e uno verticale su tutta la schermata per il foglio del dettaglio.
 * Sembra la soluzione pulita - ognuno il suo asse - e non lo e': **entrambi si
 * armano sullo stesso tocco**, ognuno aspetta di superare la soglia sul proprio
 * asse, e chi la supera per primo vince mentre l'altro **si annulla per tutto
 * il resto del gesto**.
 *
 * Il risultato e' una monetina lanciata nei primi pixel. Un dito non parte mai
 * perfettamente orizzontale: se i primissimi millimetri hanno un filo di
 * verticale in piu', il tocco va al foglio, e da quel momento si puo' scorrere
 * di lato quanto si vuole senza che il numero giri. Ecco perche' a volte si
 * riusciva a ruotare e a volte no, con lo stesso identico gesto.
 *
 * Qui il riconoscitore e' uno. Accumula lo spostamento finche' non supera la
 * soglia su un asse qualunque, poi guarda **quale dei due componenti e' piu'
 * grande** e da li' in avanti non cambia piu' idea. Nessuna gara, nessuno che
 * si annulla: la direzione la decide il gesto nel suo insieme, non i suoi primi
 * tre pixel.
 *
 * ## E perche' l'orizzontale ha un piccolo vantaggio
 *
 * Dentro la scena il gesto naturale e' girare l'oggetto, e il foglio si tira su
 * anche da tutta la fascia sotto - condizione, barra delle ore, ora mostrata -
 * dove questo riconoscitore non arriva. Perche' il tocco sia verticale serve
 * quindi che la verticale sia **chiaramente** prevalente, non che vinca per un
 * pixel. Chi vuole aprire il dettaglio tira su, e tirando su la prevalenza c'e'
 * tutta; chi vuole girare non deve piu' stare attento a come appoggia il dito.
 */
@Composable
fun Modifier.scenePointer(
    rotation: SceneRotation,
    onVerticalDelta: (Float) -> Unit,
    onVerticalSettle: suspend (Float) -> Unit,
): Modifier {
    val liveDelta by rememberUpdatedState(onVerticalDelta)
    val liveSettle by rememberUpdatedState(onVerticalSettle)
    // Il rientro della molla e l'assestamento del foglio sono sospesi, e vanno
    // lanciati **fuori** dal riconoscitore: aspettarli li' dentro terrebbe
    // fermo il riconoscitore fino a molla finita, e il dito non potrebbe
    // riafferrare l'oggetto mentre torna a posto.
    val scope = rememberCoroutineScope()
    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            var travelled = Offset.Zero
            var axis = Axis.UNDECIDED

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) break

                val step = change.positionChange()
                travelled += step
                tracker.addPosition(change.uptimeMillis, change.position)

                if (axis == Axis.UNDECIDED &&
                    (abs(travelled.x) > slop || abs(travelled.y) > slop)
                ) {
                    axis = if (abs(travelled.y) > abs(travelled.x) * VERTICAL_EDGE) {
                        Axis.VERTICAL
                    } else {
                        Axis.HORIZONTAL
                    }
                    if (axis == Axis.HORIZONTAL) rotation.begin()
                }

                when (axis) {
                    // Consumare e' cio' che tiene fuori il riconoscitore
                    // verticale che avvolge tutta la schermata: senza, quello
                    // continuerebbe a guardare lo stesso tocco e la gara
                    // tornerebbe da dove l'abbiamo tolta.
                    Axis.HORIZONTAL -> {
                        change.consume()
                        rotation.drag(step.x)
                    }
                    Axis.VERTICAL -> {
                        change.consume()
                        liveDelta(step.y)
                    }
                    Axis.UNDECIDED -> Unit
                }
            }

            val velocity = tracker.calculateVelocity()
            when (axis) {
                Axis.HORIZONTAL -> scope.launch { rotation.release(velocity.x) }
                Axis.VERTICAL -> scope.launch { liveSettle(velocity.y) }
                Axis.UNDECIDED -> Unit
            }
        }
    }
}

private enum class Axis { UNDECIDED, HORIZONTAL, VERTICAL }

/**
 * Quanto la verticale deve prevalere sull'orizzontale per prendersi il gesto.
 *
 * Uno e mezzo: un trascinamento verso l'alto vero supera abbondantemente questa
 * soglia, un movimento di lato con un po' di tremolio no.
 */
private const val VERTICAL_EDGE = 1.5f
