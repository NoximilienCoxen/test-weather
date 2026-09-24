package io.github.noximiliencoxen.caelum.widget

import io.github.noximiliencoxen.caelum.data.CurrentWeather
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * La previsione conservata, riletta piu' tardi: vedi [agedTo].
 *
 * E' quella che il widget mostra quando la rete non c'e', e l'unico modo in
 * cui puo' sbagliare in silenzio e' mostrare il passato come presente.
 */
class AgedForecastTest {

    private val start = LocalDate.of(2026, 9, 20)

    private val saved = Forecast(
        current = CurrentWeather(temperature = 10.0, weatherCode = 0),
        days = (0 until 8).map { DayForecast(start.plusDays(it.toLong()), "G$it", tempMax = 20.0 + it) },
        allHours = (0 until 8 * 24).map { h ->
            HourForecast(start.atStartOfDay().plusHours(h.toLong()), temperature = h.toDouble(), weatherCode = h % 4)
        },
    )

    @Test
    fun `i giorni passati escono`() {
        val aged = saved.agedTo(LocalDateTime.of(2026, 9, 23, 8, 30))
        assertEquals(LocalDate.of(2026, 9, 23), aged.days.first().date)
        assertEquals(5, aged.days.size)
    }

    @Test
    fun `adesso e' l'ora della previsione che cade adesso`() {
        val now = LocalDateTime.of(2026, 9, 22, 15, 40)
        val aged = saved.agedTo(now)
        val expected = (2 * 24 + 15).toDouble()
        assertEquals(expected, aged.current.temperature)
        assertEquals((2 * 24 + 15) % 4, aged.current.weatherCode)
    }

    @Test
    fun `le ore partono da adesso e la striscia resta di un giorno`() {
        val aged = saved.agedTo(LocalDateTime.of(2026, 9, 22, 15, 40))
        assertEquals(LocalDateTime.of(2026, 9, 22, 15, 0), aged.hours.first().time)
        assertEquals(24, aged.hours.size)
    }

    @Test
    fun `oltre la fine della previsione adesso resta com'era`() {
        val aged = saved.agedTo(LocalDateTime.of(2026, 10, 5, 12, 0))
        assertEquals(10.0, aged.current.temperature)
        assertEquals(0, aged.days.size)
    }
}
