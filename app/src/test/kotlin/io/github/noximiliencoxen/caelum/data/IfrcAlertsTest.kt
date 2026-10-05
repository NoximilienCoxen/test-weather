package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Il lettore dell'IFRC Alert Hub, contro risposte vere.
 *
 * `ifrc-regioni-russia.json` sono le 90 regioni della Russia con i loro
 * riquadri; `ifrc-allerte-russia.json` le quattro allerte della regione
 * "Unknown" (nebbia, ciascuna in russo e in inglese), dalla sonda del 5
 * ottobre 2026.
 */
class IfrcAlertsTest {

    private fun risorsa(nome: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(nome)).bufferedReader().readText()

    private val adesso = OffsetDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `le regioni si leggono coi loro riquadri`() {
        val regioni = IfrcAlerts.luoghi(risorsa("ifrc-regioni-russia.json"), "admin1s")
        assertEquals(89, regioni.size) // 90 meno "Unknown", che non ha riquadro
        // Mosca: la citta' sta dentro la sua oblast, e vince il riquadro piu' piccolo.
        val mosca = IfrcAlerts.tuttiQuelliCheContengono(regioni, 55.75, 37.62)
        assertEquals("Moskva", mosca.first().nome)
        assertTrue(mosca.any { it.nome == "Moskovskaya Oblast" })
    }

    @Test
    fun `i riquadri si sovrappongono, e Tomsk resta fra le candidate`() {
        val regioni = IfrcAlerts.luoghi(risorsa("ifrc-regioni-russia.json"), "admin1s")
        val tomsk = IfrcAlerts.tuttiQuelliCheContengono(regioni, 56.50, 84.97)
        assertTrue(tomsk.size > 1)
        assertTrue(tomsk.take(3).any { it.nome == "Tomskaya Oblast" })
    }

    @Test
    fun `una per lingua diventa una sola, in inglese, e solo dentro il poligono`() {
        val dentro = IfrcAlerts.parse(risorsa("ifrc-allerte-russia.json"), 47.2, 35.5, ZoneOffset.ofHours(3), adesso)
        assertEquals(1, dentro.size)
        val a = dentro.single()
        assertEquals(AlertKind.NEBBIA, a.kind) // "Fog", dalla voce inglese
        assertEquals(AlertLevel.GIALLA, a.level) // MODERATE
        assertEquals(FonteAllerte.IFRC, a.fonte)
        assertTrue(a.description!!.startsWith("Fog"))
        // Mosca non sta in nessuno dei poligoni.
        assertTrue(IfrcAlerts.parse(risorsa("ifrc-allerte-russia.json"), 55.75, 37.62, null, adesso).isEmpty())
    }

    @Test
    fun `i nomi spagnoli e portoghesi diventano fenomeni`() {
        // "Nevadas" e "Tempestade" sono gli eventi veri di SMN e INMET del 5 ottobre.
        assertEquals(AlertKind.NEVE_GHIACCIO, tipoDaEvento("Nevadas"))
        assertEquals(AlertKind.TEMPORALI, tipoDaEvento("Tempestade"))
        assertEquals(AlertKind.PIOGGIA, tipoDaEvento("Acumulado de Chuva"))
        assertEquals(AlertKind.VENTO, tipoDaEvento("Viento"))
        assertEquals(AlertKind.NEBBIA, tipoDaEvento("Nevoeiro"))
        assertEquals(AlertKind.ALTRO, tipoDaEvento("Baixa Umidade"))
    }

    @Test
    fun `le allerte scadute non si mostrano`() {
        val dopo = OffsetDateTime.of(2026, 10, 8, 0, 0, 0, 0, ZoneOffset.UTC)
        assertTrue(IfrcAlerts.parse(risorsa("ifrc-allerte-russia.json"), 47.2, 35.5, null, dopo).isEmpty())
    }
}
