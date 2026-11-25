package com.caelumweather.ui.sala

import com.caelumweather.data.QuartoDOra
import com.caelumweather.ui.sala.rooms.raccontoProssimaOra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** La frase della pioggia nei prossimi minuti, su Oggi (`ProssimaOra`). */
class ProssimaOraTest {

    private val adesso = LocalDateTime.of(2026, 11, 25, 12, 50)

    /** Dieci quarti d'ora dalle 12:45; [mm] per ciascuno, codice di pioggia dove bagnato. */
    private fun quarti(vararg mm: Double): List<QuartoDOra> = mm.mapIndexed { i, m ->
        QuartoDOra(LocalDateTime.of(2026, 11, 25, 12, 45).plusMinutes(15L * i), m, if (m > 0) 61 else 3)
    }

    @Test
    fun `asciutto per due ore non dice niente`() {
        assertNull(raccontoProssimaOra(quarti(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), adesso))
    }

    @Test
    fun `un velo d'umido sotto il decimo non e' pioggia`() {
        assertNull(raccontoProssimaOra(quarti(0.0, 0.05, 0.05, 0.0, 0.0, 0.0, 0.0, 0.0), adesso))
    }

    @Test
    fun `la pioggia che arriva dice fra quanto, arrotondato a cinque`() {
        // Bagnato dalle 13:15: fra 25 minuti dalle 12:50.
        val r = raccontoProssimaOra(quarti(0.0, 0.0, 0.3, 0.4, 0.2, 0.0, 0.0, 0.0), adesso)!!
        assertEquals("Pioggia debole fra 25 minuti", r.frase)
        assertEquals(8, r.millimetri.size)
    }

    @Test
    fun `l'intensita' viene dai millimetri del quarto d'ora`() {
        val r = raccontoProssimaOra(quarti(0.0, 0.0, 2.5, 2.0, 0.0, 0.0, 0.0, 0.0), adesso)!!
        assertTrue(r.frase, r.frase.startsWith("Pioggia forte"))
    }

    @Test
    fun `se piove adesso dice quando smette`() {
        val r = raccontoProssimaOra(quarti(0.5, 0.6, 0.4, 0.0, 0.0, 0.0, 0.0, 0.0), adesso)!!
        assertEquals("Pioggia adesso, smette verso le 13:30", r.frase)
    }
}
