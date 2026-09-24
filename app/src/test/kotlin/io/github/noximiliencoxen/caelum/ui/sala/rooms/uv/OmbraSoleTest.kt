package io.github.noximiliencoxen.caelum.ui.sala.rooms.uv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** L'altezza del sole a Forli', nei giorni in cui la si sa a memoria. */
class OmbraSoleTest {

    private val lat = 44.22
    private val lon = 12.04

    /** La piu' alta del giorno, cercata minuto per minuto: e' il mezzogiorno solare. */
    private fun massimaDel(giorno: LocalDate, scarto: Int): Double =
        (0 until 24 * 60).maxOf { m ->
            OmbraSole.altezza(giorno.atStartOfDay().plusMinutes(m.toLong()), lat, lon, scarto)
        }

    @Test
    fun `al solstizio d'estate il sole sale a circa 69 gradi`() {
        assertEquals(90 - lat + 23.44, massimaDel(LocalDate.of(2026, 6, 21), 7200), 0.6)
    }

    @Test
    fun `al solstizio d'inverno a circa 22`() {
        assertEquals(90 - lat - 23.44, massimaDel(LocalDate.of(2026, 12, 21), 3600), 0.6)
    }

    @Test
    fun `a mezzanotte e' sotto l'orizzonte`() {
        assertTrue(OmbraSole.altezza(LocalDateTime.of(2026, 6, 21, 0, 0), lat, lon, 7200) < 0)
    }

    @Test
    fun `il mezzogiorno solare d'estate cade verso le 13 e 15 dell'orologio`() {
        val giorno = LocalDate.of(2026, 6, 21)
        val minuto = (0 until 24 * 60).maxBy { m ->
            OmbraSole.altezza(giorno.atStartOfDay().plusMinutes(m.toLong()), lat, lon, 7200)
        }
        // Ora legale (+2) e Forli' a ovest del meridiano dei 30 gradi: circa 13:14.
        assertTrue("mezzogiorno solare al minuto $minuto", kotlin.math.abs(minuto - (13 * 60 + 14)) <= 6)
    }

    @Test
    fun `l'ombra e' lunga quanto la persona a 45 gradi, e non c'e' di notte`() {
        assertEquals(1.0, OmbraSole.lunghezzaRelativa(45.0)!!, 1e-9)
        assertNull(OmbraSole.lunghezzaRelativa(-3.0))
        assertEquals(OmbraSole.MASSIMA, OmbraSole.lunghezzaRelativa(0.5)!!, 1e-9)
    }
}
