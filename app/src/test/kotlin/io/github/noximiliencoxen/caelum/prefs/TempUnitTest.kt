package io.github.noximiliencoxen.caelum.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La conversione dell'unita', che sta qui e non nella rete.
 *
 * Chiedere i Fahrenheit all'API vorrebbe dire rifare la richiesta per cambiare
 * un'unita' di misura, cioe' mettere un'attesa e una schermata vuota fra chi
 * tocca l'interruttore e il numero che voleva vedere.
 */
class TempUnitTest {

    @Test
    fun `i celsius restano se stessi`() {
        assertEquals(21.0, TempUnit.CELSIUS.from(21.0), 0.0001)
    }

    @Test
    fun `ventuno gradi fanno settanta fahrenheit`() {
        // E' la coppia verificata sul telefono, scritta in CONTESTO.
        assertEquals(70.0, TempUnit.FAHRENHEIT.from(21.0), 0.5)
    }

    @Test
    fun `lo zero e il sottozero`() {
        assertEquals(32.0, TempUnit.FAHRENHEIT.from(0.0), 0.0001)
        assertEquals(-40.0, TempUnit.FAHRENHEIT.from(-40.0), 0.0001)
    }
}
