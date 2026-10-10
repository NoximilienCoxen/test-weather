package io.github.noximiliencoxen.caelum.ui.sala

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Quanto si rimpicciolisce un pannello al massimo del gesto: il dieci per cento. */
private const val RIDUZIONE_MASSIMA = 0.10f

/**
 * La scala di un pannello a metà del gesto di ritorno. `progresso` va da 0 (dito
 * appena sceso) a 1 (gesto completo); fuori da quell'intervallo si ferma ai
 * bordi, perché il sistema può consegnare valori sporchi a fine corsa.
 */
internal fun scalaIndietro(progresso: Float): Float =
    1f - RIDUZIONE_MASSIMA * progresso.coerceIn(0f, 1f)

/**
 * Il ritorno predittivo di un pannello a schermo pieno. Al posto di
 * `BackHandler(enabled, onBack)`, e **va chiamato nello stesso punto**: la
 * precedenza del tasto indietro dipende dall'ordine di composizione, non
 * dall'impilamento (vedi il commento sopra le impostazioni in `SalaShell`).
 *
 * Restituisce la scala da applicare al pannello. Durante il gesto segue il dito;
 * se il gesto viene annullato torna a 1 con una molla; se viene confermato
 * chiama `alIndietro` (che avvia la chiusura già esistente, la molla di
 * `scorrimento*`) e la scala torna a 1 mentre il pannello esce.
 */
@Composable
internal fun indietroPredittivo(attivo: Boolean, alIndietro: () -> Unit): Float {
    val scala = remember { Animatable(1f) }
    PredictiveBackHandler(enabled = attivo) { gesto ->
        try {
            gesto.collect { scala.snapTo(scalaIndietro(it.progress)) }
            alIndietro()
            scala.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                scala.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
            }
            throw e
        }
    }
    return scala.value
}
