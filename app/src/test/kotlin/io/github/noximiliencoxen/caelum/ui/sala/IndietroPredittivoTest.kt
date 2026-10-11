package io.github.noximiliencoxen.caelum.ui.sala

import org.junit.Assert.assertEquals
import org.junit.Test

class IndietroPredittivoTest {

    @Test
    fun `a gesto fermo il pannello e' a grandezza piena`() {
        assertEquals(1f, scalaIndietro(0f), 0f)
    }

    @Test
    fun `a gesto completo il pannello perde il dieci per cento`() {
        assertEquals(0.9f, scalaIndietro(1f), 1e-6f)
    }

    @Test
    fun `a meta' gesto sta a meta' strada`() {
        assertEquals(0.95f, scalaIndietro(0.5f), 1e-6f)
    }

    @Test
    fun `i valori fuori dall'intervallo si fermano ai bordi`() {
        assertEquals(1f, scalaIndietro(-0.3f), 0f)
        assertEquals(0.9f, scalaIndietro(1.4f), 1e-6f)
    }
}
