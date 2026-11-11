package com.caelumweather.data

import com.caelumweather.lingua.Lingua
import com.caelumweather.lingua.Lingue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ieri si chiede insieme alla settimana (`past_days=1`) e si stacca subito:
 * la settimana deve continuare a cominciare da oggi, perche' tutto il resto
 * dell'app conta gli indici da li'.
 */
class IeriTest {

    @Before fun italiano() { Lingue.forzata = Lingua.ITALIANO }
    @After fun comePrima() { Lingue.forzata = null }

    private fun risposta(primoGiorno: LocalDate, giorni: Int, adesso: String, massime: List<Double>): String {
        val date = (0 until giorni).map { primoGiorno.plusDays(it.toLong()) }
        val ore = date.flatMap { d -> (0..23).map { h -> d.atTime(h, 0) } }
        return """
            {"utc_offset_seconds":3600,
             "current":{"time":"$adesso","temperature_2m":12.0},
             "daily":{"time":[${date.joinToString(",") { "\"$it\"" }}],
                      "temperature_2m_max":[${massime.joinToString(",")}]},
             "hourly":{"time":[${ore.joinToString(",") { "\"$it\"" }}],
                       "temperature_2m":[${ore.joinToString(",") { "5.0" }}]}}
        """.trimIndent()
    }

    @Test
    fun `ieri si stacca, e la settimana comincia da oggi`() {
        val oggi = LocalDate.of(2026, 11, 10)
        val previsione = WeatherRepository().parse(
            risposta(oggi.minusDays(1), 3, "2026-11-10T10:15", listOf(9.4, 12.6, 13.0)),
        )
        assertEquals(oggi, previsione.days.first().date)
        assertEquals(2, previsione.days.size)
        assertEquals(9.4, previsione.ieri?.tempMax)
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), previsione.hours.first().time)
        assertEquals(24, previsione.hours.size)
        assertEquals(48, previsione.allHours.size)
    }

    @Test
    fun `una scorta di prima, senza ieri, passa com'e'`() {
        val oggi = LocalDate.of(2026, 11, 10)
        val previsione = WeatherRepository().parse(
            risposta(oggi, 2, "2026-11-10T10:15", listOf(12.6, 13.0)),
        )
        assertEquals(oggi, previsione.days.first().date)
        assertEquals(2, previsione.days.size)
        assertNull(previsione.ieri)
        assertEquals(48, previsione.allHours.size)
    }

    @Test
    fun `lo scarto si fa sui numeri che si leggono`() {
        val oggi = DayForecast(LocalDate.of(2026, 11, 10), "OGGI", tempMax = 19.5)
        val ieri = DayForecast(LocalDate.of(2026, 11, 9), "LUN", tempMax = 17.4)
        // 20 contro 17: tre, anche se i decimali direbbero 2,1.
        assertEquals(3, scartoDaIeri(oggi, ieri) { it })
        assertNull(scartoDaIeri(oggi, null) { it })
        assertNull(scartoDaIeri(oggi.copy(tempMax = null), ieri) { it })
    }

    @Test
    fun `le parole`() {
        assertEquals("come ieri", scartoDaIeriBreve(0))
        assertEquals("+3° su ieri", scartoDaIeriBreve(3))
        assertEquals("−4° su ieri", scartoDaIeriBreve(-4))
        assertNull(scartoDaIeriEsteso(1))
        assertNull(scartoDaIeriEsteso(-1))
        assertEquals("3° più caldo di ieri.", scartoDaIeriEsteso(3))
        assertEquals("5° più freddo di ieri.", scartoDaIeriEsteso(-5))
    }
}
