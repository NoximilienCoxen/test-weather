package com.forli.meteo.ui.render

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Il passaggio da un valore al successivo, a contachilometri.
 *
 * Tiene il valore da cui si viene e quanto e' avanzato il rotolamento. Chi
 * disegna li legge **dentro il disegno**, quindi la transizione ridipinge e non
 * ricompone: sono centinaia di fotogrammi per un cambio d'ora, e ricomporre a
 * ognuno vorrebbe dire pagare l'albero intero per far muovere una cifra.
 */
@Stable
class NumberRoll internal constructor(
    private val scope: CoroutineScope,
    private val slow: Boolean,
) {

    private val advance = mutableFloatStateOf(1f)
    private val outgoing = mutableStateOf<PreparedNumber?>(null)

    /** L'ultimo valore mostrato, da cui partira' il prossimo rotolamento. */
    private var last: PreparedNumber? = null
    private var lastChangeAt = 0L
    private var job: Job? = null

    /** Da 0 (il vecchio e' ancora al suo posto) a 1 (il nuovo e' arrivato). */
    val progress: Float get() = advance.floatValue

    /** Il valore da cui si sta arrivando, o nullo se non si sta rotolando. */
    val previous: PreparedNumber? get() = outgoing.value

    internal fun show(next: PreparedNumber?) {
        val from = last
        last = next

        val now = System.nanoTime()
        // Due valori a distanza di un battito vogliono dire che si sta
        // scorrendo la barra, non che si sta leggendo un numero. Rotolare a
        // ogni ora di uno scorrimento veloce significherebbe disegnare due
        // numeri per fotogramma proprio nel momento in cui ne serve uno solo, e
        // per un'animazione che nessuno fa in tempo a vedere.
        val hurried = now - lastChangeAt < QUICK_NS
        lastChangeAt = now

        job?.cancel()
        if (next == null || from == null || from === next || hurried) {
            outgoing.value = null
            advance.floatValue = 1f
            return
        }

        outgoing.value = from
        advance.floatValue = 0f
        job = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                // Al rallentatore l'animazione dura abbastanza da poterla
                // fotografare: quello che si vede in movimento va verificato in
                // movimento, e uno scatto preso a caso durante due decimi di
                // secondo non lo coglie mai.
                animationSpec = if (slow) {
                    tween(durationMillis = SLOW_MS)
                } else {
                    spring(dampingRatio = 0.94f, stiffness = 340f, visibilityThreshold = 0.002f)
                },
            ) { value, _ -> advance.floatValue = value }
            outgoing.value = null
        }
    }

    private companion object {
        /** Sotto questo intervallo fra due valori si sta scorrendo, non leggendo. */
        const val QUICK_NS = 150_000_000L

        const val SLOW_MS = 4_000
    }
}

@Composable
fun rememberNumberRoll(slow: Boolean = false): NumberRoll {
    val scope = rememberCoroutineScope()
    return remember(scope, slow) { NumberRoll(scope, slow) }
}
