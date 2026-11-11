package com.caelumweather.data

import com.caelumweather.prefs.TempUnit
import com.caelumweather.ui.sala.rooms.rigaAcqua
import com.caelumweather.ui.sala.rooms.rigaOnde
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Contro due risposte vere dell'API del mare, lette l'11 novembre 2026. */
class MareTest {

    private fun risorsa(nome: String) =
        checkNotNull(javaClass.classLoader?.getResource(nome)) { "manca $nome" }.readText()

    @Test
    fun `a Rimini il mare c'e', ora per ora`() {
        val mare = parseMare(risorsa("mare-rimini.json"))
        assertNotNull(mare)
        assertEquals(48, mare!!.ore.size)
        val alleQuindici = mare.alle(LocalDateTime.of(2026, 11, 11, 15, 0))
        assertNotNull(alleQuindici)
        assertEquals(0.24, alleQuindici!!.onda!!, 1e-9)
        assertEquals(112.0, alleQuindici.direzione!!, 1e-9)
        assertEquals(16.1, alleQuindici.acqua!!, 1e-9)
    }

    @Test
    fun `a Forli' l'API risponde ma il mare non c'e'`() {
        assertNull(parseMare(risorsa("mare-forli.json")))
    }

    @Test
    fun `un'ora che la previsione ha e il mare no non si inventa`() {
        val mare = parseMare(risorsa("mare-rimini.json"))!!
        assertNull(mare.alle(LocalDateTime.of(2026, 12, 1, 15, 0)))
        assertNull(mare.alle(null))
    }

    @Test(expected = IllegalStateException::class)
    fun `un errore dichiarato non e' 'qui non c'e' mare'`() {
        parseMare("""{"error":true,"reason":"Latitude must be in range of -90 to 90°."}""")
    }

    @Test
    fun `la scala Douglas ai suoi limiti`() {
        assertEquals(StatoDelMare.CALMO, StatoDelMare.di(0.0))
        assertEquals(StatoDelMare.QUASI_CALMO, StatoDelMare.di(0.10))
        assertEquals(StatoDelMare.POCO_MOSSO, StatoDelMare.di(0.24))
        assertEquals(StatoDelMare.POCO_MOSSO, StatoDelMare.di(0.50))
        assertEquals(StatoDelMare.MOSSO, StatoDelMare.di(0.51))
        assertEquals(StatoDelMare.MOLTO_MOSSO, StatoDelMare.di(2.5))
        assertEquals(StatoDelMare.AGITATO, StatoDelMare.di(3.9))
        assertEquals(StatoDelMare.TEMPESTOSO, StatoDelMare.di(15.0))
        assertNull(StatoDelMare.di(null))
        assertNull(StatoDelMare.di(-1.0))
        assertNull(StatoDelMare.di(Double.NaN))
    }

    @Test
    fun `le due righe della sala`() {
        val ora = OraMare(LocalDateTime.of(2026, 11, 11, 15, 0), onda = 0.24, direzione = 112.0, periodo = 4.45, acqua = 16.1)
        assertEquals("Poco mosso · onde di 0,2 m da E", rigaOnde(ora, TempUnit.CELSIUS))
        assertEquals("acqua 16° · un'onda ogni 4 s", rigaAcqua(ora, TempUnit.CELSIUS))
        assertEquals("Poco mosso · onde di 0,8 ft da E", rigaOnde(ora, TempUnit.FAHRENHEIT))
        assertEquals("acqua 61° · un'onda ogni 4 s", rigaAcqua(ora, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `senza numeri lo si dice`() {
        val vuota = OraMare(LocalDateTime.of(2026, 11, 11, 15, 0))
        assertEquals("Onde non disponibili per quest'ora", rigaOnde(vuota, TempUnit.CELSIUS))
        assertEquals("temperatura dell'acqua non disponibile", rigaAcqua(vuota, TempUnit.CELSIUS))
    }
}
