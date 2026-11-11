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

    // ── Le maree, contro una risposta vera di Venezia dell'11 novembre 2026 ──

    @Test
    fun `a Venezia le alte e le basse si alternano e hanno un'ora fra le ore`() {
        val mare = parseMare(risorsa("mare-venezia-livello.json"))!!
        val estremi = estremiDiMarea(mare.ore)
        // Tre giorni di marea semidiurna: una decina di estremi.
        assert(estremi.size in 8..11) { "estremi: ${estremi.size}" }
        estremi.zipWithNext().forEach { (a, b) ->
            assertEquals("si alternano", true, a.tipo != b.tipo)
            // Sei ore e un quarto, in media, fra un'alta e la bassa dopo.
            val ore = java.time.Duration.between(a.quando, b.quando).toMinutes() / 60.0
            assert(ore in 4.0..8.5) { "distanza $ore h" }
        }
        // La prima bassa dell'11 e' fra le 03 e le 04 (-0,47 alle 3 e alle 4).
        val prima = estremi.first()
        assertEquals(TipoMarea.BASSA, prima.tipo)
        assertEquals(3, prima.quando.hour)
        assertEquals(-0.47, prima.metri, 0.02)
    }

    @Test
    fun `una curva piatta non ha maree`() {
        val inizio = LocalDateTime.of(2026, 11, 11, 0, 0)
        val piatta = (0 until 24).map { OraMare(inizio.plusHours(it.toLong()), livello = 0.02 + (it % 2) * 0.01) }
        assertEquals(emptyList<EstremoMarea>(), estremiDiMarea(piatta))
    }

    @Test
    fun `un buco nei dati non si interpola`() {
        val inizio = LocalDateTime.of(2026, 11, 11, 0, 0)
        // Alta a mezzogiorno... ma l'ora di mezzogiorno non ha livello.
        val buco = (0 until 25).map { h ->
            OraMare(inizio.plusHours(h.toLong()), livello = if (h == 12) null else 0.4 * kotlin.math.cos((h - 12) / 12.4 * 2 * Math.PI))
        }
        assertEquals(emptyList<EstremoMarea>(), estremiDiMarea(buco).filter { it.quando.hour in 11..13 })
    }

    @Test
    fun `i prossimi estremi partono dall'ora scelta`() {
        val mare = parseMare(risorsa("mare-venezia-livello.json"))!!
        val dalle10 = prossimiEstremi(mare, LocalDateTime.of(2026, 11, 11, 10, 0), quanti = 3)
        assertEquals(3, dalle10.size)
        assert(dalle10.all { !it.quando.isBefore(LocalDateTime.of(2026, 11, 11, 10, 0)) })
        assertEquals(TipoMarea.BASSA, dalle10.first().tipo)
    }

    @Test
    fun `la riga di un estremo dice tipo, ora e livello`() {
        val da = LocalDateTime.of(2026, 11, 11, 10, 0)
        val riga = com.caelumweather.ui.sala.rooms.rigaEstremo(
            EstremoMarea(TipoMarea.BASSA, LocalDateTime.of(2026, 11, 12, 4, 5), -0.43), da, TempUnit.CELSIUS,
        )
        assert("04:05" in riga && "0,43" in riga && "domani" in riga) { riga }
    }
}
