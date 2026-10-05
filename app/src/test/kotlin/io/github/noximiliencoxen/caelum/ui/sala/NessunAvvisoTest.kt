package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.data.FonteAllerte
import io.github.noximiliencoxen.caelum.data.StatoAllerteUfficiali
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il bollettino vuoto dice cosa e' successo, non solo che e' vuoto.
 *
 * Per tutto il tempo del 406 di MeteoAlarm (CONTESTO §8-ter) scriveva "non è
 * arrivata nessuna allerta ufficiale" sopra un controllo mai avvenuto.
 */
class NessunAvvisoTest {

    @Test
    fun `con il feed arrivato si rassicura`() {
        val (etichetta, testo) = testoNessunAvviso("Fontevivo", StatoAllerteUfficiali.ARRIVATE)
        assertEquals("NESSUN AVVISO", etichetta)
        assertTrue(testo.startsWith("Per Fontevivo non è arrivata nessuna allerta ufficiale"))
    }

    @Test
    fun `con il feed fallito non si rassicura`() {
        val (etichetta, testo) = testoNessunAvviso("Fontevivo", StatoAllerteUfficiali.NON_ARRIVATE)
        assertEquals("ALLERTE NON VERIFICATE", etichetta)
        assertTrue(testo.contains("non si sono potute controllare"))
        assertFalse(testo.contains("non è arrivata nessuna allerta"))
    }

    @Test
    fun `il silenzio si attribuisce alla fonte del posto`() {
        val testo = testoNessunAvviso("New York", StatoAllerteUfficiali.NON_ARRIVATE, FonteAllerte.NWS).second
        assertTrue(testo.contains("National Weather Service non ha risposto"))
    }

    @Test
    fun `fuori copertura e in attesa hanno parole loro`() {
        val fuori = testoNessunAvviso("Singapore", StatoAllerteUfficiali.FUORI_COPERTURA).second
        val attesa = testoNessunAvviso("Forlì", StatoAllerteUfficiali.IN_ATTESA).second
        assertTrue(fuori.contains("National Weather Service negli Stati Uniti"))
        assertTrue(attesa.contains("stanno ancora arrivando"))
    }
}
