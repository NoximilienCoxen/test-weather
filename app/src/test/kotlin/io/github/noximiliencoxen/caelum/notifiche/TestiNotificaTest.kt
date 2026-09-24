package io.github.noximiliencoxen.caelum.notifiche

import io.github.noximiliencoxen.caelum.data.PrecipitazioneInArrivo
import io.github.noximiliencoxen.caelum.data.TipoPrecipitazione
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TestiNotificaTest {

    private val adesso = LocalDateTime.of(2026, 9, 24, 14, 5)

    @Test
    fun `la pioggia dice fra quanto e quanta`() {
        val (titolo, testo) = testiNotifica(
            "Noceto",
            PrecipitazioneInArrivo(TipoPrecipitazione.PIOGGIA, LocalDateTime.of(2026, 9, 24, 14, 30), 1.5),
            adesso,
        )
        assertEquals("Pioggia in arrivo a Noceto", titolo)
        assertTrue(testo, testo.contains("fra circa 25 minuti"))
        assertTrue(testo, testo.contains("14:30"))
        assertTrue(testo, testo.contains("1,5 mm"))
    }

    @Test
    fun `la grandine ha il suo titolo`() {
        val (titolo, testo) = testiNotifica(
            "Fontevivo",
            PrecipitazioneInArrivo(TipoPrecipitazione.GRANDINE, LocalDateTime.of(2026, 9, 24, 14, 10), 3.0),
            adesso,
        )
        assertEquals("Grandine in arrivo a Fontevivo", titolo)
        assertTrue(testo, testo.contains("a momenti"))
    }
}
