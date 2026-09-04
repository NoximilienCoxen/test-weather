package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I codici WMO, e in particolare quali di essi bagnano.
 *
 * La trappola gia' pagata vive qui: **se il codice dice che piove, deve
 * piovere**. Sotto la scritta TEMPORALE non cadeva niente perche' la pioggia
 * si decideva dai millimetri, e un temporale previsto all'ottanta per cento
 * puo' averne zero in quell'ora esatta. I millimetri dicono quanto forte, non
 * se.
 */
class WmoTest {

    @Test
    fun `il coperto e' asciutto e il temporale no`() {
        assertFalse(Wmo.family(3).isWet())
        assertTrue(Wmo.family(95).isWet())
    }

    @Test
    fun `pioggia neve e rovesci bagnano`() {
        listOf(51, 61, 63, 65, 71, 75, 80, 82, 85, 95).forEach { code ->
            assertTrue("il codice $code dovrebbe bagnare", Wmo.family(code).isWet())
        }
    }

    @Test
    fun `sereno nuvoloso e nebbia non bagnano`() {
        listOf(0, 1, 2, 3, 45, 48).forEach { code ->
            assertFalse("il codice $code non dovrebbe bagnare", Wmo.family(code).isWet())
        }
    }

    @Test
    fun `un codice sconosciuto non inventa niente`() {
        assertEquals(Wmo.Family.ASCIUTTO, Wmo.family(null))
        assertEquals("--", Wmo.condition(null))
    }
}
