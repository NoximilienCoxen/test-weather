package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Il giro attorno all'asse verticale, uguale in ogni sala che ne ha uno.
 *
 * Sta in un file suo perche' lo usano la scultura di Sala I e la luna di Sala
 * IV, e due copie della stessa molla divergono al primo che ne tara una.
 *
 * **Il verso e' opposto al dito, e non e' un capriccio.** Si trascina un
 * oggetto, non una telecamera: portando il dito a sinistra l'oggetto deve
 * girare come se lo si spingesse da davanti, cioe' mostrare il fianco destro.
 *
 * ---
 *
 * **Perche' l'angolo e' un `mutableFloatStateOf` scritto sul posto, e non un
 * `Animatable`.** Questa spiegazione e' l'unico motivo per cui il file esiste
 * in questa forma, e va letta prima di "semplificarla": e' la terza volta che
 * il progetto ci inciampa.
 *
 * La prima stesura di questo file passava da un `Animatable`, e ogni delta del
 * dito apriva una coroutine per il proprio `snapTo`. Il dispatcher della
 * composizione consegna **al fotogramma, non subito**: gli ultimi `snapTo` di
 * un gesto veloce finivano quindi *dopo* l'avvio dell'animazione di rilascio, e
 * un `Animatable` che riceve uno `snapTo` **annulla l'animazione in corso**. Da
 * fuori si vedeva l'oggetto partire e piantarsi a meta' giro senza tornare a
 * posto, e la cosa era tanto piu' probabile quanto piu' veloce era stato il
 * gesto - cioe' proprio quando il lancio contava. Con un valore scritto
 * direttamente non c'e' piu' una coda da cui possa uscire qualcosa in ritardo,
 * e la molla e' l'unica cosa in grado di muovere l'angolo dopo il rilascio.
 *
 * **E perche' il rilascio e' una animazione sola.** Prima erano due in fila -
 * un decadimento che doveva esaurirsi e poi una molla lenta che riportava a
 * zero - e messe in fila costavano oltre tre secondi: il decadimento con
 * attrito 1,1 se ne prendeva circa uno e mezzo, la molla a rigidita' 45 altri
 * due buoni per portare trecentosessanta gradi sotto la soglia di visibilita'.
 * Adesso l'energia del lancio entra come **velocita' iniziale** di una molla
 * sola che punta gia' al posto giusto: si stima dove finirebbe scorrendo e si
 * sceglie il giro intero piu' vicino. Un lancio piano riporta l'oggetto dov'era,
 * uno deciso lo fa girare su se stesso una volta o due e lo lascia nella stessa
 * posa. In entrambi i casi torna a casa, ma quanto gira lo decide la mano.
 */
@Stable
class Giro internal constructor(private val scope: CoroutineScope) {

    /** L'angolo, in gradi. Si legge **dentro il disegno**, mai in composizione. */
    private val angolo = mutableFloatStateOf(0f)

    /** La molla del rilascio, se ne sta girando una. Una sola, sempre. */
    private var assestamento: Job? = null

    /** Angolo imposto dall'aggancio di cattura. Nullo quando comanda il dito. */
    private val imposto = mutableStateOf<Float?>(null)

    /** L'angolo da disegnare. */
    val gradi: Float get() = imposto.value ?: angolo.floatValue

    /**
     * Blocca la scena a un angolo, o la libera con `null`.
     *
     * Serve alla cattura automatica e a nient'altro: i difetti che si vedono
     * girando vanno fotografati **girati**, e un trascinamento simulato non
     * arriva oltre la larghezza dello schermo.
     *
     * La molla va fermata qui dentro. Senza, l'angolo imposto e l'assestamento
     * si contendono la scena e lo scatto esce a un angolo che nessuno ha
     * chiesto - che e' esattamente il modo in cui una prova smette di provare
     * qualcosa senza dirlo.
     */
    fun imponi(gradi: Float?) {
        imposto.value = gradi
        if (gradi != null) {
            assestamento?.cancel()
            assestamento = null
        }
    }

    internal fun inizia() {
        // Il dito riprende il comando: qualunque molla in corso ha finito il suo
        // compito. Fermarla **qui**, e non lasciare che sia il valore nuovo a
        // contendersi l'angolo con lei, e' cio' che rende il gesto sempre
        // vincente sull'animazione. Un ritorno non interrompibile, sotto un
        // dito, si sente come un ritardo dell'app.
        assestamento?.cancel()
        assestamento = null
    }

    internal fun trascina(deltaPx: Float) {
        angolo.floatValue -= deltaPx * GRADI_PER_PIXEL
    }

    internal fun rilascia(velocitaPx: Float) {
        val lancio = (-velocitaPx * GRADI_PER_PIXEL).coerceIn(-LANCIO_MAX, LANCIO_MAX)
        val da = angolo.floatValue
        val bersaglio = ((da + lancio * SCORRIMENTO) / GIRO_INTERO).roundToInt() * GIRO_INTERO

        assestamento?.cancel()
        assestamento = scope.launch {
            animate(
                initialValue = da,
                targetValue = bersaglio,
                initialVelocity = lancio,
                animationSpec = spring(
                    // Smorzamento alto e rigidita' media: la sovraelongazione
                    // resta attorno all'uno per cento, cioe' nessun rimbalzo
                    // percepibile. Si assesta prima **senza** diventare
                    // scattante, perche' e' salita solo la rigidita'.
                    dampingRatio = 0.82f,
                    stiffness = 90f,
                    // Mezzo decimo di grado: senza una soglia dichiarata la
                    // molla resta formalmente viva a strascicare centesimi di
                    // grado, e ogni fotogramma sprecato li' e' un ridisegno
                    // intero della scultura a schermo fermo - la trappola #8
                    // rientrata dalla porta di servizio.
                    visibilityThreshold = 0.05f,
                ),
            ) { valore, _ -> angolo.floatValue = valore }

            // Un giro intero e' indistinguibile da nessun giro: riportare il
            // conto a zero non si vede e impedisce all'angolo di crescere senza
            // fine.
            angolo.floatValue = 0f
            assestamento = null
        }
    }

    private companion object {
        /**
         * Quanti gradi vale un pixel di dito. Piu' piano e l'oggetto sembra
         * incollato al vetro, piu' svelto e il minimo tremolio lo fa girare.
         */
        const val GRADI_PER_PIXEL = 0.30f

        const val GIRO_INTERO = 360f

        /** Per quanti secondi si immagina che il lancio scorra. */
        const val SCORRIMENTO = 0.28f

        /** Gradi al secondo. Oltre, un colpo di dito diventa una trottola. */
        const val LANCIO_MAX = 1900f
    }
}

/**
 * L'angolo di giro di una sala, azzerato a ogni ingresso nella stanza.
 *
 * @param imposto l'angolo chiesto dall'aggancio di cattura, se c'e'.
 */
@Composable
fun rememberGiro(imposto: Float? = null): Giro {
    val scope = rememberCoroutineScope()
    val giro = remember(scope) { Giro(scope) }
    LaunchedEffect(imposto) { giro.imponi(imposto) }
    return giro
}

/**
 * Il gesto che gira la scena.
 *
 * Solo orizzontale, e non e' un dettaglio: il verticale e' l'asse con cui il
 * carosello cambia sala. Un riconoscitore che accetta qualunque direzione se lo
 * mangerebbe, e il gesto decorativo bloccherebbe quello utile (trappola #5).
 */
@Composable
fun Modifier.giroConLancio(giro: Giro): Modifier = draggable(
    state = rememberDraggableState { delta -> giro.trascina(delta) },
    orientation = Orientation.Horizontal,
    onDragStarted = { giro.inizia() },
    onDragStopped = { velocita -> giro.rilascia(velocita) },
)
