package io.github.noximiliencoxen.caelum.ui.sala.rooms.rilievo

import io.github.noximiliencoxen.caelum.data.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RilievoTest {

    private val inizio = LocalDateTime.of(2026, 9, 24, 0, 0)

    private fun ore(giorni: Int) = (0 until giorni * 24).map { h ->
        HourForecast(inizio.plusHours(h.toLong()), temperature = 10.0 + (h % 24) / 2.0 + h / 24)
    }

    @Test
    fun `una riga per giorno e ventiquattro ore`() {
        val r = Rilievo.da(ore(8))
        assertNotNull(r)
        r!!
        assertEquals(7, r.righe)
        assertEquals(24, r.colonne)
        assertEquals(10f, r.minima, 1e-4f)
        assertEquals(10f + 23 / 2f + 6f, r.massima, 1e-4f)
    }

    @Test
    fun `la quota va da 0 alla minima a 1 alla massima`() {
        val r = Rilievo.da(ore(7))!!
        assertEquals(0f, r.quota(0, 0), 1e-4f)
        assertEquals(1f, r.quota(6, 23), 1e-4f)
    }

    @Test
    fun `con un giorno solo non c'e' rilievo`() {
        assertNull(Rilievo.da(ore(1)))
    }

    @Test
    fun `il tocco sceglie il punto piu' vicino fra i visibili`() {
        val punti = floatArrayOf(0f, 0f, 10f, 0f, 20f, 0f)
        val visibili = booleanArrayOf(true, false, true)
        assertEquals(0, Rilievo.piuVicino(punti, visibili, 9f, 1f, 30f))
        assertEquals(2, Rilievo.piuVicino(punti, visibili, 16f, 0f, 30f))
        assertEquals(-1, Rilievo.piuVicino(punti, visibili, 100f, 0f, 30f))
    }
}
