package io.github.noximiliencoxen.caelum.lingua

import io.github.noximiliencoxen.caelum.data.AlertLevel
import io.github.noximiliencoxen.caelum.data.MoonSegment
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LinguaTest {

    @After
    fun tornaItaliano() = Lingua.applica(SceltaLingua.ITALIANO)

    @Test
    fun `la stessa voce nelle due lingue`() {
        Lingua.applica(SceltaLingua.ITALIANO)
        assertEquals("TEMPORALE E GRANDINE", Wmo.condition(96))
        assertEquals("PLENILUNIO", MoonSegment.PLENILUNIO.label)
        assertEquals("ALLERTA ROSSA", AlertLevel.ROSSA.label)
        assertEquals("La settimana", SalaRoom.SETTIMANA.heading)
        assertEquals("SO", Wmo.windDirection(225.0))

        Lingua.applica(SceltaLingua.INGLESE)
        assertEquals("THUNDERSTORM WITH HAIL", Wmo.condition(96))
        assertEquals("FULL MOON", MoonSegment.PLENILUNIO.label)
        assertEquals("RED WARNING", AlertLevel.ROSSA.label)
        assertEquals("The week", SalaRoom.SETTIMANA.heading)
        assertEquals("SW", Wmo.windDirection(225.0))
    }
}
