package io.github.noximiliencoxen.caelum.ui.feed

import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.PrecipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ogni caso qui sotto sta su una trappola gia' pagata o su un guasto vero, non
 * sulla copertura per la copertura. Sono tutte funzioni pure: girano senza
 * emulatore, che e' la ragione per cui vivono fuori da Compose.
 */
class RainStoryTest {

    private val giorno = LocalDate.of(2026, 9, 8)

    private fun ora(
        h: Int,
        code: Int? = 0,
        mm: Double? = 0.0,
        neve: Double? = null,
        prob: Int? = null,
    ) = HourForecast(
        time = LocalDateTime.of(giorno, java.time.LocalTime.of(h, 0)),
        weatherCode = code,
        precipitation = mm,
        snowfall = neve,
        precipProbability = prob,
    )

    private fun giornata(vararg bagnate: Pair<IntRange, Pair<Int, Double>>): List<HourForecast> {
        val map = mutableMapOf<Int, Pair<Int, Double>>()
        bagnate.forEach { (range, what) -> range.forEach { map[it] = what } }
        return (0..23).map { h ->
            val wet = map[h]
            if (wet == null) ora(h) else ora(h, code = wet.first, mm = wet.second)
        }
    }

    // ── La trappola #14: il codice dice che piove, i millimetri tacciono ─────

    @Test
    fun `un codice bagnato senza millimetri fa un tratto, e nessun aggettivo`() {
        // Temporale previsto, zero millimetri in quell'ora: succede davvero, e
        // dedurre "debole" da un'assenza sarebbe inventare un valore.
        val ore = giornata(15..17 to (95 to 0.0))
        val tratti = rainRuns(ore)

        assertEquals(1, tratti.size)
        assertEquals(15, tratti.single().from)
        assertEquals(17, tratti.single().to)
        assertNull(intensityWord(tratti.single().peak, snow = false))
        assertEquals("Temporale dalle 15 alle 18.", rainSentence(ore))
    }

    @Test
    fun `il temporale non riceve mai un aggettivo`() {
        // "temporale moderata" non e' italiano: gli aggettivi concordano al
        // femminile con pioggia e neve, e un temporale e' gia' un temporale.
        val ore = giornata(14..18 to (95 to 8.0))
        val frase = rainSentence(ore)!!

        assertTrue(frase, frase.startsWith("Temporale"))
        assertTrue(frase, !frase.contains("forte") && !frase.contains("moderata"))
    }

    @Test
    fun `una pioggia con i millimetri prende il suo aggettivo`() {
        assertEquals("debole", intensityWord(1.5, snow = false))
        assertEquals("moderata", intensityWord(3.0, snow = false))
        assertEquals("forte", intensityWord(8.0, snow = false))
        assertEquals("molto forte", intensityWord(24.0, snow = false))
    }

    @Test
    fun `la neve si misura in centimetri, e con le sue soglie`() {
        // Tre centimetri all'ora di neve non sono "moderata" come tre
        // millimetri di pioggia: la scala e' un'altra, e usare la stessa
        // direbbe che una nevicata seria e' una spolverata.
        assertEquals("debole", intensityWord(0.8, snow = true))
        assertEquals("forte", intensityWord(5.0, snow = true))

        // La frase intera, e non un `contains`: cominciando con la maiuscola
        // "neve" minuscolo non ci si trova, e un controllo che cerca un pezzo
        // passa anche quando il resto e' sbagliato.
        val ore = (0..23).map { h ->
            if (h in 8..11) ora(h, code = 73, mm = 2.0, neve = 2.0) else ora(h)
        }
        assertEquals("Neve dalle 8 alle 12, moderata.", rainSentence(ore))
    }

    // ── I tratti: quando fondere e quando no ────────────────────────────────

    @Test
    fun `due tratti separati da un'ora asciutta si fondono`() {
        // Senza, una normale giornata di fronte si spezza in cinque tratti e la
        // frase diventa un inventario invece di una risposta.
        val ore = giornata(8..9 to (61 to 1.0), 11..12 to (61 to 1.0))
        val tratti = rainRuns(ore)

        assertEquals(1, tratti.size)
        assertEquals(8, tratti.single().from)
        assertEquals(12, tratti.single().to)
    }

    @Test
    fun `due tratti separati da due ore asciutte restano due`() {
        val ore = giornata(8..9 to (61 to 1.0), 12..13 to (61 to 1.0))
        val tratti = rainRuns(ore)

        assertEquals(2, tratti.size)
        assertEquals("Pioggia a tratti fra le 8 e le 14, al massimo debole.", rainSentence(ore))
    }

    @Test
    fun `il temporale vince sulla pioggia dentro lo stesso tratto`() {
        val ore = (0..23).map { h ->
            when (h) {
                14, 15 -> ora(h, code = 61, mm = 2.0)
                16, 17 -> ora(h, code = 95, mm = 9.0)
                else -> ora(h)
            }
        }
        assertEquals(RainWord.TEMPORALE, rainRuns(ore).single().word)
    }

    // ── Le forme della frase ────────────────────────────────────────────────

    @Test
    fun `giornata asciutta`() {
        assertEquals("Giornata asciutta.", rainSentence(giornata()))
    }

    @Test
    fun `asciutta ma non esclusa quando il modello se la tiene aperta`() {
        // Zero millimetri con probabilita' alta e' l'unica cosa utile che il
        // modello sa dire, e chiamarla solo "asciutta" la nasconderebbe.
        val ore = (0..23).map { ora(it, prob = if (it == 15) 48 else 10) }
        assertEquals("Giornata asciutta, ma il modello non lo esclude (48%).", rainSentence(ore))
    }

    @Test
    fun `sotto il trenta per cento una giornata asciutta e' solo asciutta`() {
        val ore = (0..23).map { ora(it, prob = 22) }
        assertEquals("Giornata asciutta.", rainSentence(ore))
    }

    @Test
    fun `un tratto che arriva a sera`() {
        val ore = giornata(16..23 to (61 to 1.0))
        assertEquals("Asciutto fino alle 16, poi pioggia debole.", rainSentence(ore))
    }

    @Test
    fun `un tratto che comincia con la giornata`() {
        val ore = giornata(0..8 to (61 to 3.0))
        assertEquals("Pioggia moderata fino alle 9, poi asciutto.", rainSentence(ore))
    }

    @Test
    fun `un tratto in mezzo alla giornata`() {
        val ore = giornata(14..18 to (61 to 3.0))
        assertEquals("Pioggia dalle 14 alle 19, moderata.", rainSentence(ore))
    }

    @Test
    fun `un tratto di un'ora sola`() {
        val ore = giornata(17..17 to (61 to 1.0))
        assertEquals("Un'ora di pioggia verso le 17.", rainSentence(ore))
    }

    @Test
    fun `tutta la giornata bagnata`() {
        val ore = giornata(0..23 to (63 to 7.0))
        assertEquals("Pioggia per tutta la giornata, a tratti forte.", rainSentence(ore))
    }

    @Test
    fun `un tratto che tocca mezzanotte non dice le ventiquattro`() {
        // Le ventiquattro non esistono: la giornata finisce a mezzanotte.
        val ore = (0..23).map { h -> if (h >= 20) ora(h, code = 61, mm = 1.0) else ora(h) }
        assertEquals("Asciutto fino alle 20, poi pioggia debole.", rainSentence(ore))
    }

    // ── Su oggi la frase guarda avanti ──────────────────────────────────────

    @Test
    fun `a tarda sera la frase non racconta la mattina`() {
        // Alle diciotto, "asciutto fino alle sedici" e' una previsione del
        // passato. Sugli altri giorni non si taglia niente.
        val ore = giornata(6..9 to (61 to 1.0), 20..22 to (61 to 1.0))

        assertEquals("Pioggia a tratti fra le 6 e le 23, al massimo debole.", rainSentence(ore))
        // Dalle diciotto resta il solo tratto della sera, e resta un tratto **in
        // mezzo**: alle ventitre e' gia' asciutto, quindi la frase lo chiude
        // invece di lasciarlo aperto fino a fine giornata.
        assertEquals("Pioggia dalle 20 alle 23, debole.", rainSentence(ore, from = 18))
    }

    @Test
    fun `da un'ora oltre la quale non piove piu' la giornata e' asciutta`() {
        val ore = giornata(6..9 to (61 to 1.0))
        assertEquals("Giornata asciutta.", rainSentence(ore, from = 18))
    }

    // ── Il caso vuoto, che e' reale ─────────────────────────────────────────

    @Test
    fun `senza ore non si inventa una frase`() {
        // Trappola #42: con un modello a corto raggio il giorno esiste e le sue
        // ore no. Meglio nessuna frase che una frase su niente.
        assertNull(rainSentence(emptyList()))
        assertEquals(emptyList<RainRun>(), rainRuns(emptyList()))
    }

    // ── Le scale ────────────────────────────────────────────────────────────

    @Test
    fun `il soffitto della fascia e' il primo gradino che contiene il giorno`() {
        // Adattiva, la fascia direbbe sempre la stessa cosa: quattro decimi di
        // pioviggine uscirebbero identici a un rovescio.
        assertEquals(4.0, bandCeiling(0.4), 0.0)
        assertEquals(4.0, bandCeiling(4.0), 0.0)
        assertEquals(10.0, bandCeiling(4.1), 0.0)
        assertEquals(30.0, bandCeiling(11.0), 0.0)
    }

    @Test
    fun `oltre l'ultimo gradino il soffitto resta li' e la colonna si taglia`() {
        assertEquals(30.0, bandCeiling(80.0), 0.0)
        assertTrue(80.0 > bandCeiling(80.0))
    }

    @Test
    fun `la neve ha i suoi gradini`() {
        assertEquals(1.0, bandCeiling(0.5, snow = true), 0.0)
        assertEquals(8.0, bandCeiling(4.0, snow = true), 0.0)
    }

    // ── La tipologia, che era scritta e non la chiamava nessuno ─────────────

    private fun day(
        code: Int? = null,
        pioggia: Double? = null,
        neve: Double? = null,
    ) = DayForecast(
        date = giorno,
        label = "OGGI",
        weatherCode = code,
        rainSum = pioggia,
        snowfallSum = neve,
    )

    @Test
    fun `mista e' lo stato che il codice da solo non raggiunge mai`() {
        // `Wmo.precipKind` non ha nessun ramo che produca MIXED: un codice
        // giornaliero dice il fenomeno prevalente, non i due insieme.
        assertEquals(PrecipKind.MIXED, precipKindOf(day(code = 71, pioggia = 3.0, neve = 2.0)))
    }

    @Test
    fun `pioggia, neve e grandine restano quelle che sono`() {
        assertEquals(PrecipKind.RAIN, precipKindOf(day(code = 63, pioggia = 4.0)))
        assertEquals(PrecipKind.SNOW, precipKindOf(day(code = 73, neve = 4.0)))
        assertEquals(PrecipKind.HAIL, precipKindOf(day(code = 96, pioggia = 4.0, neve = 1.0)))
    }

    @Test
    fun `con un codice bagnato e le somme a zero decide il codice`() {
        // Ancora la trappola #14, vista dal lato della tipologia.
        assertEquals(PrecipKind.RAIN, precipKindOf(day(code = 95)))
        assertEquals(PrecipKind.NONE, precipKindOf(day(code = 0)))
    }
}
