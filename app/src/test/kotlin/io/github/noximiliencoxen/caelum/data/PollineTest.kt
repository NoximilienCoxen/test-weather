package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Il polline dai granuli ai livelli, e dalle ore ai giorni.
 *
 * Il difetto da tenere fermo e' quello che la sala dell'aria dichiarava da
 * sempre: tre pastiglie con dentro un numero inventato. Qui si prova che dove
 * il dato non c'e' non esce niente, e che dove c'e' il livello e' quello del
 * picco del giorno, con le soglie della specie giusta.
 */
class PollineTest {

    @Test
    fun `ogni specie ha le sue soglie`() {
        // Dieci granuli di graminacee sono "moderato", dieci di ambrosia gia'
        // "moderato" da cinque: le erbacce fanno male con meno.
        assertEquals(0, SpeciePolline.GRAMINACEE.livello(0.0))
        assertEquals(1, SpeciePolline.GRAMINACEE.livello(3.0))
        assertEquals(2, SpeciePolline.GRAMINACEE.livello(10.0))
        assertEquals(3, SpeciePolline.GRAMINACEE.livello(30.0))
        assertEquals(4, SpeciePolline.GRAMINACEE.livello(500.0))
        assertEquals(2, SpeciePolline.AMBROSIA.livello(6.0))
        assertEquals(1, SpeciePolline.BETULLA.livello(6.0))
    }

    @Test
    fun `il giorno prende il picco, e gli alberi la specie peggiore`() {
        val tempi = listOf("2026-09-25T08:00", "2026-09-25T14:00", "2026-09-26T08:00")
        val giorni = Polline.giorni(
            tempi,
            mapOf(
                SpeciePolline.GRAMINACEE to listOf(2.0, 40.0, 0.0),
                SpeciePolline.BETULLA to listOf(0.0, 1.0, 0.0),
                SpeciePolline.OLIVO to listOf(30.0, 0.0, null),
                SpeciePolline.AMBROSIA to listOf(null, null, 6.0),
            ),
        )
        assertEquals(2, giorni.size)
        val oggi = giorni[0]
        assertEquals(LocalDate.of(2026, 9, 25), oggi.giorno)
        assertEquals(3, oggi.livelli[TipoPolline.ERBA])
        assertEquals(3, oggi.livelli[TipoPolline.ALBERI])
        // Nessun valore di erbacce il primo giorno: la famiglia manca, non e' zero.
        assertFalse(oggi.livelli.containsKey(TipoPolline.ERBACCE))
        assertEquals(3, oggi.massimo)
        assertEquals(2, giorni[1].livelli[TipoPolline.ERBACCE])
    }

    @Test
    fun `fuori dall'Europa non esce niente`() {
        val tempi = listOf("2026-09-25T08:00", "2026-09-25T09:00")
        val tuttoNullo = SpeciePolline.entries.associateWith { listOf<Double?>(null, null) }
        assertTrue(Polline.giorni(tempi, tuttoNullo).isEmpty())
        assertTrue(Polline.giorni(tempi, emptyMap()).isEmpty())
    }

    @Test
    fun `al piu' tre giorni, dal primo`() {
        val tempi = (0 until 5).map { "2026-09-2${5 + it}T12:00" }
        val giorni = Polline.giorni(tempi, mapOf(SpeciePolline.GRAMINACEE to List(5) { 1.0 }))
        assertEquals(3, giorni.size)
        assertEquals(LocalDate.of(2026, 9, 25), giorni.first().giorno)
    }

    @Test
    fun `i campi della richiesta sono quelli dell'API`() {
        assertEquals(
            "alder_pollen,birch_pollen,olive_pollen,grass_pollen,mugwort_pollen,ragweed_pollen",
            SpeciePolline.CAMPI,
        )
    }
}
