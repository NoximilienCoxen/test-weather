package com.caelumweather.notifiche

import com.caelumweather.data.PrecipitazioneInArrivo
import com.caelumweather.data.TipoPrecipitazione
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ContoAllaRovesciaTest {

    private val adesso = LocalDateTime.of(2026, 11, 25, 14, 0)
    private fun alle(h: Int, m: Int) = PrecipitazioneInArrivo(TipoPrecipitazione.PIOGGIA, LocalDateTime.of(2026, 11, 25, h, m), 1.0)

    @Test
    fun `conta fino all'inizio dall'orologio del telefono, non dal fuso`() {
        // Il posto puo' stare in un altro fuso: conta solo la distanza.
        val c = contoAllaRovescia(adesso, alle(14, 25), oraMs = 1_000_000L)!!
        assertEquals(25, c.minuti)
        assertEquals(1_000_000L + 25 * 60_000L, c.inizioMs)
    }

    @Test
    fun `sotto i cinque minuti non si conta`() {
        assertNull(contoAllaRovescia(adesso, alle(14, 4)))
        assertNull(contoAllaRovescia(adesso, alle(13, 50)))
    }
}
