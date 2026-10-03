package io.github.noximiliencoxen.caelum.ui.sala

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Sala I senza una previsione: ne' arrivata, ne' salvata.
 *
 * Prima ci si leggeva "Pieno sole, aria calda" sopra i trattini (CONTESTO
 * §39.3): la condizione ripiegava sul sereno. Qui si prova che nessuna delle
 * due righe scritte al suo posto e' una didascalia del tempo.
 */
class SenzaPrevisioneTest {

    private val didascalieDelTempo = SalaCondition.entries.flatMap { c ->
        SalaPhase.entries.map { f -> salaTitle(c, f) }
    }.toSet()

    @Test
    fun `mentre si aspetta non si inventa un tempo`() {
        val titolo = titoloSenzaPrevisione(null)
        assertFalse(titolo, titolo in didascalieDelTempo)
    }

    @Test
    fun `se la richiesta fallisce il titolo dice perche'`() {
        assertEquals("Rete non raggiungibile", titoloSenzaPrevisione("Rete non raggiungibile"))
        assertFalse(corpoSenzaPrevisione("Rete non raggiungibile") == corpoSenzaPrevisione(null))
    }
}
