package io.github.noximiliencoxen.caelum.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class ScenaTest {

    @Test
    fun `a luglio la citta' natalizia non esce mai`() {
        val luglio = LocalDateTime.of(2026, 7, 15, 12, 0)
        val caso = Random(7)
        repeat(500) { assertTrue(scegliScena(luglio, caso) != Scena.CITTA_NATALE) }
    }

    @Test
    fun `a dicembre e fino all'Epifania puo' uscire, e tutte le scene escono prima o poi`() {
        val natale = LocalDateTime.of(2026, 12, 24, 18, 0)
        val caso = Random(3)
        val viste = (0 until 500).map { scegliScena(natale, caso) }.toSet()
        assertEquals(Scena.entries.toSet(), viste)
        assertTrue(natalizio(LocalDateTime.of(2027, 1, 6, 10, 0)))
        assertFalse(natalizio(LocalDateTime.of(2027, 1, 7, 10, 0)))
    }

    @Test
    fun `notte e giorno secondo la stagione`() {
        assertTrue(notteA(LocalDateTime.of(2026, 12, 21, 18, 0)))
        assertFalse(notteA(LocalDateTime.of(2026, 6, 21, 20, 30)))
        assertTrue(notteA(LocalDateTime.of(2026, 6, 21, 23, 0)))
        assertTrue(notteA(LocalDateTime.of(2026, 3, 1, 4, 0)))
        assertFalse(notteA(LocalDateTime.of(2026, 3, 1, 12, 0)))
    }
}
