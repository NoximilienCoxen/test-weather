package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * La carta della pioggia prevista, che e' quella che risponde per ventuno ore
 * su ventiquattro.
 *
 * Il radar tiene due ore di storico: alle 17:06 risponde dalle 15:10 alle
 * 17:00. Chi usa l'app ha scorso fino alle 11:00, ha trovato "niente per
 * quest'ora" e ha detto la cosa giusta - una mappa che risponde per tre ore
 * non e' una mappa.
 *
 * Quello che si prova qui e' che la previsione abbia lo **stesso rigore** del
 * radar sull'ora: un valore orario mostrato sotto un'ora diversa sarebbe di
 * nuovo un dato vero messo dove non e' vero, e il fatto che sia una previsione
 * non lo rende meno sbagliato.
 */
class MappaPrevistaTest {

    private val mezzanotte: Instant = Instant.parse("2026-09-18T00:00:00Z")

    /** Quarantotto ore, e quattro punti con valori riconoscibili. */
    private val mappa = MappaPrevista(
        ore = (0 until 48).map { mezzanotte.plus(Duration.ofHours(it.toLong())) },
        punti = listOf(
            PuntoPrevisto(44.0, 12.0, List(48) { it * 0.1f }),
            PuntoPrevisto(44.5, 12.0, List(48) { 0f }),
            PuntoPrevisto(44.0, 12.5, List(48) { 2f }),
            PuntoPrevisto(44.5, 12.5, List(48) { 5f }),
        ),
    )

    @Test
    fun `un'ora dentro la finestra torna un valore per ogni punto`() {
        val valori = mappa.a(mezzanotte.plus(Duration.ofHours(10)))
        assertNotNull(valori)
        assertEquals(4, valori!!.size)
        assertEquals(1.0f, valori[0].second, 0.001f)
    }

    @Test
    fun `l'ora giusta e non un'altra`() {
        // Il punto zero vale un decimo per ogni ora passata: se tornasse
        // l'ora sbagliata, questo numero lo direbbe subito.
        (0 until 48 step 7).forEach { h ->
            val valori = mappa.a(mezzanotte.plus(Duration.ofHours(h.toLong())))!!
            assertEquals("ora $h", h * 0.1f, valori[0].second, 0.001f)
        }
    }

    @Test
    fun `dentro la finestra ogni istante trova la propria ora`() {
        // **Questa prova e' stata scritta sbagliata la prima volta, e la nota
        // resta perche' l'errore era istruttivo.** Diceva: mezz'ora di scarto
        // passa, un'ora no - copiata dal test del radar senza accorgersi che
        // i due casi non si somigliano. I fotogrammi del radar distano dieci
        // minuti **e finiscono**: dopo l'ultimo non c'e' piu' niente, e la
        // tolleranza morde. Le ore del modello distano un'ora e si toccano:
        // dentro la finestra, qualunque istante ha un'ora a meno di trenta
        // minuti, sempre. La tolleranza li' non rifiuta niente - e non deve.
        //
        // Un test che "verificava" un comportamento impossibile avrebbe
        // continuato a fallire finche' qualcuno non avesse storpiato il codice
        // per accontentarlo.
        listOf(0, 17, 31, 59).forEach { minuti ->
            val quando = mezzanotte.plus(Duration.ofHours(5)).plus(Duration.ofMinutes(minuti.toLong()))
            assertNotNull("a $minuti minuti", mappa.a(quando))
        }
    }

    @Test
    fun `oltre l'ultima ora la tolleranza morde`() {
        // Qui si', perche' dopo l'ultima ora non c'e' un'altra ora a raccogliere
        // l'istante: e' il bordo della finestra, ed e' li' che la tolleranza
        // serve.
        val ultima = mezzanotte.plus(Duration.ofHours(47))
        assertNotNull(mappa.a(ultima.plus(Duration.ofMinutes(29))))
        assertNull(mappa.a(ultima.plus(Duration.ofMinutes(31))))
    }

    @Test
    fun `fuori dalle ore che il modello copre non si inventa niente`() {
        assertNull(mappa.a(mezzanotte.minus(Duration.ofDays(1))))
        assertNull(mappa.a(mezzanotte.plus(Duration.ofDays(5))))
    }

    @Test
    fun `una mappa vuota non esplode`() {
        val vuota = MappaPrevista(emptyList(), emptyList())
        assertNull(vuota.a(mezzanotte))
    }

    @Test
    fun `un punto con meno ore degli altri non fa cadere la carta`() {
        // Open-Meteo risponde punto per punto, e non c'e' niente che prometta
        // che tutti abbiano la stessa lunghezza: un punto corto deve sparire
        // dalla carta, non portarsela dietro.
        val zoppa = MappaPrevista(
            ore = (0 until 48).map { mezzanotte.plus(Duration.ofHours(it.toLong())) },
            punti = listOf(
                PuntoPrevisto(44.0, 12.0, List(48) { 1f }),
                PuntoPrevisto(44.5, 12.0, List(3) { 1f }),
            ),
        )
        assertEquals(1, zoppa.a(mezzanotte.plus(Duration.ofHours(20)))!!.size)
        assertEquals(2, zoppa.a(mezzanotte.plus(Duration.ofHours(1)))!!.size)
    }
}
