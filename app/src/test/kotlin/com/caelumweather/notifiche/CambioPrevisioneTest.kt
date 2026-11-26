package com.caelumweather.notifiche

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CambioPrevisioneTest {

    private val oggi = LocalDate.of(2026, 11, 25)
    private val domani = oggi.plusDays(1)

    @Test
    fun `il respiro del modello non e' un cambio`() {
        assertNull(confronta(domani, Istantanea(14.0, false, 0.2), Istantanea(16.5, false, 0.6)))
    }

    @Test
    fun `la pioggia che compare e la massima che scende si dicono insieme`() {
        val c = confronta(domani, Istantanea(18.0, false, 0.0), Istantanea(13.6, true, 6.2))!!
        assertTrue(c.pioggiaArriva)
        assertEquals(-4, c.gradi)
        val (titolo, testo) = testiCambio("Parma", listOf(c), oggi)!!
        assertEquals("La previsione è cambiata a Parma", titolo)
        assertEquals("Domani: ora è prevista pioggia (6 mm), massima -4°, ora 14°.", testo)
    }

    @Test
    fun `la pioggia che sparisce e l'istantanea che si rilegge`() {
        val c = confronta(domani, Istantanea(10.0, true, 5.0), Istantanea(11.0, false, 0.3))!!
        assertTrue(c.pioggiaSparisce)
        assertNull(c.gradi)
        val s = Istantanea(10.5, true, 3.25)
        assertEquals(s, Istantanea.leggi(s.scrivi()))
        assertNull(Istantanea.leggi("rotta"))
        assertNull(testiCambio("Parma", emptyList(), oggi))
    }
}
