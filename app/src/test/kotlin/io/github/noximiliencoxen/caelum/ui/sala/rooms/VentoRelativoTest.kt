package io.github.noximiliencoxen.caelum.ui.sala.rooms

import org.junit.Assert.assertEquals
import org.junit.Test

class VentoRelativoTest {

    @Test
    fun `vento da nord guardando a est arriva da sinistra`() {
        assertEquals(-90f, ventoRispettoASguardo(0f, 90f), 1e-4f)
        assertEquals("ti arriva da sinistra", latoDelVento(ventoRispettoASguardo(0f, 90f)))
    }

    @Test
    fun `vento da dove si guarda arriva in faccia, dal lato opposto alle spalle`() {
        assertEquals("ti arriva in faccia", latoDelVento(ventoRispettoASguardo(350f, 10f)))
        assertEquals("ti arriva alle spalle", latoDelVento(ventoRispettoASguardo(180f, 0f)))
    }

    @Test
    fun `lo scarto resta fra meno 180 e 180 attraverso il nord`() {
        assertEquals(20f, ventoRispettoASguardo(10f, 350f), 1e-4f)
        assertEquals("ti arriva da destra", latoDelVento(ventoRispettoASguardo(90f, 0f)))
    }
}
