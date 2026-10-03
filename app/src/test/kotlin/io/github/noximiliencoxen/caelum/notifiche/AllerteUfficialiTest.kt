package io.github.noximiliencoxen.caelum.notifiche

import io.github.noximiliencoxen.caelum.data.AlertKind
import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Quali allerte diventano una notifica, e quante volte.
 *
 * La regola che conta e' la stessa della fascia che il feed aveva: una notizia
 * si dice una volta, ma la gialla che diventa arancione e' una notizia nuova
 * pur avendo lo stesso identificativo.
 */
class AllerteUfficialiTest {

    private val adesso = LocalDateTime.of(2026, 9, 30, 10, 0)

    private fun allerta(
        id: String = "urn:oid:2.49.0.1.380.0.2026.1",
        livello: AlertLevel = AlertLevel.ARANCIONE,
        ufficiale: Boolean = true,
        fine: LocalDateTime? = adesso.plusHours(12),
        inizio: LocalDateTime? = adesso.minusHours(2),
    ) = WeatherAlert(
        id = id,
        level = livello,
        kind = AlertKind.TEMPORALI,
        headline = "${livello.label}: temporali",
        onset = inizio,
        expires = fine,
        areaDesc = "Emilia-Romagna",
        source = "MeteoAlarm",
        official = ufficiale,
    )

    @Test
    fun `solo arancioni e rosse, e solo ufficiali`() {
        val tutte = listOf(
            allerta(id = "g", livello = AlertLevel.GIALLA),
            allerta(id = "a", livello = AlertLevel.ARANCIONE),
            allerta(id = "r", livello = AlertLevel.ROSSA),
            allerta(id = "s", livello = AlertLevel.ROSSA, ufficiale = false),
        )
        val dette = allerteDaNotificare(tutte, emptySet(), adesso).map { it.id }
        // La piu' grave per prima.
        assertEquals(listOf("r", "a"), dette)
    }

    @Test
    fun `una volta detta tace, finche' non peggiora`() {
        val arancione = allerta()
        val memoria = memoriaAggiornata(listOf(arancione), emptySet(), adesso)
        assertTrue(allerteDaNotificare(listOf(arancione), memoria, adesso).isEmpty())

        val rossa = allerta(livello = AlertLevel.ROSSA)
        assertEquals(listOf(rossa), allerteDaNotificare(listOf(rossa), memoria, adesso))

        // E una volta detta rossa, tornare arancione non e' una notizia.
        val dopo = memoriaAggiornata(listOf(rossa), memoria, adesso)
        assertEquals(1, dopo.size)
        assertTrue(allerteDaNotificare(listOf(arancione), dopo, adesso).isEmpty())
    }

    @Test
    fun `un feed vuoto per un guasto non cancella la memoria`() {
        val arancione = allerta()
        val memoria = memoriaAggiornata(listOf(arancione), emptySet(), adesso)
        val dopoIlBuco = memoriaAggiornata(emptyList(), memoria, adesso.plusHours(1))
        assertTrue(allerteDaNotificare(listOf(arancione), dopoIlBuco, adesso.plusHours(2)).isEmpty())
    }

    @Test
    fun `la memoria dimentica le allerte finite da piu' di un giorno`() {
        val memoria = memoriaAggiornata(listOf(allerta()), emptySet(), adesso)
        assertTrue(memoriaAggiornata(emptyList(), memoria, adesso.plusDays(3)).isEmpty())
    }

    @Test
    fun `un'allerta gia' finita non si notifica`() {
        assertTrue(allerteDaNotificare(listOf(allerta(fine = adesso.minusMinutes(1))), emptySet(), adesso).isEmpty())
    }

    @Test
    fun `il testo dice colore, fenomeno, posto e quando`() {
        val (titolo, testo) = testiAllerta("Forlì", allerta(), adesso)
        assertEquals("Allerta arancione a Forlì: temporali", titolo)
        assertTrue(testo, testo.startsWith("In corso fino alle 22:00 di oggi."))
        assertTrue(testo, testo.contains("Emilia-Romagna"))

        val (_, futura) = testiAllerta(
            "Forlì",
            allerta(inizio = adesso.plusHours(14), fine = adesso.plusHours(30)),
            adesso,
        )
        assertTrue(futura, futura.startsWith("Dalle 00:00 di domani alle 16:00 di domani."))
    }
}
