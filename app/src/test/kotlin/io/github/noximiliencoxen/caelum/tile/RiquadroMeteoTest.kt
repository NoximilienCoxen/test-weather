package io.github.noximiliencoxen.caelum.tile

import io.github.noximiliencoxen.caelum.prefs.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class RiquadroMeteoTest {

    @Test
    fun `gradi e tempo sopra, la citta' sotto`() {
        assertEquals("21° · Sereno" to "Forlì", testiRiquadro(21.4, 0, "Forlì", TempUnit.CELSIUS))
    }

    @Test
    fun `in fahrenheit`() {
        assertEquals("70° · Sereno", testiRiquadro(21.0, 0, "Forlì", TempUnit.FAHRENHEIT).first)
    }

    @Test
    fun `senza previsione dice almeno di dove parla`() {
        assertEquals("--°" to "Forlì", testiRiquadro(null, null, "Forlì", TempUnit.CELSIUS))
    }
}
