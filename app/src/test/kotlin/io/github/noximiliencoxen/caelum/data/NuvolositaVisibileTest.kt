package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvolositaVisibileTest {

    @Test
    fun `un velo di cirri resta una giornata di sole`() {
        // Il caso visto sul telefono: totale alto, tutto in quota.
        val visibile = nuvolositaVisibile(totale = 85, bassa = 0, media = 0, alta = 85)!!
        assertTrue("cirri all'85% visti come $visibile%", visibile < 25)
    }

    @Test
    fun `le nubi basse contano per intero`() {
        assertEquals(90, nuvolositaVisibile(totale = 90, bassa = 90, media = 0, alta = 0))
    }

    @Test
    fun `le quote si combinano come coperture indipendenti`() {
        // Libero: 0,5 * 0,5 = 0,25 -> coperto 75.
        assertEquals(75, nuvolositaVisibile(totale = 80, bassa = 50, media = 50, alta = 0))
    }

    @Test
    fun `senza le quote resta il totale`() {
        assertEquals(64, nuvolositaVisibile(totale = 64, bassa = null, media = null, alta = null))
        assertEquals(null, nuvolositaVisibile(totale = null, bassa = null, media = null, alta = null))
    }
}
