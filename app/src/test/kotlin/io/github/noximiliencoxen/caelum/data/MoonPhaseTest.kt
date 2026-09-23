package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/**
 * La luna, e le tre formule che erano scritte due volte.
 *
 * `render3d/Bodies.kt` - il disegnatore dei widget - si ricalcolava in casa la
 * mediana, la crescenza e la gibbosita', **con le stesse identiche
 * espressioni** che stanno in [MoonPhase]. Due copie della stessa formula sono
 * una formula che un giorno diverge; e intanto facevano risultare
 * `MoonPhase.terminator` e `MoonPhase.waxing` "non chiamate da nessuno" a ogni
 * giro di pulizia, cioe' candidate alla cancellazione sbagliata.
 *
 * Adesso la strada e' una. Questa prova tiene ferme le tre uguaglianze **a
 * livello di bit**, perche' la sostituzione valeva solo se i numeri sono gli
 * stessi: un disegno che si sposta di un pixel per una parentesi diversa
 * sarebbe esattamente il difetto che unificare doveva evitare.
 */
class MoonPhaseTest {

    /** Le fasi provate: tutto il ciclo a passo fine, estremi compresi. */
    private val fasi: List<Float> = (0..1000).map { it / 1000f }

    @Test
    fun `il terminatore e' quello che il disegno calcolava a mano`() {
        fasi.forEach { fase ->
            val aMano = abs(cos(2.0 * Math.PI * fase).toFloat())
            assertEquals(
                "fase $fase",
                aMano.toRawBits(),
                MoonPhase.terminator(fase).toRawBits(),
            )
        }
    }

    @Test
    fun `la crescenza e' quella che il disegno calcolava a mano`() {
        fasi.forEach { fase ->
            assertEquals("fase $fase", fase < 0.5f, MoonPhase.waxing(fase))
        }
    }

    /**
     * Qui l'uguaglianza non e' fra i numeri ma fra le **decisioni**:
     * [MoonPhase.illumination] aggiunge un `coerceIn(0f, 1f)` che il calcolo a
     * mano non aveva. Un taglio a zero e uno a uno non possono spostare un
     * confronto con mezzo - sotto resta sotto, sopra resta sopra - ed e'
     * esattamente cio' che questa riga verifica invece di darlo per ovvio.
     */
    @Test
    fun `la gibbosita' decide come decideva il calcolo a mano`() {
        fasi.forEach { fase ->
            val aMano = ((1f - cos(2.0 * Math.PI * fase).toFloat()) / 2f) > 0.5f
            assertEquals("fase $fase", aMano, MoonPhase.illumination(fase) > 0.5f)
        }
    }

    @Test
    fun `novilunio e plenilunio stanno dove devono`() {
        assertEquals(0f, MoonPhase.illumination(0f), 0.0001f)
        assertEquals(1f, MoonPhase.illumination(0.5f), 0.0001f)
        assertTrue(MoonPhase.waxing(0.25f))
        assertTrue(!MoonPhase.waxing(0.75f))
        // Al primo e all'ultimo quarto la mediana e' una riga dritta.
        assertEquals(0f, MoonPhase.terminator(0.25f), 0.0001f)
        assertEquals(0f, MoonPhase.terminator(0.75f), 0.0001f)
    }
}
