package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.data.AlertKind
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * La pastiglia con un'allerta gia' emessa ma non ancora cominciata.
 *
 * Il caso vero: il 4 ottobre 2026 MeteoAlarm aveva un'allerta gialla per
 * temporali sull'Emilia-Romagna dalle 14 del 6, e la pastiglia diceva
 * "nessun avviso".
 */
class AllertaInArrivoTest {

    private val adesso = LocalDateTime.of(2026, 10, 4, 23, 0)

    private fun avviso(
        inizio: LocalDateTime?,
        livello: AlertLevel = AlertLevel.GIALLA,
        ufficiale: Boolean = true,
        id: String = "t",
    ) = WeatherAlert(
        id = id, level = livello, kind = AlertKind.TEMPORALI, headline = "t",
        onset = inizio, expires = inizio?.plusHours(10), source = "t", official = ufficiale,
    )

    @Test
    fun `l'allerta di dopodomani si annuncia`() {
        val emilia = avviso(LocalDateTime.of(2026, 10, 6, 14, 0))
        assertEquals(emilia, listOf(emilia).prossimaUfficiale(adesso))
        assertEquals("MAR", quandoInArrivo(emilia, adesso))
    }

    @Test
    fun `domani e oggi si dicono per nome`() {
        assertEquals("DOMANI", quandoInArrivo(avviso(LocalDateTime.of(2026, 10, 5, 20, 0)), adesso))
        assertEquals("DALLE 14", quandoInArrivo(avviso(LocalDateTime.of(2026, 10, 4, 14, 0)), adesso.withHour(9)))
        assertEquals("DALLE 14:30", quandoInArrivo(avviso(LocalDateTime.of(2026, 10, 4, 14, 30)), adesso.withHour(9)))
    }

    @Test
    fun `vince la prima, e a parita' la piu' grave`() {
        val gialla = avviso(LocalDateTime.of(2026, 10, 6, 0, 0), id = "g")
        val arancione = avviso(LocalDateTime.of(2026, 10, 6, 0, 0), AlertLevel.ARANCIONE, id = "a")
        val dopo = avviso(LocalDateTime.of(2026, 10, 5, 18, 0), AlertLevel.ROSSA, id = "r")
        assertEquals(dopo, listOf(gialla, arancione, dopo).prossimaUfficiale(adesso))
        assertEquals(arancione, listOf(gialla, arancione).prossimaUfficiale(adesso))
    }

    @Test
    fun `gli avvisi calcolati e quelli gia' cominciati non si annunciano`() {
        val calcolato = avviso(LocalDateTime.of(2026, 10, 6, 0, 0), ufficiale = false)
        val cominciato = avviso(adesso.minusHours(1))
        val senzaInizio = avviso(null)
        assertNull(listOf(calcolato, cominciato, senzaInizio).prossimaUfficiale(adesso))
    }
}
