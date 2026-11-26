package com.caelumweather.ui.sala.rooms

import com.caelumweather.data.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CosaMettersiTest {

    private val mattino = LocalDateTime.of(2026, 11, 25, 8, 0)

    private fun giornata(
        percepite: List<Double>,
        mm: Double = 0.0,
        prob: Int = 0,
        raffiche: Double = 10.0,
        uv: Double = 1.0,
        codice: Int = 3,
    ) = percepite.mapIndexed { i, t ->
        HourForecast(
            time = mattino.plusHours(i.toLong()),
            temperature = t,
            apparent = t,
            weatherCode = codice,
            precipitation = mm,
            precipProbability = prob,
            windGusts = raffiche,
            uvIndex = uv,
        )
    }

    @Test
    fun `una mattina fredda che si scalda chiede la giacca a strati`() {
        val r = cosaMettersi(giornata(listOf(8.0, 10.0, 13.0, 16.0, 17.0, 15.0)), mattino)!!
        assertEquals("Giacca, a strati", r)
    }

    @Test
    fun `la pioggia aggiunge l'ombrello, il sole forte occhiali e crema`() {
        val r = cosaMettersi(giornata(listOf(26.0, 28.0, 30.0), mm = 1.2, prob = 70, uv = 8.0), mattino)!!
        assertEquals("Abiti leggeri · ombrello · occhiali e crema", r)
    }

    @Test
    fun `sotto zero e con neve, scarpe che non bagnano`() {
        val r = cosaMettersi(giornata(listOf(-3.0, -1.0), mm = 2.0, prob = 90, codice = 73, raffiche = 45.0), mattino)!!
        assertTrue(r, r.startsWith("Cappotto pesante"))
        assertTrue(r, r.contains("scarpe impermeabili"))
        assertTrue(r, r.contains("vento"))
    }

    @Test
    fun `l'umido dei modelli non porta l'ombrello`() {
        val r = cosaMettersi(giornata(listOf(15.0, 15.0), mm = 0.1, prob = 80), mattino)!!
        assertEquals("Felpa o giacca leggera", r)
    }

    @Test
    fun `la notte dopo le 23 non conta, e senza ore niente riga`() {
        val sera = LocalDateTime.of(2026, 11, 25, 22, 0)
        val ore = listOf(
            HourForecast(time = sera, apparent = 20.0),
            HourForecast(time = sera.plusHours(2), apparent = 2.0),
        )
        assertEquals("Maglietta e qualcosa sopra", cosaMettersi(ore, sera))
        assertNull(cosaMettersi(emptyList(), sera))
    }
}
