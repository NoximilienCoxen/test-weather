package com.caelumweather.notifiche

import com.caelumweather.data.HourForecast
import com.caelumweather.prefs.SalaWindUnit
import com.caelumweather.prefs.SogliePersonali
import com.caelumweather.prefs.TempUnit
import com.caelumweather.prefs.TipoSoglia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SogliePersonaliTest {

    private val data = LocalDate.of(2026, 1, 14)
    private val leQuindici = data.atTime(15, 20)

    /** Due giorni di ore: la temperatura e le raffiche le decide chi prova. */
    private fun ore(gradi: (giorno: Int, ora: Int) -> Double, raffica: (giorno: Int, ora: Int) -> Double = { _, _ -> 2.0 }) =
        (0..1).flatMap { g ->
            (0..23).map { h ->
                HourForecast(time = data.plusDays(g.toLong()).atTime(h, 0), temperature = gradi(g, h), windGusts = raffica(g, h))
            }
        }

    private val tutte = SogliePersonali(gelo = true, caldo = true, vento = true)

    @Test
    fun `spente non dicono niente, nemmeno sotto zero`() {
        val gelide = ore({ _, _ -> -8.0 })
        assertTrue(soglieSuperate(gelide, leQuindici, SogliePersonali()).isEmpty())
    }

    @Test
    fun `il gelo delle cinque si sa dal pomeriggio prima`() {
        // Sei gradi di giorno, due sotto zero alle cinque del mattino dopo.
        val notte = ore({ g, h -> if (g == 1 && h == 5) -2.3 else 6.0 })
        val superate = soglieSuperate(notte, leQuindici, SogliePersonali(gelo = true))
        assertEquals(1, superate.size)
        assertEquals(TipoSoglia.GELO, superate.single().tipo)
        assertEquals(data.plusDays(1).atTime(5, 0), superate.single().quando)
        assertEquals(-2.3, superate.single().valore, 1e-9)
    }

    @Test
    fun `fuori dalle sedici ore non si guarda`() {
        // Alle 8 del giorno dopo: diciassette ore dopo le 15.
        val lontano = ore({ g, h -> if (g == 1 && h == 8) -4.0 else 6.0 })
        assertTrue(soglieSuperate(lontano, leQuindici, SogliePersonali(gelo = true)).isEmpty())
    }

    @Test
    fun `un'ora gia' passata non conta`() {
        val stamattina = ore({ g, h -> if (g == 0 && h == 6) -4.0 else 6.0 })
        assertTrue(soglieSuperate(stamattina, leQuindici, SogliePersonali(gelo = true)).isEmpty())
    }

    @Test
    fun `si confronta il numero che si legge, arrotondato`() {
        // 0,4 si scrive "0°": con la soglia a zero l'avviso parte. 0,6 si scrive "1°".
        val quasi = ore({ g, h -> if (g == 1 && h == 5) 0.4 else 6.0 })
        assertEquals(1, soglieSuperate(quasi, leQuindici, SogliePersonali(gelo = true, geloGradi = 0)).size)
        val sopra = ore({ g, h -> if (g == 1 && h == 5) 0.6 else 6.0 })
        assertTrue(soglieSuperate(sopra, leQuindici, SogliePersonali(gelo = true, geloGradi = 0)).isEmpty())
    }

    @Test
    fun `caldo e vento prendono l'ora del picco`() {
        val mattina = data.atTime(8, 0)
        val estate = ore(
            gradi = { g, h -> if (g == 0) 22.0 + (12 - kotlin.math.abs(h - 15)) else 22.0 },
            raffica = { g, h -> if (g == 0 && h == 18) 19.0 else 3.0 },
        )
        val superate = soglieSuperate(estate, mattina, tutte.copy(gelo = false, caldoGradi = 33, ventoKmh = 60))
        assertEquals(listOf(TipoSoglia.CALDO, TipoSoglia.VENTO), superate.map { it.tipo })
        assertEquals(data.atTime(15, 0), superate[0].quando)
        assertEquals(34.0, superate[0].valore, 1e-9)
        // 19 m/s sono 68 km/h.
        assertEquals(data.atTime(18, 0), superate[1].quando)
    }

    @Test
    fun `sotto soglia il vento tace`() {
        // 16 m/s sono 58 km/h: sotto i 60.
        val brezza = ore({ _, _ -> 20.0 }, raffica = { _, _ -> 16.0 })
        assertTrue(soglieSuperate(brezza, leQuindici, SogliePersonali(vento = true, ventoKmh = 60)).isEmpty())
    }

    @Test
    fun `di notte non si parla`() {
        assertFalse(oraDiParlare(LocalTime.of(23, 10)))
        assertFalse(oraDiParlare(LocalTime.of(6, 59)))
        assertTrue(oraDiParlare(LocalTime.of(7, 0)))
        assertTrue(oraDiParlare(LocalTime.of(21, 59)))
        assertFalse(oraDiParlare(LocalTime.of(22, 0)))
    }

    @Test
    fun `il testo dice quanto, quando, la soglia, e che e' una previsione`() {
        val evento = SogliaSuperata(TipoSoglia.GELO, data.plusDays(1).atTime(5, 0), -2.3, 0)
        val (titolo, testo) = testiSoglia("Forlì", evento, leQuindici, TempUnit.CELSIUS, SalaWindUnit.KMH)
        assertEquals("Gelo a Forlì: minima di -2°", titolo)
        assertEquals("Prevista domani verso le 5. La tua soglia: 0°. È una previsione, non un'allerta ufficiale.", testo)
    }

    @Test
    fun `soglia e valore seguono le unita' scelte`() {
        val caldo = SogliaSuperata(TipoSoglia.CALDO, data.atTime(16, 0), 36.2, 33)
        val (titolo, testo) = testiSoglia("Forlì", caldo, leQuindici, TempUnit.FAHRENHEIT, SalaWindUnit.KMH)
        assertEquals("Caldo a Forlì: massima di 97°", titolo)
        assertTrue(testo, testo.startsWith("Prevista oggi verso le 16. La tua soglia: 91°."))

        val vento = SogliaSuperata(TipoSoglia.VENTO, data.atTime(18, 0), 19.0, 60)
        val (titoloVento, testoVento) = testiSoglia("Forlì", vento, leQuindici, TempUnit.CELSIUS, SalaWindUnit.KN)
        assertEquals("Vento forte a Forlì: raffiche a 37 nodi", titoloVento)
        assertTrue(testoVento, testoVento.startsWith("Previste oggi verso le 18. La tua soglia: 32 nodi."))
    }
}
