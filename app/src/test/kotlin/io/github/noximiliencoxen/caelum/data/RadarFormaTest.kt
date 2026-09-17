package io.github.noximiliencoxen.caelum.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Il lettore che riconosce le forme, provato sulle forme che potrebbe trovare.
 *
 * **Questo test non dimostra che il radar funziona**, e non puo': il servizio
 * del DPC da qui risponde 403 (CONTESTO 13-septies), e nessuno in questo
 * progetto ha mai visto una sua risposta. Quello che dimostra e' piu' piccolo e
 * piu' onesto: che il lettore riconosce l'istante, l'immagine e il riquadro
 * **comunque si chiamino i campi**, e che non li riconosce dove non ci sono.
 *
 * Le risposte qui sotto sono inventate di proposito in tre dialetti diversi -
 * nomi inglesi, nomi italiani, nomi che non vogliono dire niente - perche' e'
 * esattamente l'incertezza contro cui il lettore e' stato scritto. Se un giorno
 * si sapra' com'e' fatta davvero, il posto in cui aggiungere il caso vero e'
 * questo.
 */
class RadarFormaTest {

    private fun albero(testo: String) = Json.parseToJsonElement(testo)

    private val pngFinto: String = Base64.getEncoder().encodeToString(
        // La firma di un PNG, e poi zavorra fino a superare la soglia oltre la
        // quale una stringa vale la pena di essere decodificata.
        byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) + ByteArray(600) { 7 },
    )

    // ── l'istante ───────────────────────────────────────────────────────────

    @Test
    fun `riconosce un istante in millesimi comunque si chiami il campo`() {
        val ms = 1_789_625_316_000L
        listOf(
            """{"lastProductDate":$ms}""",
            """{"time":$ms,"productType":"VMI"}""",
            """{"xyz":{"quando":$ms}}""",
        ).forEach { corpo ->
            assertTrue(corpo, RadarForma.cercaEpoca(albero(corpo)) == ms)
        }
    }

    @Test
    fun `un istante in secondi diventa millesimi`() {
        val s = 1_789_625_316L
        assertTrue(RadarForma.cercaEpoca(albero("""{"t":$s}""")) == s * 1000L)
    }

    @Test
    fun `fra piu istanti prende il piu recente`() {
        val corpo = """{"da":1700000000000,"a":1789625316000,"altro":1750000000000}"""
        assertTrue(RadarForma.cercaEpoca(albero(corpo)) == 1_789_625_316_000L)
    }

    @Test
    fun `numeri che non sono date non diventano date`() {
        // Identificativi, conteggi, dimensioni: tutto cio' che un servizio
        // mette accanto a una data e che non e' una data.
        assertNull(RadarForma.cercaEpoca(albero("""{"id":42,"n":1024,"byte":2801}""")))
    }

    @Test
    fun `la pagina d'errore del DPC non contiene nessun istante`() {
        // E' la risposta vera che la sonda ha ricevuto nove volte su nove.
        // Se un giorno il lettore ci pescasse dentro un numero, la mappa si
        // disegnerebbe sopra il nulla.
        val negata = """<HTML><HEAD> <TITLE>Access Denied</TITLE> </HEAD></HTML>"""
        assertNull(runCatching { RadarForma.cercaEpoca(albero(negata)) }.getOrNull())
    }

    // ── l'immagine ──────────────────────────────────────────────────────────

    @Test
    fun `riconosce un png dalla firma, col prefisso data o senza`() {
        listOf(
            """{"radarProduct":"$pngFinto"}""",
            """{"immagine":"data:image/png;base64,$pngFinto"}""",
            """{"a":{"b":["$pngFinto"]}}""",
        ).forEach { corpo ->
            val byte = RadarForma.cercaImmagine(albero(corpo))
            assertNotNull(corpo, byte)
            assertTrue(corpo, byte!![0] == (-119).toByte())
        }
    }

    @Test
    fun `una stringa lunga che non e' un png non passa per un png`() {
        val rumore = "z".repeat(900)
        assertNull(RadarForma.cercaImmagine(albero("""{"nota":"$rumore"}""")))
    }

    // ── il riquadro ─────────────────────────────────────────────────────────

    @Test
    fun `riconosce un riquadro in quattro numeri e in due coppie`() {
        val piatto = RadarForma.cercaBbox(albero("""{"bbox":[35.0,4.0,48.0,21.0]}"""))
        val coppie = RadarForma.cercaBbox(albero("""{"limiti":[[35.0,4.0],[48.0,21.0]]}"""))
        listOf(piatto, coppie).forEach { r ->
            assertNotNull(r)
            assertEquals(35.0, r!!.latMin, 0.001)
            assertEquals(21.0, r.lonMax, 0.001)
        }
    }

    @Test
    fun `accetta anche l'ordine longitudine prima`() {
        // C'e' chi scrive lat,lon e chi lon,lat, e nessuno dei due lo dichiara.
        val r = RadarForma.cercaBbox(albero("""{"bbox":[4.0,35.0,21.0,48.0]}"""))
        assertNotNull(r)
        assertEquals(35.0, r!!.latMin, 0.001)
        assertEquals(21.0, r.lonMax, 0.001)
    }

    @Test
    fun `quattro numeri qualsiasi non sono un riquadro`() {
        // E' il caso che conta davvero: prendere per riquadro un array di
        // soglie, di colori o di dimensioni sposterebbe la pioggia di
        // centinaia di chilometri senza che nessuno se ne accorga.
        listOf(
            """{"soglie":[0.5,2.0,4.0,8.0]}""",
            """{"colore":[255.0,128.0,0.0,1.0]}""",
            """{"misura":[1400.0,1200.0,96.0,96.0]}""",
        ).forEach { corpo ->
            assertNull(corpo, RadarForma.cercaBbox(albero(corpo)))
        }
    }

    @Test
    fun `un riquadro troppo piccolo per essere un dominio non passa`() {
        // Un rettangolo di mezzo grado sara' un ritaglio, una cella, un
        // qualcosa: non il dominio di un radar nazionale.
        assertNull(RadarForma.cercaBbox(albero("""{"bbox":[44.0,12.0,44.4,12.4]}""")))
    }

    // ── il riassunto che finisce sullo schermo ──────────────────────────────

    @Test
    fun `il riassunto elenca le chiavi quando il corpo e' un oggetto`() {
        assertEquals("chiavi[tipo,quando]", RadarForma.riassunto("""{"tipo":"VMI","quando":1}"""))
    }

    @Test
    fun `di una pagina d'errore resta solo la frase che conta`() {
        // Non e' un vezzo: riversata per intero sotto la carta, questa pagina
        // occupava dieci righe di markup e spingeva meta' sala fuori schermo.
        val r = RadarForma.riassunto(
            "<HTML><HEAD> <TITLE>Access Denied</TITLE> </HEAD>" +
                "<BODY> <H1>Access Denied</H1> You don't have permission</BODY></HTML>",
        )
        assertEquals("Access Denied", r)
    }

    @Test
    fun `il riassunto non va mai a capo e non supera la riga`() {
        val r = RadarForma.riassunto("prima\n  seconda   terza " + "x".repeat(400))
        assertTrue(r, !r.contains("\n"))
        assertTrue(r, r.length <= 120)
    }
}
