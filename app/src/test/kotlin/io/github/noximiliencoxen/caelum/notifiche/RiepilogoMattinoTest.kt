package io.github.noximiliencoxen.caelum.notifiche

import io.github.noximiliencoxen.caelum.data.AlertKind
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RiepilogoMattinoTest {

    private val data = LocalDate.of(2026, 10, 12)
    private val sette = data.atTime(7, 0)
    private val sereno = DayForecast(date = data, label = "OGGI", weatherCode = 0, tempMax = 21.4, tempMin = 11.6, uvMax = 1.0)

    private fun ore(pioggiaDalle: Int? = null, uvPicco: Int? = null) = (0..23).map { h ->
        HourForecast(
            time = data.atTime(h, 0),
            precipitation = if (pioggiaDalle != null && h >= pioggiaDalle) 0.8 else 0.0,
            uvIndex = if (h == uvPicco) 7.0 else 1.0,
        )
    }

    private fun testi(giorno: DayForecast, ore: List<HourForecast> = ore(), allerte: List<WeatherAlert> = emptyList()) =
        testiRiepilogo("Forlì", giorno, ore, sette, TempUnit.CELSIUS, SalaWindUnit.KMH, allerte)

    @Test
    fun `una giornata serena dice poco`() {
        val (titolo, testo) = testi(sereno)
        assertEquals("Forlì, oggi: 12° – 21°, sereno", titolo)
        assertEquals("Niente pioggia.", testo)
    }

    @Test
    fun `condiviso da un altro giorno, il titolo dice quale`() {
        val (titolo, _) = testiRiepilogo(
            "Forlì", sereno, ore(), data.atStartOfDay(), TempUnit.CELSIUS, SalaWindUnit.KMH, emptyList(),
            giornoDetto = "sabato 17 ott",
        )
        assertEquals("Forlì, sabato 17 ott: 12° – 21°, sereno", titolo)
    }

    @Test
    fun `la pioggia dice da quando e quanta`() {
        val (_, testo) = testi(sereno.copy(precipitationSum = 3.2, rainSum = 3.2), ore(pioggiaDalle = 15))
        assertTrue(testo, testo.contains("Pioggia dalle 15 circa, 3,2 mm"))
    }

    @Test
    fun `UV alto e raffiche, solo quando ci sono`() {
        val (_, testo) = testi(sereno.copy(uvMax = 7.2, gustMax = 16.0), ore(uvPicco = 13))
        assertTrue(testo, testo.contains("UV alto, 7 verso le 13"))
        assertTrue(testo, testo.contains("Raffiche fino a 58 km/h"))
        val (_, calmo) = testi(sereno.copy(gustMax = 8.0))
        assertFalse(calmo, calmo.contains("Raffiche"))
    }

    @Test
    fun `un'allerta finita ieri non si nomina`() {
        val vecchia = allerta(AlertLevel.ARANCIONE, scade = sette.minusHours(5))
        val (_, testo) = testi(sereno, allerte = listOf(vecchia))
        assertFalse(testo, testo.contains("Allerta"))
        val (_, oggi) = testi(sereno, allerte = listOf(allerta(AlertLevel.ARANCIONE, scade = sette.plusHours(10))))
        assertTrue(oggi, oggi.contains("Allerta arancione per vento"))
    }

    @Test
    fun `il prossimo turno e' all'ora esatta`() {
        val sei = LocalTime.of(7, 0)
        assertEquals(Duration.ofMinutes(30), ritardoFinoA(LocalDateTime.of(2026, 10, 12, 6, 30), sei))
        assertEquals(Duration.ofHours(23), ritardoFinoA(LocalDateTime.of(2026, 10, 12, 8, 0), sei))
        assertEquals(Duration.ofHours(24), ritardoFinoA(sette, sei))
    }

    @Test
    fun `a mezzogiorno il buongiorno non parte`() {
        val ora = LocalTime.of(7, 0)
        assertTrue(inTempo(sette.plusMinutes(5), ora))
        assertTrue(inTempo(sette.minusMinutes(20), ora))
        assertFalse(inTempo(sette.plusHours(5), ora))
    }

    private fun allerta(livello: AlertLevel, scade: LocalDateTime) = WeatherAlert(
        id = "x",
        level = livello,
        kind = AlertKind.VENTO,
        headline = "Vento",
        expires = scade,
        source = "prova",
        official = true,
    )
}
