package io.github.noximiliencoxen.caelum.ui.sala.rooms.uv

import io.github.noximiliencoxen.caelum.data.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class LuceDelleFotoTest {

    // Forli', ora legale: UTC+2.
    private val forli = Triple(44.2226, 12.0407, 7200)

    private fun luce(data: LocalDate, lat: Double = forli.first, lon: Double = forli.second, scarto: Int = forli.third) =
        luceDelGiorno(data, lat, lon, scarto)

    @Test
    fun `a Forli' in ottobre l'oro della sera abbraccia il tramonto`() {
        // Il tramonto dell'11 ottobre a Forli' e' verso le 18:40.
        val l = luce(LocalDate.of(2026, 10, 11))
        val sera = l.oroSera!!
        assertTrue("${sera.inizio}", sera.inizio.toLocalTime().isAfter(LocalTime.of(17, 40)))
        assertTrue("${sera.fine}", sera.fine.toLocalTime().isBefore(LocalTime.of(19, 15)))
        assertTrue(sera.inizio.toLocalTime().isBefore(LocalTime.of(18, 40)))
        assertTrue(sera.fine.toLocalTime().isAfter(LocalTime.of(18, 40)))
    }

    @Test
    fun `al mattino prima la blu, la sera prima l'oro`() {
        val l = luce(LocalDate.of(2026, 10, 11))
        assertEquals(l.bluMattina!!.fine, l.oroMattina!!.inizio)
        assertEquals(l.oroSera!!.fine, l.bluSera!!.inizio)
    }

    @Test
    fun `l'ora d'oro dura meno di un'ora e mezza alle nostre latitudini`() {
        val l = luce(LocalDate.of(2026, 3, 20), scarto = 3600)
        val durata = Duration.between(l.oroMattina!!.inizio, l.oroMattina!!.fine)
        assertTrue("$durata", durata in Duration.ofMinutes(40)..Duration.ofMinutes(90))
    }

    @Test
    fun `a Tromso d'estate il sole non scende, niente ora blu`() {
        val l = luce(LocalDate.of(2026, 6, 21), lat = 69.65, lon = 18.96, scarto = 7200)
        assertNull(l.bluSera)
        assertNull(l.bluMattina)
    }

    @Test
    fun `il cielo si legge dalle ore toccate`() {
        val data = LocalDate.of(2026, 10, 11)
        val fascia = Fascia(data.atTime(18, 10), data.atTime(19, 5))
        fun ore(copertura: Int) = (0..23).map { HourForecast(time = data.atTime(it, 0), cloudCover = copertura) }
        assertEquals(CieloDellaLuce.LIBERO, cieloDellaLuce(fascia, ore(10)))
        assertEquals(CieloDellaLuce.SPARSO, cieloDellaLuce(fascia, ore(50)))
        assertEquals(CieloDellaLuce.COPERTO, cieloDellaLuce(fascia, ore(95)))
        assertNull(cieloDellaLuce(null, ore(10)))
        assertNotNull(cieloDellaLuce(fascia, ore(0)))
    }
}
