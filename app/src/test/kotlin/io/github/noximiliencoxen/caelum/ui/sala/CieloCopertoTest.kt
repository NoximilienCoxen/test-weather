package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.Wmo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quanto chiuso appare il cielo, e quanto restano accesi gli astri.
 *
 * **Il difetto che questa classe chiude si vedeva solo dalla finestra.** L'app
 * diceva "nuvoloso" e dipingeva un cielo grigio con tre masse di nuvola e il
 * sole quasi spento, mentre fuori la giornata era aperta. Nessun controllo
 * automatico poteva accorgersene: il codice funzionava, faceva esattamente
 * quello che c'era scritto.
 *
 * La causa era una sola e produceva tre sintomi. Il codice WMO 1 -
 * *prevalentemente sereno*, una o due ottavi di cielo - finiva fra i nuvolosi,
 * e da li' prendeva il pavimento di [coperturaMinima], che vale 0,45: con il
 * cinque per cento di nuvole vere la sala lavorava sul quarantacinque.
 *
 * Le prove sono scritte **nella forma in cui il difetto puo' tornare**: non
 * "il codice 1 fa cosi'", ma *nessun codice, fra tutti quelli che esistono,
 * puo' dire due cose diverse di se stesso*.
 */
class CieloCopertoTest {

    /** Tutti i codici WMO che l'app puo' ricevere. */
    private val codici = listOf(
        0, 1, 2, 3, 45, 48,
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82,
        71, 73, 75, 77, 85, 86,
        95, 96, 99,
    )

    // ── Il codice e la sua famiglia dicono la stessa cosa ────────────────────

    @Test
    fun `il codice 1 e' sereno, e i suoi vicini restano dove stavano`() {
        assertEquals(SalaCondition.SERENO, salaConditionOf(0))
        assertEquals(SalaCondition.SERENO, salaConditionOf(1))
        assertEquals(SalaCondition.NUVOLOSO, salaConditionOf(2))
        assertEquals(SalaCondition.NUVOLOSO, salaConditionOf(3))
        // La nebbia non ha cielo aperto, e resta fra i nuvolosi.
        assertEquals(SalaCondition.NUVOLOSO, salaConditionOf(45))
        assertEquals(SalaCondition.NUVOLOSO, salaConditionOf(48))
        assertEquals(SalaCondition.PIOGGIA, salaConditionOf(61))
        assertEquals(SalaCondition.NEVE, salaConditionOf(73))
        assertEquals(SalaCondition.TEMPORALE, salaConditionOf(95))
        assertEquals(SalaCondition.TEMPORALE_GRANDINE, salaConditionOf(96))
    }

    /**
     * **La prova che vale per tutti, non per i tre che mi sono ricordato.**
     *
     * `Wmo.family` e' la verita' sui codici, e lo dice il KDoc di
     * [salaConditionOf] a proposito della neve. Sul codice 1 le due strade si
     * erano separate lo stesso: la famiglia lo chiamava ASCIUTTO, la
     * condizione NUVOLOSO. Questa riga impedisce che succeda di nuovo a
     * qualunque codice, anche a uno aggiunto domani.
     */
    @Test
    fun `nessun codice asciutto puo' finire fra i nuvolosi`() {
        codici.filter { Wmo.family(it) == Wmo.Family.ASCIUTTO }.forEach { code ->
            assertEquals(
                "il codice $code e' ASCIUTTO per la famiglia",
                SalaCondition.SERENO,
                salaConditionOf(code),
            )
        }
    }

    @Test
    fun `da un codice asciutto non cade niente, da tutti gli altri si'`() {
        codici.forEach { code ->
            val asciutto = Wmo.family(code) == Wmo.Family.ASCIUTTO
            val nebbia = Wmo.family(code) == Wmo.Family.NEBBIA
            assertEquals(
                "codice $code",
                !asciutto && !nebbia,
                cade(salaConditionOf(code)),
            )
        }
    }

    // ── La copertura ────────────────────────────────────────────────────────

    @Test
    fun `dove non cade niente comanda la nuvolosita' vera`() {
        // Il caso del reclamo: cielo quasi aperto, e il pavimento lo copriva.
        assertEquals(0.05f, coperturaDi(SalaCondition.SERENO, 5), 0.0001f)
        assertEquals(0.10f, coperturaDi(SalaCondition.NUVOLOSO, 10), 0.0001f)
        assertEquals(0.30f, coperturaDi(SalaCondition.NUVOLOSO, 30), 0.0001f)
        // E quando il dato vero e' alto non lo si smorza: comanda lui in su
        // come in giu'.
        assertEquals(0.90f, coperturaDi(SalaCondition.NUVOLOSO, 90), 0.0001f)
    }

    /**
     * **La trappola #14, che questo giro non deve rompere.**
     *
     * Se il codice dice che piove **deve piovere**, e una pioggia che cade da
     * un cielo vuoto e' lo stesso errore delle gocce che non cadevano. Con
     * nuvolosita' vera a zero - succede, per modello o per ora imposta - il
     * pavimento e' l'unica cosa che tiene delle nuvole in scena.
     */
    @Test
    fun `dove cade qualcosa il pavimento tiene, anche a nuvolosita' zero`() {
        listOf(
            SalaCondition.PIOGGIA to 0.80f,
            SalaCondition.NEVE to 0.85f,
            SalaCondition.TEMPORALE to 0.95f,
            SalaCondition.TEMPORALE_GRANDINE to 0.95f,
        ).forEach { (condizione, minima) ->
            assertEquals("$condizione a zero", minima, coperturaDi(condizione, 0), 0.0001f)
            assertEquals("$condizione al 20%", minima, coperturaDi(condizione, 20), 0.0001f)
            // Sopra il pavimento comanda di nuovo il dato vero.
            assertEquals("$condizione al 99%", 0.99f, coperturaDi(condizione, 99), 0.0001f)
        }
    }

    /**
     * Senza nuvolosita' oraria si usa il ripiego, **per tutte le condizioni**.
     *
     * Capita davvero: i modelli a corto raggio si fermano attorno alle
     * settantadue ore, e dal quarto giorno in poi ci sono i totali del giorno
     * ma non le sue ore. Prima e dopo questo giro il numero e' lo stesso, ed e'
     * la riga che lo dimostra.
     */
    @Test
    fun `senza nuvolosita' oraria vale il ripiego del codice`() {
        SalaCondition.entries.forEach { condizione ->
            assertEquals(
                "$condizione senza dato",
                coperturaMinima(condizione),
                coperturaDi(condizione, null),
                0.0001f,
            )
        }
    }

    @Test
    fun `la copertura resta fra zero e uno per qualunque numero arrivi`() {
        SalaCondition.entries.forEach { condizione ->
            listOf(-40, 0, 55, 100, 140, 9999).forEach { letto ->
                val c = coperturaDi(condizione, letto)
                assertTrue("$condizione con $letto -> $c", c in 0f..1f)
            }
        }
    }

    // ── Il velo sugli astri ─────────────────────────────────────────────────

    /** Lo stesso conto che fa il disegno, tenuto qui per poterlo provare. */
    private fun velo(copertura: Float): Float =
        (1f - copertura * 0.45f).coerceAtLeast(0.55f)

    /**
     * **Sole e luna non si spengono mai**, ed e' la richiesta a cui questo
     * numero risponde. Il velo era `1 - copertura * 0,92`, il coefficiente piu'
     * severo dell'app - le stelle perdono al massimo il 72 per cento, gli
     * uccelli il 62 - e portava il disco all'otto per cento.
     */
    @Test
    fun `il velo non scende mai sotto il minimo`() {
        (0..100).forEach { i ->
            val v = velo(i / 100f)
            assertTrue("copertura ${i / 100f} -> $v", v >= 0.55f)
            assertTrue("copertura ${i / 100f} -> $v", v <= 1f)
        }
    }

    @Test
    fun `il velo cala col cielo, e tocca il minimo solo a copertura piena`() {
        assertEquals(1f, velo(0f), 0.0001f)
        assertEquals(0.55f, velo(1f), 0.0001f)
        // Monotono: piu' cielo chiuso, meno luce. Mai un gradino all'insu'.
        var precedente = velo(0f)
        (1..100).forEach { i ->
            val v = velo(i / 100f)
            assertTrue("risale a ${i / 100f}", v <= precedente + 0.0001f)
            precedente = v
        }
    }

    // ── La scena intera, come la vede la Shell ──────────────────────────────

    @Test
    fun `una giornata quasi serena non chiude il cielo`() {
        val scena = scenaDi(code = 1, cloudCover = 10)
        assertEquals(0.10f, scena.copertura, 0.0001f)
        assertEquals(0f, scena.bagnato, 0.0001f)
        assertEquals(0f, scena.tempesta, 0.0001f)
        // Il cielo sta sulla scala 0..4 di `livelloCielo`: con il dieci per
        // cento resta praticamente aperto. Prima valeva 1,35.
        assertEquals(0.30f, livelloCielo(scena.copertura, scena.tempesta), 0.0001f)
    }

    @Test
    fun `un rovescio chiude il cielo anche se la nuvolosita' oraria dice zero`() {
        val scena = scenaDi(code = 61, cloudCover = 0)
        assertEquals(0.80f, scena.copertura, 0.0001f)
        assertTrue("deve bagnare", scena.bagnato > 0f)
    }

    private fun scenaDi(code: Int, cloudCover: Int?): Scena = scenaBersaglio(
        sky = SkyState.of(altitude = 0.6f),
        condition = salaConditionOf(code),
        nevicaWmo = Wmo.family(code) == Wmo.Family.NEVE,
        coperturaOraria = cloudCover,
        pioggiaMm = 1.0,
    )
}
