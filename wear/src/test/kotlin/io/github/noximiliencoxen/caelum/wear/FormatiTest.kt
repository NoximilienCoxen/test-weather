package io.github.noximiliencoxen.caelum.wear

import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.lingua.Lingue
import io.github.noximiliencoxen.caelum.lingua.SceltaLingua
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
    fun `in Fahrenheit la temperatura si converte`() {
        assertEquals("32°", temperatura(0.0, fahrenheit = true))
        assertEquals("70°", temperatura(21.0, fahrenheit = true))
        assertEquals("-4°", temperatura(-20.0, fahrenheit = true))
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

    @Test
    fun `la condizione si scrive in frase`() {
        Lingue.impostaScelta(SceltaLingua.ITALIANO)
        assertEquals("Parz. nuvoloso", condizioneInFrase(2))
        assertEquals("Sereno", condizioneInFrase(0))
    }

    private fun bagnata(h: Int, mm: Double? = null, prob: Int? = null, codice: Int? = null) =
        HourForecast(time = base.plusHours(h.toLong()), precipitation = mm, precipProbability = prob, weatherCode = codice)

    @Test
    fun `un decimo di millimetro o meta' delle probabilita' fanno pioggia`() {
        assertEquals(false, bagnata(bagnata(0, mm = 0.05, prob = 40)))
        assertEquals(true, bagnata(bagnata(0, mm = 0.1)))
        assertEquals(true, bagnata(bagnata(0, prob = 50)))
    }

    @Test
    fun `pioggia che arriva, con la sua ora e la probabilita'`() {
        val ore = listOf(bagnata(14), bagnata(15, mm = 0.0, prob = 20), bagnata(16, mm = 1.2, prob = 70, codice = 61))
        assertEquals(PioggiaInVista.Arriva(base.withHour(16), 70, neve = false), pioggiaInVista(ore))
    }

    @Test
    fun `pioggia in corso, con l'ora in cui smette`() {
        val ore = listOf(bagnata(14, mm = 2.0), bagnata(15, mm = 0.4), bagnata(16), bagnata(17, mm = 3.0))
        assertEquals(PioggiaInVista.Adesso(base.withHour(16), neve = false), pioggiaInVista(ore))
    }

    @Test
    fun `pioggia che non smette nelle ore guardate`() {
        val ore = listOf(bagnata(14, mm = 2.0), bagnata(15, mm = 0.4))
        assertEquals(PioggiaInVista.Adesso(null, neve = false), pioggiaInVista(ore))
    }

    @Test
    fun `la neve si chiama neve`() {
        val ore = listOf(bagnata(14), bagnata(15, mm = 0.8, codice = 73))
        assertEquals(PioggiaInVista.Arriva(base.withHour(15), null, neve = true), pioggiaInVista(ore))
    }

    @Test
    fun `asciutto conta le ore guardate`() {
        assertEquals(PioggiaInVista.Asciutto(3), pioggiaInVista(listOf(bagnata(1), bagnata(2), bagnata(3))))
    }

    @Test
    fun `le righe della pioggia`() {
        Lingue.impostaScelta(SceltaLingua.ITALIANO)
        assertEquals("Pioggia alle 16 · 70%", testoPioggia(PioggiaInVista.Arriva(base.withHour(16), 70, neve = false)))
        assertEquals("Pioggia alle 16", testoPioggia(PioggiaInVista.Arriva(base.withHour(16), 5, neve = false)))
        assertEquals("Piove fino alle 09", testoPioggia(PioggiaInVista.Adesso(base.withHour(9), neve = false)))
        assertEquals("Neve per ore", testoPioggia(PioggiaInVista.Adesso(null, neve = true)))
        assertEquals("Asciutto per 12 ore", testoPioggia(PioggiaInVista.Asciutto(12)))
    }

    @Test
    fun `gli estremi della settimana`() {
        val giorni = listOf(
            DayForecast(date = base.toLocalDate(), label = "OGGI", tempMin = 8.0, tempMax = 19.0),
            DayForecast(date = base.toLocalDate().plusDays(1), label = "LUN", tempMin = 4.0, tempMax = 22.0),
            DayForecast(date = base.toLocalDate().plusDays(2), label = "MAR"),
        )
        assertEquals(4.0..22.0, estremiSettimana(giorni))
        assertNull(estremiSettimana(emptyList()))
    }
}
