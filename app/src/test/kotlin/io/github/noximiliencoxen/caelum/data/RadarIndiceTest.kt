package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * La scelta del fotogramma, che e' il difetto che l'app aveva addosso.
 *
 * La carta mostrava **sempre** il piu' recente, sotto qualunque ora scelta: la
 * barra diceva le ventidue e la carta era delle quindici e quaranta, ferma,
 * uguale a se stessa tutta la giornata. Sembrava una fotografia appesa, e nel
 * frattempo invitava a leggere quella pioggia come se fosse delle ventidue.
 *
 * Quello che si prova qui e' che il "se" esista: o il fotogramma e' di
 * quell'ora, o non ce n'e' uno. Un radar **misura**, e quello che non ha
 * misurato non lo sa.
 */
class RadarIndiceTest {

    // Dodici fotogrammi a dieci minuti l'uno dall'altro, come quelli veri:
    // dalle 20:40 alle 22:30.
    private val adesso: Instant = Instant.parse("2026-09-18T22:35:00Z")
    private val indice = RadarIndice(
        host = "https://esempio",
        fotogrammi = (0..11).map {
            RadarFotogramma(adesso.minus(Duration.ofMinutes((115 - it * 10).toLong())), "/f$it")
        },
    )

    @Test
    fun `l'ora corrente trova il proprio fotogramma`() {
        val scelto = indice.vicinoA(adesso)
        assertEquals(indice.ultimo?.istante, scelto?.istante)
    }

    @Test
    fun `un'ora di un'ora fa trova il fotogramma di allora, non l'ultimo`() {
        // E' la prova che il difetto e' finito: prima qui sarebbe tornato
        // l'ultimo, cioe' la pioggia di adesso sotto l'etichetta di un'ora fa.
        val unOraFa = adesso.minus(Duration.ofHours(1))
        val scelto = indice.vicinoA(unOraFa)!!
        val scarto = Duration.between(scelto.istante, unOraFa).abs()
        assertTrue("scarto $scarto", scarto <= Duration.ofMinutes(10))
        assertTrue("ha preso l'ultimo", scelto.istante != indice.ultimo?.istante)
    }

    @Test
    fun `stanotte alle tre non c'e' nessun fotogramma`() {
        assertNull(indice.vicinoA(adesso.plus(Duration.ofHours(4))))
        assertNull(indice.vicinoA(adesso.minus(Duration.ofHours(6))))
    }

    @Test
    fun `il bordo della tolleranza e' dove dice di essere`() {
        val ultimo = indice.ultimo!!.istante
        // Mezz'ora dopo l'ultimo fotogramma: ancora dentro, di un soffio.
        assertEquals(ultimo, indice.vicinoA(ultimo.plus(Duration.ofMinutes(29)))?.istante)
        // Mezz'ora e un minuto: fuori. Allargare vorrebbe dire far passare per
        // "le venti" una pioggia delle ventuno e mezza.
        assertNull(indice.vicinoA(ultimo.plus(Duration.ofMinutes(31))))
    }

    @Test
    fun `un indice vuoto non sceglie niente invece di esplodere`() {
        val vuoto = RadarIndice("https://esempio", emptyList())
        assertNull(vuoto.vicinoA(adesso))
        assertNull(vuoto.ultimo)
    }
}
