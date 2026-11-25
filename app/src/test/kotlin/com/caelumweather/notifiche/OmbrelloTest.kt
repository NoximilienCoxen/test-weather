package com.caelumweather.notifiche

import com.caelumweather.data.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Il testo della notifica "porta l'ombrello" (`testiOmbrello`). */
class OmbrelloTest {

    private val giorno = LocalDate.of(2026, 11, 26)

    /** Le ventiquattro ore del giorno; [mm] per le ore indicate, 80 per cento dove bagnato. */
    private fun ore(vararg mm: Pair<Int, Double>, codice: Int = 61): List<HourForecast> = (0 until 24).map { h ->
        val m = mm.firstOrNull { it.first == h }?.second ?: 0.0
        HourForecast(giorno.atTime(h, 0), precipitation = m, precipProbability = if (m > 0) 80 else 5, weatherCode = if (m > 0) codice else 3)
    }

    @Test
    fun `nei giorni asciutti non dice niente`() {
        assertNull(testiOmbrello("Forli", ore(), giorno.atTime(7, 0)))
    }

    @Test
    fun `l'umido sotto i tre decimi non conta`() {
        assertNull(testiOmbrello("Forli", ore(10 to 0.1, 11 to 0.2), giorno.atTime(7, 0)))
    }

    @Test
    fun `la pioggia della notte non riguarda chi esce al mattino`() {
        assertNull(testiOmbrello("Forli", ore(23 to 3.0, 2 to 2.0), giorno.atTime(7, 0)))
    }

    @Test
    fun `dice la fascia e i millimetri`() {
        val (titolo, corpo) = testiOmbrello("Forli", ore(15 to 1.2, 16 to 2.5, 17 to 1.8), giorno.atTime(7, 0))!!
        assertEquals("Porta l'ombrello", titolo)
        assertEquals("Pioggia dalle 15 alle 18 circa a Forli: 6 mm in giornata.", corpo)
    }

    @Test
    fun `se piove gia' lo dice`() {
        val (_, corpo) = testiOmbrello("Forli", ore(7 to 1.0, 8 to 1.0, 9 to 0.6), giorno.atTime(7, 20))!!
        assertEquals("Pioggia già in corso, fino alle 10 circa a Forli: 3 mm in giornata.", corpo)
    }

    @Test
    fun `la neve ha il suo titolo`() {
        val (titolo, _) = testiOmbrello("Cortina", ore(10 to 1.0, 11 to 1.5, codice = 73), giorno.atTime(7, 0))!!
        assertEquals("Neve oggi a Cortina", titolo)
    }
}
