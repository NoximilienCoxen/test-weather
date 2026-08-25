package com.forli.meteo.ui.render3d

import kotlin.math.cos
import kotlin.math.sin

/**
 * Una pila di trasformazioni: dal sistema di una parte a quello del modello.
 *
 * E' il pezzo che mancava per avere qualcosa di **articolato**. `Camera` sa
 * portare un punto dal modello allo schermo, ma non sa che un avambraccio ruota
 * attorno a un gomito che a sua volta pende da una spalla che sta su un torso
 * che si inclina. Questa e' quella catena: si entra in una parte con [push], si
 * chiedono i suoi punti con [at], si esce con [pop].
 *
 * **Nessuna matrice 4x4 e nessuna libreria.** Ogni livello tiene nove numeri di
 * rotazione e tre di traslazione dentro un unico array, riusato a ogni
 * fotogramma: un rig di sei livelli sono settantadue float, allocati una volta
 * sola alla costruzione. Il resto delle librerie di algebra qui non servirebbe a
 * nulla - non c'e' proiezione da comporre, di quella si occupa gia' la camera.
 *
 * **Attenzione all'ordine delle rotazioni**: prima attorno a x, poi a y, poi a
 * z. Non e' una convenzione universale ed e' quella che serve qui - la testa che
 * si gira (y) mentre il busto e' gia' inclinato (z) deve girare rispetto al
 * busto, non rispetto al mondo.
 */
class Rig(private val depth: Int = 8) {

    /** Dodici valori per livello: nove di rotazione, tre di traslazione. */
    private val stack = FloatArray(depth * SLOT)
    private var level = 0

    /** L'ultimo punto portato nel modello da [at]. */
    var x = 0f; private set
    var y = 0f; private set
    var z = 0f; private set

    /** Torna alla radice, con la parte allineata al modello. */
    fun reset() {
        level = 0
        val o = 0
        stack[o] = 1f; stack[o + 1] = 0f; stack[o + 2] = 0f
        stack[o + 3] = 0f; stack[o + 4] = 1f; stack[o + 5] = 0f
        stack[o + 6] = 0f; stack[o + 7] = 0f; stack[o + 8] = 1f
        stack[o + 9] = 0f; stack[o + 10] = 0f; stack[o + 11] = 0f
    }

    /**
     * Entra in una parte figlia.
     *
     * @param px la sua origine nel sistema del padre - il gomito rispetto alla
     *   spalla, la spalla rispetto al torso.
     * @param rxDeg rotazioni della parte attorno ai propri assi, in gradi.
     */
    fun push(
        px: Float,
        py: Float,
        pz: Float,
        rxDeg: Float = 0f,
        ryDeg: Float = 0f,
        rzDeg: Float = 0f,
    ) {
        if (level >= depth - 1) return
        val p = level * SLOT
        val c = p + SLOT
        level++

        val sa = sin(rxDeg * DEG); val ca = cos(rxDeg * DEG)
        val sb = sin(ryDeg * DEG); val cb = cos(ryDeg * DEG)
        val sc = sin(rzDeg * DEG); val cc = cos(rzDeg * DEG)

        // R = Rz * Ry * Rx, scritta per esteso: comporre tre matrici a runtime
        // costerebbe due prodotti completi per ogni giuntura di ogni fotogramma.
        val r0 = cc * cb
        val r1 = -sc * ca + cc * sb * sa
        val r2 = sc * sa + cc * sb * ca
        val r3 = sc * cb
        val r4 = cc * ca + sc * sb * sa
        val r5 = -cc * sa + sc * sb * ca
        val r6 = -sb
        val r7 = cb * sa
        val r8 = cb * ca

        // La rotazione del figlio nel modello e' quella del padre composta con
        // la propria; la sua origine e' il perno portato dal padre.
        stack[c] = stack[p] * r0 + stack[p + 1] * r3 + stack[p + 2] * r6
        stack[c + 1] = stack[p] * r1 + stack[p + 1] * r4 + stack[p + 2] * r7
        stack[c + 2] = stack[p] * r2 + stack[p + 1] * r5 + stack[p + 2] * r8
        stack[c + 3] = stack[p + 3] * r0 + stack[p + 4] * r3 + stack[p + 5] * r6
        stack[c + 4] = stack[p + 3] * r1 + stack[p + 4] * r4 + stack[p + 5] * r7
        stack[c + 5] = stack[p + 3] * r2 + stack[p + 4] * r5 + stack[p + 5] * r8
        stack[c + 6] = stack[p + 6] * r0 + stack[p + 7] * r3 + stack[p + 8] * r6
        stack[c + 7] = stack[p + 6] * r1 + stack[p + 7] * r4 + stack[p + 8] * r7
        stack[c + 8] = stack[p + 6] * r2 + stack[p + 7] * r5 + stack[p + 8] * r8

        stack[c + 9] = stack[p] * px + stack[p + 1] * py + stack[p + 2] * pz + stack[p + 9]
        stack[c + 10] = stack[p + 3] * px + stack[p + 4] * py + stack[p + 5] * pz + stack[p + 10]
        stack[c + 11] = stack[p + 6] * px + stack[p + 7] * py + stack[p + 8] * pz + stack[p + 11]
    }

    fun pop() {
        if (level > 0) level--
    }

    /** Porta un punto dal sistema della parte corrente a quello del modello. */
    fun at(lx: Float, ly: Float, lz: Float) {
        val o = level * SLOT
        x = stack[o] * lx + stack[o + 1] * ly + stack[o + 2] * lz + stack[o + 9]
        y = stack[o + 3] * lx + stack[o + 4] * ly + stack[o + 5] * lz + stack[o + 10]
        z = stack[o + 6] * lx + stack[o + 7] * ly + stack[o + 8] * lz + stack[o + 11]
    }

    private companion object {
        const val SLOT = 12
        const val DEG = (Math.PI / 180.0).toFloat()
    }
}
