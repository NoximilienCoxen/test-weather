package com.caelumweather.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il codice di un'ora non promette pioggia a un'ora con zero millimetri.
 *
 * Il caso vero: Forli', 15 novembre 2026, ore 13. Sala I diceva "Rovescio di
 * meta' pomeriggio - Pioggia in corso", la sala della pioggia alla stessa ora
 * "0,0 mm, 18 %".
 */
class CodiceDellOraTest {

    @Test
    fun `rovesci senza millimetri diventano il cielo delle nuvole`() {
        assertEquals(3, codiceDellOra(80, 0.0, 95))
        assertEquals(2, codiceDellOra(61, 0.0, 60))
        assertEquals(1, codiceDellOra(80, 0.0, 20))
    }

    @Test
    fun `senza nuvolosita' resta coperto, mai sereno`() {
        assertEquals(3, codiceDellOra(80, 0.0, null))
        assertEquals(1, codiceDellOra(95, 0.0, 0))
    }

    @Test
    fun `un decimo di millimetro e' pioggia vera`() {
        assertEquals(61, codiceDellOra(61, 0.1, 90))
    }

    @Test
    fun `millimetri che mancano non autorizzano a correggere`() {
        assertEquals(80, codiceDellOra(80, null, 90))
    }

    @Test
    fun `il tempo asciutto non si tocca`() {
        assertEquals(2, codiceDellOra(2, 0.0, 90))
        assertEquals(45, codiceDellOra(45, 0.0, 100))
        assertEquals(null, codiceDellOra(null, 0.0, 100))
    }
}
