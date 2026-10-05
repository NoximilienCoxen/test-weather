package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Il lettore di Environment and Climate Change Canada, contro una risposta vera.
 *
 * `eccc-quebec.json` e' la cattura della sonda del 5 ottobre 2026: tre
 * "special weather statement" in Quebec, gia' conclusi (`status_en`
 * "ended") perche' quel giorno in Canada non ce n'erano di attivi.
 */
class EcccAlertsTest {

    private val quebec: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("eccc-quebec.json")).bufferedReader().readText()

    private val est = ZoneOffset.ofHours(-4)

    @Test
    fun `le allerte concluse non si mostrano`() {
        assertTrue(EcccAlerts.parse(quebec, est).isEmpty())
    }

    @Test
    fun `la stessa voce attiva si legge per intero`() {
        // La forma vera, con lo stato che avrebbe un'allerta in corso.
        val attive = EcccAlerts.parse(quebec.replace("\"status_en\":\"ended\"", "\"status_en\":\"active\""), est)
        assertEquals(1, attive.size) // tre zone, un avviso
        val a = attive.single()
        assertEquals(FonteAllerte.ECCC, a.fonte)
        assertEquals(AlertLevel.GIALLA, a.level) // statement, senza colore
        assertEquals(AlertKind.ALTRO, a.kind)
        assertTrue(a.description!!.startsWith("Special weather statement"))
        // validity 2026-10-06T10:00Z, event_end 2026-10-07T00:00Z, nell'ora del Quebec.
        assertEquals(LocalDateTime.of(2026, 10, 6, 6, 0), a.onset)
        assertEquals(LocalDateTime.of(2026, 10, 6, 20, 0), a.expires)
        assertTrue(a.areaDesc!!.endsWith("QC"))
    }

    @Test
    fun `il colore di rischio vince sul tipo`() {
        val rossa = EcccAllerta(alert_type = "statement", alert_name_en = "x", risk_colour_en = "red")
        val warning = EcccAllerta(alert_type = "warning", alert_name_en = "x")
        assertEquals(AlertLevel.ROSSA, rossa.level)
        assertEquals(AlertLevel.ARANCIONE, warning.level)
    }

    @Test
    fun `i nomi canadesi diventano fenomeni`() {
        assertEquals(AlertKind.NEVE_GHIACCIO, tipoDaEvento("freezing rain warning"))
        assertEquals(AlertKind.FREDDO, tipoDaEvento("arctic outflow warning"))
        assertEquals(AlertKind.FREDDO, tipoDaEvento("extreme cold warning"))
        assertEquals(AlertKind.PIOGGIA, tipoDaEvento("rainfall warning"))
        assertEquals(AlertKind.NEVE_GHIACCIO, tipoDaEvento("snowfall warning"))
        assertEquals(AlertKind.VENTO, tipoDaEvento("squall warning"))
    }

    @Test
    fun `il Canada va a ECCC`() {
        assertEquals(FonteAllerte.ECCC, WeatherAlertsRepository.fonteDi("Canada"))
    }
}
