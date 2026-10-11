package io.github.noximiliencoxen.caelum.wear

import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class FormatiTest {

    private val base = LocalDateTime.of(2026, 10, 11, 0, 0)

    private fun previsione(vararg ore: HourForecast) =
        Forecast(current = CurrentWeather(), days = emptyList(), allHours = ore.toList())

    private fun ora(h: Int, nuvole: Int? = null) = HourForecast(time = base.plusHours(h.toLong()), cloudCover = nuvole)

    @Test
    fun `la temperatura si arrotonda e porta il grado`() {
        assertEquals("21°", temperatura(20.6))
        assertEquals("-3°", temperatura(-3.2))
        assertEquals("0°", temperatura(0.2))
    }

    @Test
    fun `senza dato la temperatura e' un trattino`() {
        assertEquals("–", temperatura(null))
    }

    @Test
    fun `l'ora ha sempre due cifre`() {
        assertEquals("07", oraBreve(base.withHour(7)))
        assertEquals("15", oraBreve(base.withHour(15)))
    }

    @Test
    fun `la pioggia si dice dal dieci per cento`() {
        assertNull(probabilitaPioggia(null))
        assertNull(probabilitaPioggia(9))
        assertEquals("10%", probabilitaPioggia(10))
        assertEquals("80%", probabilitaPioggia(80))
    }

    @Test
    fun `le ore partono da quella in corso`() {
        val p = previsione(*(0..23).map { ora(it) }.toTypedArray())
        val ore = oreDaAdesso(p, base.withHour(15).withMinute(40), quante = 3)
        assertEquals(listOf(15, 16, 17), ore.map { it.time.hour })
    }

    @Test
    fun `a fine giornata le ore finiscono senza inventarne`() {
        val p = previsione(*(0..23).map { ora(it) }.toTypedArray())
        assertEquals(2, oreDaAdesso(p, base.withHour(22), quante = 12).size)
    }

    @Test
    fun `la nuvolosita' e' quella dell'ora in corso`() {
        val p = previsione(ora(14, nuvole = 10), ora(15, nuvole = 80))
        assertEquals(80, nuvolositaAdesso(p, base.withHour(15).withMinute(59)))
        assertNull(nuvolositaAdesso(p, base.withHour(3)))
    }
}
