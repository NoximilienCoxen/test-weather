package com.caelumweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NeveTest {

    private val giorno = LocalDate.of(2026, 12, 3)

    private fun previsione(cm: Double?, zero: (Int) -> Double?, nevica: (Int) -> Double = { 0.0 }, quota: Double? = 1650.0) = Forecast(
        current = CurrentWeather(),
        days = listOf(DayForecast(giorno, "OGGI", snowfallSum = cm)),
        allHours = (0..23).map { h ->
            HourForecast(time = giorno.atTime(h, 0), snowfall = nevica(h), freezingLevel = zero(h))
        },
        elevation = quota,
    )

    @Test
    fun `la quota neve sta trecento metri sotto lo zero termico, a passi di cinquanta`() {
        assertEquals(1500, quotaNeve(1800.0))
        assertEquals(1500, quotaNeve(1790.0))
        assertEquals(1550, quotaNeve(1860.0))
        assertEquals(0, quotaNeve(200.0))
        assertNull(quotaNeve(null))
        assertNull(quotaNeve(Double.NaN))
    }

    @Test
    fun `un giorno asciutto non e' un giorno di neve`() {
        assertTrue(nevePrevista(previsione(0.0, { 2900.0 })).isEmpty())
        assertTrue(nevePrevista(previsione(0.05, { 2900.0 })).isEmpty())
        assertTrue(nevePrevista(previsione(null, { 2900.0 })).isEmpty())
    }

    @Test
    fun `la quota e' la piu' bassa fra le ore in cui nevica`() {
        // Zero termico a 2000 m di giorno e a 1400 la sera, quando nevica.
        val p = previsione(
            cm = 12.5,
            zero = { if (it >= 18) 1400.0 else 2000.0 },
            nevica = { if (it in 17..21) 2.5 else 0.0 },
        )
        val giorni = nevePrevista(p)
        assertEquals(1, giorni.size)
        assertEquals(12.5, giorni.single().cm, 0.0)
        // Alle 17 lo zero termico e' ancora 2000: la quota piu' bassa e' dalle 18, 1400 - 300.
        assertEquals(1100, giorni.single().quota)
    }

    @Test
    fun `senza zero termico la neve c'e' e la quota no`() {
        val giorni = nevePrevista(previsione(5.0, { null }, { if (it == 9) 1.0 else 0.0 }))
        assertEquals(5.0, giorni.single().cm, 0.0)
        assertNull(giorni.single().quota)
    }

    @Test
    fun `il posto rispetto alla quota`() {
        assertEquals(PostoSottoLaNeve.NEVICA_QUI, postoRispettoAllaNeve(1663.0, 1300))
        assertEquals(PostoSottoLaNeve.AL_LIMITE, postoRispettoAllaNeve(1663.0, 1550))
        assertEquals(PostoSottoLaNeve.AL_LIMITE, postoRispettoAllaNeve(1000.0, 1100))
        assertEquals(PostoSottoLaNeve.PIU_IN_ALTO, postoRispettoAllaNeve(200.0, 1400))
        assertNull(postoRispettoAllaNeve(null, 1400))
        assertNull(postoRispettoAllaNeve(1000.0, null))
    }

    @Test
    fun `la sezione c'e' con la neve o in quota, non in pianura asciutta`() {
        assertTrue(serveLaNeve(previsione(3.0, { 1500.0 }, quota = 50.0)))
        assertTrue(serveLaNeve(previsione(0.0, { 3000.0 }, quota = 1663.0)))
        assertEquals(false, serveLaNeve(previsione(0.0, { 3000.0 }, quota = 50.0)))
        assertEquals(false, serveLaNeve(null))
    }
}
