package com.forli.meteo.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.floor

/**
 * Il battito della scena: un solo orologio per tutto quello che si muove.
 *
 * ## Perche' uno solo
 *
 * Pioggia, neve, uccelli, banchi di nebbia e oscillazione delle nuvole
 * potrebbero avere ciascuno il proprio ciclo. Non devono: ogni ciclo e' un
 * `LaunchedEffect` che chiede fotogrammi per conto suo, e cinque richiedenti
 * indipendenti sullo stesso schermo litigano - due si svegliano nello stesso
 * millisecondo e ne producono due, uno resta acceso quando gli altri hanno
 * finito. Un battito solo, che qualunque disegno legge, e' anche l'unico modo
 * di sapere con certezza quando la scena e' davvero ferma.
 *
 * ## Perche' non torna mai a zero
 *
 * Era **questo** lo stacco netto della neve e della pioggia. Il valore veniva
 * ricavato con `elapsed % CICLO`, cioe' tornava a zero ogni ciclo, e ogni
 * particella ne ricavava la propria posizione con `(fase + valore * velocita')`.
 * Finche' la velocita' vale esattamente uno il salto non si vede; ma le
 * velocita' erano sparse fra 0,85 e 1,35 apposta, per dare parallasse, e per
 * ognuna di esse il ritorno a zero e' una discontinuita'. Risultato: tutte le
 * gocce saltavano insieme, allo stesso istante, un paio di volte al secondo.
 *
 * Qui il tempo **non torna indietro mai**. Il conto sta in un `Double` e non in
 * un `Float` non per pignoleria: il modulo si fa su `secondi * velocita'`, e in
 * singola precisione dopo una decina di minuti quel prodotto ha una risoluzione
 * peggiore del fotogramma, cioe' l'animazione comincia a scattare da sola.
 */
@Stable
class SceneClock {

    /** Secondi dall'accensione del battito. Cresce e basta. */
    var seconds by mutableDoubleStateOf(0.0)
        private set

    internal fun advance(delta: Double) {
        seconds += delta
    }

    internal fun restart() {
        seconds = 0.0
    }

    /**
     * La fase di un ciclo che compie [rate] giri al secondo, da 0 a 1.
     *
     * Continua per costruzione: chi la chiama non vede mai il tempo tornare
     * indietro, vede solo la propria fase passare da 1 a 0, che e' il punto in
     * cui la particella e' comunque appena rinata in cima.
     */
    fun cycle(rate: Float): Float {
        val turns = seconds * rate
        return (turns - floor(turns)).toFloat()
    }
}

/**
 * Il battito, acceso solo mentre [running] e' vero.
 *
 * Spento, l'app torna a disegnare zero fotogrammi. Acceso, ne chiede uno per
 * ogni fotogramma dello schermo: e' il prezzo di qualunque cosa si muova, e non
 * lo si paga due volte perche' il battito e' uno.
 */
@Composable
fun rememberSceneClock(running: Boolean): SceneClock {
    val clock = remember { SceneClock() }
    LaunchedEffect(clock, running) {
        if (!running) return@LaunchedEffect
        // Non riparte da zero: riprendere il conto da dove si era interrotto
        // evita che tornando dal secondo piano tutta la neve salti di colpo in
        // una posizione nuova.
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    // Il tetto serve al rientro dal secondo piano: fra l'ultimo
                    // fotogramma di ieri sera e il primo di stamattina possono
                    // esserci ore, e senza limite la neve farebbe un balzo di
                    // diecimila cicli in un fotogramma solo.
                    clock.advance(((now - previous) / 1e9).coerceIn(0.0, 0.05))
                }
                previous = now
            }
        }
    }
    return clock
}
