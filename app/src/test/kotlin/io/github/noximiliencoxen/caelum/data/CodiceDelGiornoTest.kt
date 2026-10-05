package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Il codice del giorno non promette pioggia a un giorno con zero millimetri.
 *
 * Il caso vero: Forli', 6 ottobre 2026, `weather_code` 80 (rovesci) con
 * `precipitation_sum` 0,0 - e a Noceto le gocce sull'icona di "oggi" con la
 * sala della pioggia a 0,0 mm.
 */
class CodiceDelGiornoTest {

    @Test
    fun `rovesci senza millimetri diventano il tempo asciutto delle ore`() {
        val ore = listOf(2, 2, 3, 2, 80, 2)
        assertEquals(2, codiceDelGiorno(80, 0.0, ore))
    }

    @Test
    fun `senza ore resta coperto`() {
        assertEquals(3, codiceDelGiorno(61, 0.0, emptyList()))
    }

    @Test
    fun `a parita' vince il piu' coperto`() {
        assertEquals(3, codiceDelGiorno(80, 0.0, listOf(1, 3, 1, 3)))
    }

    @Test
    fun `con millimetri veri il codice resta`() {
        assertEquals(61, codiceDelGiorno(61, 1.7, listOf(2, 3)))
        // Un decimo di millimetro e' una pioviggine vera.
        assertEquals(51, codiceDelGiorno(51, 0.1, listOf(2, 3)))
    }

    @Test
    fun `senza totale non si corregge niente`() {
        assertEquals(80, codiceDelGiorno(80, null, listOf(2)))
    }

    @Test
    fun `i codici asciutti non si toccano`() {
        assertEquals(45, codiceDelGiorno(45, 0.0, listOf(2)))
        assertEquals(3, codiceDelGiorno(3, 0.0, listOf(1)))
    }

    @Test
    fun `anche un temporale senza una goccia si corregge`() {
        assertEquals(3, codiceDelGiorno(95, 0.0, listOf(3, 3, 2)))
    }
}
