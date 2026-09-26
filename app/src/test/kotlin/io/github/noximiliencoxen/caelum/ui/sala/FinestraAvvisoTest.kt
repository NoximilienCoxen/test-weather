package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.data.AlertKind
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Il "da quando a quando" del bollettino, detto come lo si direbbe. */
class FinestraAvvisoTest {

    private val oggi = LocalDate.of(2026, 9, 26)

    private fun avviso(inizio: LocalDateTime?, fine: LocalDateTime?) = WeatherAlert(
        id = "t", level = AlertLevel.GIALLA, kind = AlertKind.VENTO, headline = "t",
        onset = inizio, expires = fine, source = "t", official = true,
    )

    @Test
    fun `nello stesso giorno si dice il giorno una volta sola`() {
        val a = avviso(oggi.atTime(10, 0), oggi.atTime(17, 59))
        assertEquals("Oggi, dalle 10:00 alle 17:59", finestra(a, oggi))
    }

    @Test
    fun `a cavallo della mezzanotte si dicono i due giorni`() {
        val a = avviso(oggi.atTime(10, 0), oggi.plusDays(1).atTime(1, 59))
        assertEquals("Dalle 10:00 di oggi alle 01:59 di domani", finestra(a, oggi))
    }

    @Test
    fun `un giorno intero si chiama giornata`() {
        // E' la forma degli avvisi calcolati: dalla mezzanotte a LocalTime.MAX.
        val domani = oggi.plusDays(1)
        val a = avviso(domani.atStartOfDay(), domani.atTime(LocalTime.MAX))
        assertEquals("Domani, per tutta la giornata", finestra(a, oggi))
    }

    @Test
    fun `senza date lo si ammette`() {
        assertEquals("La fonte non dice fino a quando vale", finestra(avviso(null, null), oggi))
    }
}
