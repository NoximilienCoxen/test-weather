package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Il lettore del National Weather Service, contro risposte vere.
 *
 * `nws-*.json` sono catture di `api.weather.gov/alerts/active?area=..` fatte
 * dalla sonda `probe_allerte_mondo.py` il 5 ottobre 2026 (finite in
 * `ci-artifacts/api/allerte-mondo/`), non file scritti su come il formato
 * dovrebbe essere.
 */
class NwsAlertsTest {

    private fun risorsa(nome: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(nome)).bufferedReader().readText()

    private val pacifico = ZoneOffset.ofHours(-7)

    @Test
    fun `legge le allerte della California`() {
        val allerte = NwsAlerts.parse(risorsa("nws-california.json"), pacifico)
        assertTrue(allerte.isNotEmpty())
        assertTrue(allerte.all { it.official && it.fonte == FonteAllerte.NWS })
        // Caldo e mareggiate: nient'altro era in vigore quel giorno.
        assertTrue(allerte.all { it.kind == AlertKind.CALDO || it.kind == AlertKind.COSTIERO })
    }

    @Test
    fun `la severita' CAP decide il colore`() {
        val allerte = NwsAlerts.parse(risorsa("nws-california.json"), pacifico)
        val warning = allerte.first { it.description!!.startsWith("Extreme Heat Warning") }
        val advisory = allerte.first { it.description!!.startsWith("Heat Advisory") }
        assertEquals(AlertLevel.ARANCIONE, warning.level) // Severe
        assertEquals(AlertLevel.GIALLA, advisory.level) // Moderate
        // La piu' grave per prima.
        assertEquals(AlertLevel.ARANCIONE, allerte.first().level)
    }

    @Test
    fun `la fine e' ends, non la scadenza del messaggio`() {
        // Heat Advisory di NWS Hanford: expires alle 18 del 5, ends alle 23 del 7.
        val allerte = NwsAlerts.parse(risorsa("nws-california.json"), pacifico)
        val hanford = allerte.first { it.source.endsWith("NWS Hanford CA") && it.kind == AlertKind.CALDO && it.level == AlertLevel.GIALLA }
        assertEquals(LocalDateTime.of(2026, 10, 7, 23, 0), hanford.expires)
    }

    @Test
    fun `senza ends resta expires`() {
        // Il Flood Warning della Florida non porta `ends`.
        val allerte = NwsAlerts.parse(risorsa("nws-florida.json"), ZoneOffset.ofHours(-4))
        val senzaFine = allerte.filter { it.kind == AlertKind.PIOGGIA && it.level == AlertLevel.ARANCIONE }
            .first { it.expires == LocalDateTime.of(2026, 10, 5, 21, 0) }
        assertEquals(AlertKind.PIOGGIA, senzaFine.kind)
    }

    @Test
    fun `i tipi della Florida e dell'Alaska`() {
        val florida = NwsAlerts.parse(risorsa("nws-florida.json"), ZoneOffset.ofHours(-4))
        assertTrue(florida.any { it.kind == AlertKind.COSTIERO }) // Rip Current, Coastal Flood
        assertTrue(florida.any { it.kind == AlertKind.PIOGGIA }) // Flood Watch/Warning
        val alaska = NwsAlerts.parse(risorsa("nws-alaska.json"), ZoneOffset.ofHours(-8))
        assertTrue(alaska.any { it.kind == AlertKind.NEBBIA }) // Dense Fog Advisory
        assertTrue(alaska.any { it.kind == AlertKind.ALTRO }) // Special Weather Statement
    }

    @Test
    fun `lo stesso evento riemesso vale una volta`() {
        // Sei Extreme Heat Warning con la stessa fine, da uffici diversi.
        val allerte = NwsAlerts.parse(risorsa("nws-california.json"), pacifico)
        val chiavi = allerte.map { it.description!!.substringBefore("\n") to it.expires }
        assertEquals(chiavi.size, chiavi.toSet().size)
    }

    @Test
    fun `un punto senza allerte da' una lista vuota`() {
        val vuota = """{"type":"FeatureCollection","features":[],"title":"Current watches"}"""
        assertTrue(NwsAlerts.parse(vuota, null).isEmpty())
    }

    @Test
    fun `i paesi si instradano alla fonte giusta`() {
        assertEquals(FonteAllerte.NWS, WeatherAlertsRepository.fonteDi("Stati Uniti"))
        assertEquals(FonteAllerte.NWS, WeatherAlertsRepository.fonteDi("United States"))
        assertEquals(FonteAllerte.METEOALARM, WeatherAlertsRepository.fonteDi("Italia"))
        assertEquals(FonteAllerte.METEOALARM, WeatherAlertsRepository.fonteDi("Regno Unito"))
        // Tutti gli altri, e un posto senza paese, vanno all'IFRC.
        assertEquals(FonteAllerte.IFRC, WeatherAlertsRepository.fonteDi("Singapore"))
        assertEquals(FonteAllerte.IFRC, WeatherAlertsRepository.fonteDi(null))
    }

    @Test
    fun `i nomi degli eventi NWS diventano fenomeni`() {
        assertEquals(AlertKind.FREDDO, tipoDaEvento("Wind Chill Advisory"))
        assertEquals(AlertKind.VENTO, tipoDaEvento("Wind Advisory"))
        assertEquals(AlertKind.TEMPORALI, tipoDaEvento("Tornado Warning"))
        assertEquals(AlertKind.VENTO, tipoDaEvento("Hurricane Warning"))
        assertEquals(AlertKind.NEVE_GHIACCIO, tipoDaEvento("Winter Storm Watch"))
        assertEquals(AlertKind.INCENDI, tipoDaEvento("Red Flag Warning"))
        assertEquals(AlertKind.COSTIERO, tipoDaEvento("Coastal Flood Statement"))
        // E quelli di MeteoAlarm restano dove erano.
        assertEquals(AlertKind.CALDO, tipoDaEvento("Yellow High-temperature Warning"))
        assertEquals(AlertKind.NEVE_GHIACCIO, tipoDaEvento("Orange Snow-Ice Warning"))
        assertEquals(AlertKind.PIOGGIA, tipoDaEvento("Yellow Rain-Flood Warning"))
    }
}
