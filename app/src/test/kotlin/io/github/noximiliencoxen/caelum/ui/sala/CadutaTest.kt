package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.isWet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cosa cade, per ogni codice WMO che esiste.
 *
 * **Il difetto che questo test chiude si vedeva solo guardando il cielo.** La
 * tavolozza chiamava "grandine" i codici 77, 85 e 86 - granuli e rovesci di
 * **neve** - mentre la famiglia WMO li chiamava neve. Le due strade arrivavano
 * tutte e due alla scena, quindi per quei tre codici cadevano chicchi e fiocchi
 * **insieme**, dalla stessa nuvola, nello stesso istante. Negli scatti non si
 * notava: a quella taglia un chicco e un fiocco sono due dischi chiari.
 *
 * E la neve vera - 71, 73, 75 - finiva fra le piogge, col titolo "Pioggia nella
 * notte" sopra una nevicata.
 *
 * Qui si prova la cosa nella forma in cui puo' rompersi di nuovo: non "il
 * codice 86 fa questo", ma **nessun codice, fra tutti quelli che esistono, fa
 * cadere due sostanze insieme**. Un domani in cui qualcuno aggiunge una
 * famiglia, questa riga la trova.
 */
class CadutaTest {

    /** Un cielo qualunque: quello che cade non dipende dall'ora. */
    private val cielo = SkyState.of(altitude = 0.5f)

    private fun scenaDi(code: Int): Scena = scenaBersaglio(
        sky = cielo,
        condition = salaConditionOf(code),
        nevicaWmo = Wmo.family(code) == Wmo.Family.NEVE,
        coperturaOraria = null,
        pioggiaMm = null,
    )

    /** I codici che Open-Meteo puo' davvero restituire. */
    private val codici = listOf(
        0, 1, 2, 3, 45, 48,
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        71, 73, 75, 77,
        80, 81, 82, 85, 86,
        95, 96, 99,
    )

    @Test
    fun `nessun codice fa cadere neve e ghiaccio insieme`() {
        codici.forEach { code ->
            val scena = scenaDi(code)
            assertTrue(
                "il codice $code fa cadere neve ${scena.neve} e ghiaccio ${scena.ghiaccio}",
                scena.neve <= 0f || scena.ghiaccio <= 0f,
            )
        }
    }

    @Test
    fun `nevica per tutti e soli i codici della neve`() {
        codici.forEach { code ->
            val nevoso = Wmo.family(code) == Wmo.Family.NEVE
            assertEquals("codice $code", nevoso, scenaDi(code).neve > 0f)
        }
    }

    @Test
    fun `la grandine cade solo dentro il temporale che la fa`() {
        codici.forEach { code ->
            val conGrandine = code == 96 || code == 99
            assertEquals("codice $code", conGrandine, scenaDi(code).ghiaccio > 0f)
        }
    }

    /**
     * La trappola gia' pagata di `WmoTest`, riletta di qua: **se il codice dice
     * che cade, deve cadere**, anche senza millimetri. `pioggiaMm` qui e' nullo
     * apposta - e' il caso del temporale previsto che in quell'ora esatta non
     * ha ancora scaricato niente.
     */
    @Test
    fun `tutto cio che precipita cade anche senza millimetri`() {
        codici.filter { Wmo.family(it).isWet() }.forEach { code ->
            assertTrue("il codice $code non fa cadere niente", scenaDi(code).bagnato >= 0.55f)
        }
    }

    @Test
    fun `sotto il sereno e il coperto non cade niente`() {
        listOf(0, 1, 2, 3).forEach { code ->
            assertEquals("codice $code", 0f, scenaDi(code).bagnato, 0.0001f)
        }
    }

    /**
     * La neve non tinge di scuro l'interfaccia.
     *
     * Ci finiva dentro perche' era etichettata "grandine", e una cella di
     * grandine porta con se' il buio del fronte che la fa. Una nevicata e' il
     * contrario: e' la giornata piu' chiara dell'anno.
     */
    @Test
    fun `la neve non porta il tema scuro`() {
        listOf(71, 73, 75, 77, 85, 86).forEach { code ->
            assertTrue("codice $code", !temaScuroPerTempesta(salaConditionOf(code)))
        }
        listOf(95, 96, 99).forEach { code ->
            assertTrue("codice $code", temaScuroPerTempesta(salaConditionOf(code)))
        }
    }
}
