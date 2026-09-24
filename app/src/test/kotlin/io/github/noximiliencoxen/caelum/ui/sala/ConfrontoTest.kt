package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ConfrontoTest {

    private fun previsione(max: Double?, mm: Double?) = Forecast(
        current = CurrentWeather(),
        days = listOf(DayForecast(LocalDate.of(2026, 9, 24), "OGGI", tempMax = max, precipitationSum = mm)),
    )

    private val massima = RigaConfronto("MASSIMA", { it.days.first().tempMax }, { "$it" }, Meglio.ALTO)
    private val pioggia = RigaConfronto("PIOGGIA", { it.days.first().precipitationSum }, { "$it" }, Meglio.BASSO)

    @Test
    fun `vince il piu' caldo e il piu' asciutto`() {
        val p = listOf(previsione(22.0, 3.0), previsione(26.0, 0.0), previsione(24.0, 1.0))
        assertEquals(1, migliore(massima, p))
        assertEquals(1, migliore(pioggia, p))
    }

    @Test
    fun `un pareggio o un dato solo non vincono`() {
        assertNull(migliore(massima, listOf(previsione(22.0, 0.0), previsione(22.0, 0.0))))
        assertNull(migliore(massima, listOf(previsione(22.0, 0.0), null)))
    }

    @Test
    fun `la citta' guardata entra nel confronto anche se non e' salvata`() {
        val a = Place("Noceto", latitude = 44.8, longitude = 10.2)
        val b = Place("Parma", latitude = 44.8, longitude = 10.3)
        assertEquals(listOf(a, b), postiDaConfrontare(UiState(place = a, favorites = listOf(b))))
        assertEquals(listOf(a, b), postiDaConfrontare(UiState(place = b, favorites = listOf(a, b))))
    }
}
