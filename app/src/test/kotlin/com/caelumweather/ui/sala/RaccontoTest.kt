package com.caelumweather.ui.sala

import com.caelumweather.data.HourForecast
import com.caelumweather.prefs.SalaWindUnit
import com.caelumweather.prefs.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Titolo e paragrafo di Sala I vengono dalle ore, non da frasi fisse. I casi
 * sono quelli visti negli scatti del 15 novembre.
 */
class RaccontoTest {

    private val t0 = LocalDateTime.of(2026, 11, 15, 13, 0)

    private fun ore(
        n: Int = 24,
        mm: (Int) -> Double = { 0.0 },
        nuvole: (Int) -> Int = { 95 },
        temp: (Int) -> Double = { 9.0 },
        codice: (Int) -> Int = { if (mm(it) > 0) 61 else 3 },
        raffiche: (Int) -> Double = { 3.0 },
    ) = (0 until n).map { i ->
        HourForecast(
            time = t0.plusHours(i.toLong()),
            temperature = temp(i),
            weatherCode = codice(i),
            precipitation = mm(i),
            cloudCover = nuvole(i),
            windGusts = raffiche(i),
        )
    }

    private fun racconta(h: List<HourForecast>) =
        raccontoDellOra(h, t0, TempUnit.CELSIUS, SalaWindUnit.KMH)!!

    @Test
    fun `Forli alle 13 - coperto, la pioggia arriva alle 2 di notte`() {
        // Il caso di "Rovescio di meta' pomeriggio" con 0,0 mm.
        val r = racconta(ore(mm = { if (it in 13..14) 0.4 else 0.0 }))
        assertEquals("Coperto, pioggia dalle 02:00 di domani", r.titolo)
        assertTrue(r.corpo, r.corpo.startsWith("Circa 2 ore, 0,8 mm in tutto."))
    }

    @Test
    fun `piove adesso - si dice fino a quando e quanta`() {
        val r = racconta(ore(mm = { if (it < 3) 1.2 else 0.0 }, nuvole = { if (it < 3) 100 else 60 }))
        assertEquals("Pioggia moderata fino alle 16:00", r.titolo)
        assertTrue(r.corpo, r.corpo.startsWith("3,6 mm fino ad allora, poi nuvoloso."))
    }

    @Test
    fun `asciutto e coperto - nessuna promessa di sole`() {
        val r = racconta(ore())
        assertEquals("Coperto e asciutto", r.titolo)
        assertTrue(r.corpo.contains("Nessuna pioggia nelle prossime 18 ore."))
        assertFalse(r.titolo.contains("passaggio"))
    }

    @Test
    fun `il cielo che si apre si dice con l'ora`() {
        val r = racconta(ore(nuvole = { if (it < 4) 95 else 20 }))
        assertEquals("Coperto, si apre verso le 17:00", r.titolo)
    }

    @Test
    fun `un'ora sola di schiarita e' rumore`() {
        val r = racconta(ore(nuvole = { if (it == 4) 20 else 95 }))
        assertEquals("Coperto e asciutto", r.titolo)
    }

    @Test
    fun `la temperatura dice dove va`() {
        val sale = racconta(ore(temp = { listOf(9.0, 10.0, 12.0, 11.0).getOrElse(it) { 8.0 } }))
        assertTrue(sale.corpo, sale.corpo.contains("Massima di 12° verso le 15:00."))
        val ferma = racconta(ore())
        assertTrue(ferma.corpo, ferma.corpo.contains("Temperatura ferma intorno a 9°."))
    }

    @Test
    fun `le raffiche solo quando contano`() {
        assertFalse(racconta(ore()).corpo.contains("Raffiche"))
        val forti = racconta(ore(raffiche = { if (it == 5) 16.7 else 5.0 }))
        assertTrue(forti.corpo, forti.corpo.contains("Raffiche fino a 60 km/h verso le 18:00."))
    }

    @Test
    fun `temporale senza aggettivo, neve col suo nome`() {
        val t = racconta(ore(mm = { if (it < 2) 5.0 else 0.0 }, codice = { if (it < 2) 95 else 3 }))
        assertEquals("Temporali fino alle 15:00", t.titolo)
        val n = racconta(ore(mm = { if (it < 2) 0.3 else 0.0 }, codice = { if (it < 2) 73 else 3 }))
        assertEquals("Neve debole fino alle 15:00", n.titolo)
    }

    @Test
    fun `senza ore da raccontare si ripiega`() {
        assertNull(raccontoDellOra(emptyList(), t0, TempUnit.CELSIUS, SalaWindUnit.KMH))
    }
}
