package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class BarometroTest {

    private val mezzanotte = LocalDateTime.of(2026, 10, 11, 0, 0)

    private fun ore(vararg hPa: Double?): List<HourForecast> =
        hPa.mapIndexed { i, p -> HourForecast(time = mezzanotte.plusHours(i.toLong()), pressure = p) }

    @Test
    fun `la tendenza si legge sulle tre ore prima`() {
        val p = pressioneAlle(ore(1015.0, 1014.0, 1013.0, 1010.5), mezzanotte.plusHours(3))!!
        assertEquals(1010.5, p.hPa, 0.0)
        assertEquals(-4.5, p.scarto!!, 1e-9)
        assertEquals(TendenzaPressione.SCENDE_IN_FRETTA, p.tendenza)
    }

    @Test
    fun `a mezzanotte la tendenza non si inventa`() {
        val p = pressioneAlle(ore(1015.0, 1016.0), mezzanotte)!!
        assertNull(p.scarto)
        assertNull(p.tendenza)
    }

    @Test
    fun `senza pressione a quell'ora non c'e' niente da dire`() {
        assertNull(pressioneAlle(ore(1015.0, null), mezzanotte.plusHours(1)))
        assertNull(pressioneAlle(ore(1015.0), mezzanotte.plusHours(5)))
    }

    @Test
    fun `le soglie della tendenza`() {
        assertEquals(TendenzaPressione.STABILE, tendenzaDi(0.9))
        assertEquals(TendenzaPressione.STABILE, tendenzaDi(-0.9))
        assertEquals(TendenzaPressione.SALE, tendenzaDi(1.0))
        assertEquals(TendenzaPressione.SALE_IN_FRETTA, tendenzaDi(3.5))
        assertEquals(TendenzaPressione.SCENDE, tendenzaDi(-3.4))
        assertEquals(TendenzaPressione.SCENDE_IN_FRETTA, tendenzaDi(-3.5))
    }

    @Test
    fun `l'afa viene dalla rugiada`() {
        assertEquals(Afa.SECCA, afaDi(4.0))
        assertEquals(Afa.GRADEVOLE, afaDi(12.0))
        assertEquals(Afa.UMIDA, afaDi(16.5))
        assertEquals(Afa.AFOSA, afaDi(19.0))
        assertEquals(Afa.OPPRIMENTE, afaDi(23.0))
    }
}
