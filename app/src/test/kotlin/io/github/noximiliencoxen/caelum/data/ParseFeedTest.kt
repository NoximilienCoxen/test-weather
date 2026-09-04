package io.github.noximiliencoxen.caelum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.OffsetDateTime

/**
 * Il parser del feed MeteoAlarm, contro una risposta vera.
 *
 * `src/test/resources/meteoalarm-italia.xml` e' una cattura del feed italiano
 * ripresa da `ci-artifacts/api/allerte.xml`, non un file scritto a mano su
 * quello che il formato dovrebbe essere. La differenza non e' teorica: **la
 * prima stesura del parser era scritta su `awareness_level` e
 * `awareness_type`**, che sono i campi descritti dalla documentazione di terze
 * parti e che nel feed vero non esistono - zero occorrenze su trentacinquemila
 * byte. Ogni allerta sarebbe uscita come una gialla generica, senza un errore
 * da nessuna parte.
 *
 * Robolectric serve per una ragione sola: `parseFeed` passa da
 * `android.util.Xml`, che su una JVM non c'e'. Riscrivere il parser su SAX per
 * evitarlo sarebbe stato rifare da capo del codice gia' pagato caro contro
 * questa stessa risposta, e un test non vale quel rischio.
 */
@RunWith(RobolectricTestRunner::class)
class ParseFeedTest {

    private fun feed(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("meteoalarm-italia.xml"))
            .bufferedReader().readText()

    private fun entries() = parseFeed(feed())

    @Test
    fun `legge tutte le voci del feed`() {
        assertEquals(27, entries().size)
    }

    @Test
    fun `nessuna voce esce senza identificativo`() {
        // L'identificativo e' cio' con cui si evita di mostrare due volte la
        // stessa cosa, e cio' su cui si ricorda una fascia chiusa.
        assertTrue(entries().all { it.id.isNotBlank() })
    }

    @Test
    fun `il colore si legge dentro la frase inglese di cap-event`() {
        // "Yellow High-temperature Warning": il colore e' la prima parola.
        // `cap:severity` accanto e' piu' grossolana - nella cattura tutte e
        // ventisette le voci dicono `Moderate`, comprese le gialle - quindi
        // fidarsi di quella avrebbe appiattito tutto su un gradino solo.
        val livelli = entries().mapNotNull { it.level }.toSet()
        assertTrue("nessun livello riconosciuto: il parser guarda il campo sbagliato", livelli.isNotEmpty())
    }

    @Test
    fun `riconosce il tipo di fenomeno e non lo lascia tutto su ALTRO`() {
        val tipi = entries().map { it.kind }.toSet()
        assertTrue("i tipi non vengono riconosciuti: $tipi", tipi.any { it != AlertKind.ALTRO })
    }

    @Test
    fun `area e scadenza ci sono`() {
        val e = entries().first()
        assertNotNull("cap:areaDesc non viene letto", e.areaDesc)
        assertNotNull("cap:expires non viene letto", e.expires)
    }

    @Test
    fun `il collegamento al documento CAP e' un attributo, non testo`() {
        assertTrue(entries().any { it.capUrl?.contains("cap") == true })
    }

    @Test
    fun `una voce scaduta non risulta in corso`() {
        val e = entries().first { it.expires != null }
        val dopo = e.expires!!.plusDays(1)
        assertTrue(e.isCurrent(e.expires!!.minusDays(1)))
        assertTrue(!e.isCurrent(dopo))
    }

    @Test
    fun `un feed vuoto non fa esplodere niente`() {
        assertEquals(emptyList<FeedEntry>(), parseFeed("<feed></feed>"))
    }

    @Test
    fun `le voci si trasformano in allerte gia' in italiano`() {
        // Il feed scrive "Yellow High-temperature Warning": messo in cima a una
        // schermata italiana sarebbe la traduzione mancante piu' visibile
        // dell'app. Il titolo si compone, non si copia.
        val alert = entries().first().toAlert(null)
        assertTrue("il titolo non e' in italiano: ${alert.headline}", alert.headline.contains("ALLERTA"))
        assertTrue(alert.official)
    }

    @Test
    fun `il verde non diventa un'allerta`() {
        // Il verde vuol dire "nessun avviso": una fascia che comparisse per dire
        // che non succede niente insegnerebbe a ignorare la fascia.
        val verde = FeedEntry(
            id = "v", event = "Green Wind Warning", severity = "Minor",
            areaDesc = "Puglia", onset = null,
            expires = OffsetDateTime.now().plusDays(1), capUrl = null,
        )
        assertEquals(null, verde.level)
    }
}
