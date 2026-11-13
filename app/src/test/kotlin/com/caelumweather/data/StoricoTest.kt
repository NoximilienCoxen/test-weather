package com.caelumweather.data

import com.caelumweather.prefs.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Contro un esempio **fatto a mano** nel formato documentato dell'archivio: dal
 * container in cui e' stato scritto `archive-api.open-meteo.com` era bloccato,
 * quindi manca una risposta vera. La prova su telefono dira' se combacia.
 */
class StoricoTest {

    private fun risorsa(nome: String) =
        checkNotNull(javaClass.classLoader?.getResource(nome)) { "manca $nome" }.readText()

    @Test
    fun `un giorno dell'archivio si legge`() {
        val g = parseStorico(risorsa("archivio-un-giorno.json"))
        assertNotNull(g)
        assertEquals(LocalDate.of(2025, 11, 11), g!!.data)
        assertEquals(16.4, g.tempMax!!, 1e-9)
        assertEquals(7.9, g.tempMin!!, 1e-9)
        assertEquals(3.2, g.pioggia!!, 1e-9)
        assertEquals(61, g.codice)
    }

    @Test
    fun `senza temperature non c'e' niente da dire`() {
        assertNull(parseStorico("""{"daily":{"time":["2025-11-11"],"temperature_2m_max":[null],"temperature_2m_min":[null]}}"""))
        assertNull(parseStorico("""{"latitude":1.0}"""))
    }

    @Test(expected = IllegalStateException::class)
    fun `un errore dichiarato solleva`() {
        parseStorico("""{"error":true,"reason":"Date out of range"}""")
    }

    @Test
    fun `il 29 febbraio ha un anno prima il 28`() {
        assertEquals(LocalDate.of(2023, 2, 28), unAnnoPrima(LocalDate.of(2024, 2, 29)))
        assertEquals(LocalDate.of(2025, 11, 11), unAnnoPrima(LocalDate.of(2026, 11, 11)))
    }

    private val ieri = GiornoStorico(LocalDate.of(2025, 11, 11), tempMax = 16.4, tempMin = 7.9, pioggia = 3.2, codice = 61)

    private fun oggi(max: Double) = DayForecast(LocalDate.of(2026, 11, 11), "OGGI", tempMax = max)

    @Test
    fun `le righe dicono cosa c'era e di quanto oggi e' diverso`() {
        val r = righeUnAnnoFa(ieri, oggi(19.6), TempUnit.CELSIUS)
        assertTrue(r.cosaEra, "16° / 8°" in r.cosaEra && "3,2 mm" in r.cosaEra)
        // 20 - 16 = 4, sui numeri mostrati.
        assertEquals("Oggi 4° in più sulla massima.", r.confronto)
        assertEquals("Oggi 3° in meno sulla massima.", righeUnAnnoFa(ieri, oggi(13.4), TempUnit.CELSIUS).confronto)
    }

    @Test
    fun `sotto i due gradi e' come allora, e senza la massima di oggi non si confronta`() {
        assertEquals("Oggi quasi come allora.", righeUnAnnoFa(ieri, oggi(17.0), TempUnit.CELSIUS).confronto)
        assertNull(righeUnAnnoFa(ieri, null, TempUnit.CELSIUS).confronto)
    }

    @Test
    fun `un giorno asciutto non scrive pioggia`() {
        val secco = ieri.copy(pioggia = 0.0, codice = 0)
        assertTrue("mm" !in righeUnAnnoFa(secco, null, TempUnit.CELSIUS).cosaEra)
    }
}
