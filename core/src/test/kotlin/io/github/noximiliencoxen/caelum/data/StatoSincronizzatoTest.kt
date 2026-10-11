package io.github.noximiliencoxen.caelum.data

import io.github.noximiliencoxen.caelum.lingua.SceltaLingua
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatoSincronizzatoTest {

    private val forli = Place("Forlì", "Emilia-Romagna", "Italia", 44.2226, 12.0407)

    @Test
    fun `scritto e riletto torna uguale`() {
        val stato = StatoSincronizzato.di(forli, "FAHRENHEIT", "KN", SceltaLingua.INGLESE)
        assertEquals(stato, StatoSincronizzato.fromJson(stato.toJson()))
    }

    @Test
    fun `la localita' torna quella di partenza`() {
        val stato = StatoSincronizzato.di(forli, "CELSIUS", "KMH", SceltaLingua.AUTOMATICA)
        assertEquals(forli, StatoSincronizzato.fromJson(stato.toJson())?.toPlace())
    }

    @Test
    fun `i campi facoltativi possono mancare`() {
        val solo = StatoSincronizzato.fromJson("""{"nome":"Singapore","latitudine":1.29,"longitudine":103.85}""")
        assertEquals(Place("Singapore", null, null, 1.29, 103.85), solo?.toPlace())
        assertFalse(solo!!.inFahrenheit)
        assertEquals(SceltaLingua.AUTOMATICA, solo.sceltaLingua)
    }

    @Test
    fun `una lingua sconosciuta ripiega sull'automatica`() {
        val stato = StatoSincronizzato.fromJson("""{"nome":"X","latitudine":1.0,"longitudine":2.0,"lingua":"TEDESCO"}""")
        assertEquals(SceltaLingua.AUTOMATICA, stato?.sceltaLingua)
    }

    @Test
    fun `i campi di una versione piu' nuova non rompono la lettura`() {
        val stato = StatoSincronizzato.fromJson("""{"nome":"X","latitudine":1.0,"longitudine":2.0,"nuovo":42}""")
        assertEquals("X", stato?.nome)
    }

    @Test
    fun `un testo illeggibile non e' un messaggio`() {
        assertNull(StatoSincronizzato.fromJson("non json"))
        assertNull(StatoSincronizzato.fromJson("{}"))
    }

    @Test
    fun `coordinate che non esistono non passano`() {
        assertNull(StatoSincronizzato.fromJson("""{"nome":"X","latitudine":"NaN","longitudine":2.0}"""))
    }

    @Test
    fun `i gradi Fahrenheit si riconoscono`() {
        assertTrue(StatoSincronizzato.fromJson("""{"nome":"X","latitudine":1.0,"longitudine":2.0,"unita":"FAHRENHEIT"}""")!!.inFahrenheit)
    }
}
