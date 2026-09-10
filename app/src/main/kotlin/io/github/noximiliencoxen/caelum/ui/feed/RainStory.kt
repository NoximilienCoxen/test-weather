package io.github.noximiliencoxen.caelum.ui.feed

import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.PrecipKind
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.isWet

/**
 * Cosa dice la giornata di pioggia, in numeri e in una frase.
 *
 * **Niente Compose qui dentro.** E' la stessa scelta gia' fatta per `isWet()`,
 * portata fuori dalla scultura perche' "da qui la usano la schermata principale
 * e la barra delle ore senza dipendere da millequattrocento righe di Compose -
 * e si puo' provare". Una frase in italiano e una scala che decide dove tagliare
 * sono esattamente le due cose che si sbagliano in silenzio e che uno scatto non
 * mostra: qui si provano con JUnit, senza emulatore.
 */

// ---------------------------------------------------------------------------
// Che cosa e' caduto

/**
 * Il tipo di precipitazione di un giorno.
 *
 * [Wmo.precipKind] esisteva gia', con scritto addosso "tipologia mostrata nella
 * tabella della pagina Precip", e non la chiamava nessuno. Da sola pero' non
 * basta: **non restituisce mai [PrecipKind.MIXED]** - nessun suo ramo la
 * produce - perche' un codice giornaliero dice il fenomeno prevalente, non i
 * due insieme. Le somme lo sanno, e ci sono gia'.
 */
internal fun precipKindOf(day: DayForecast, forcedCode: Int? = null): PrecipKind {
    // Il codice imposto vince, come vince dappertutto: senza, negli scatti di
    // verifica la finestra pioveva e TIPOLOGIA diceva "--", cioe' la scheda si
    // contraddiceva dentro la stessa schermata.
    if (forcedCode != null) return Wmo.precipKind(forcedCode)
    val byCode = Wmo.precipKind(day.weatherCode)
    val rain = day.rainSum ?: 0.0
    val snow = day.snowfallSum ?: 0.0
    return when {
        byCode == PrecipKind.HAIL -> PrecipKind.HAIL
        rain > 0.0 && snow > 0.0 -> PrecipKind.MIXED
        snow > 0.0 -> PrecipKind.SNOW
        rain > 0.0 -> PrecipKind.RAIN
        // Zero millimetri con un codice bagnato esiste, ed e' la trappola #14:
        // li' il codice e' l'unico a saperlo, e vince.
        else -> byCode
    }
}

/** Vero se la fascia e i numeri vanno letti in centimetri invece che in millimetri. */
internal fun PrecipKind.isSnowy(): Boolean = this == PrecipKind.SNOW

// ---------------------------------------------------------------------------
// Le scale

/**
 * I soffitti della fascia delle 24 ore, in millimetri **in un'ora**.
 *
 * Tre gradini dichiarati, e non una scala continua che si adatta al massimo del
 * giorno. Adattiva, la fascia direbbe sempre la stessa cosa: la colonna piu'
 * alta tocca il bordo tutti i giorni, e quattro decimi di pioviggine
 * uscirebbero identici a un rovescio - due giornate diverse con lo stesso
 * disegno. Fissa a un valore solo, la pioviggine sparirebbe.
 *
 * Il diagrammino della prima scheda si normalizza sul massimo, e li' va bene:
 * e' la forma della giornata vista di sfuggita mentre si sceglie un'ora. Qui si
 * legge, e quello che si legge va scritto - il soffitto scelto sta in chiaro
 * sulla fascia.
 *
 * **E non ha niente a che vedere con il totale del giorno** che sta fra i
 * numeri in fondo: uno misura millimetri in un'ora, l'altro millimetri in un
 * giorno. Erano due righe diverse quando il totale stava dentro una vasca
 * graduata, e restano due righe diverse adesso che e' un numero. Non vanno
 * unificati.
 */
internal val BAND_STEPS_MM = doubleArrayOf(4.0, 10.0, 30.0)

/** Gli stessi gradini per la neve, in centimetri all'ora. */
internal val BAND_STEPS_CM = doubleArrayOf(1.0, 3.0, 8.0)

/**
 * Il primo gradino che contiene il massimo orario del giorno.
 *
 * Oltre l'ultimo si resta li' e la colonna viene tagliata: chi disegna lo
 * dichiara con una tacca in cima, invece di ritarare la scala. Una scala che si
 * riscala da sola non e' una scala, e due giornate affiancate smetterebbero di
 * essere confrontabili - che e' l'unica ragione per averne una.
 */
internal fun bandCeiling(peak: Double, snow: Boolean = false): Double {
    val steps = if (snow) BAND_STEPS_CM else BAND_STEPS_MM
    return steps.firstOrNull { peak <= it } ?: steps.last()
}

/**
 * Il massimo orario del giorno: **quanto forte**, nell'ora peggiore.
 *
 * E' il terzo numero della scheda della pioggia, e prende il posto di
 * `TIPOLOGIA`. La ragione e' che i tre numeri devono rispondere a tre domande
 * diverse, e `TIPOLOGIA` rispondeva a una che la scheda dice gia' due volte: le
 * colonne della neve sono bianche invece che azzurre, e la frase sopra la fascia
 * la chiama per nome. `PICCO` invece dice una cosa che nessun altro pezzo della
 * scheda dice.
 *
 * **Non e' il totale del giorno diviso qualcosa.** Quaranta millimetri
 * distribuiti su tutta la giornata e quaranta caduti in due ore sono due
 * giornate diverse con lo stesso totale: e' esattamente la domanda per cui
 * esiste la fascia delle ventiquattro ore, e qui e' scritta in cifre.
 *
 * Nulla quando la previsione non porta valori orari - un giorno oltre la portata
 * del modello ha il giorno e non le ore - e nulla quando nessuna ora ne ha uno.
 * Uno zero scritto direbbe "misurato, ed e' zero", che e' un'altra cosa.
 *
 * E' il medesimo massimo che [bandCeiling] cerca per tararsi la scala, ma non si
 * puo' leggere di li': quella torna il **gradino**, cioe' un numero tondo scelto
 * fra tre, e scriverlo fra i dati direbbe che alle sedici sono caduti dieci
 * millimetri quando ne erano caduti sei.
 */
internal fun peakPrecipitation(hours: List<HourForecast>, snow: Boolean = false): Double? =
    hours.mapNotNull { if (snow) it.snowfall else it.precipitation }.maxOrNull()

// ---------------------------------------------------------------------------
// La frase

/**
 * Il nome della cosa che cade.
 *
 * [takesAdjective] esiste per una ragione di lingua e non di disegno:
 * *"temporale moderata"* non e' italiano. Gli aggettivi di intensita' qui sotto
 * concordano al femminile perche' pioggia e neve lo sono, e un temporale e' gia'
 * un temporale - non ha bisogno di essere qualificato.
 */
internal enum class RainWord(val noun: String, val takesAdjective: Boolean) {
    PIOGGIA("pioggia", true),
    NEVE("neve", true),
    TEMPORALE("temporale", false),
}

/** Un tratto bagnato: da che ora, a che ora, quanto forte, e di che cosa. */
internal data class RainRun(val from: Int, val to: Int, val peak: Double, val word: RainWord)

/**
 * Se a quest'ora piove, e di che.
 *
 * **Il codice per primo**, i millimetri poi: e' la trappola #14. Un temporale
 * previsto all'ottanta per cento puo' avere zero millimetri in quell'ora esatta,
 * e sotto la scritta TEMPORALE non cadeva niente. I millimetri dicono quanto
 * forte, non se.
 */
private fun HourForecast.wetWord(forcedCode: Int?): RainWord? {
    val code = forcedCode ?: weatherCode
    val family = Wmo.family(code)
    if (family.isWet()) {
        return when (family) {
            Wmo.Family.TEMPORALE -> RainWord.TEMPORALE
            Wmo.Family.NEVE -> RainWord.NEVE
            else -> RainWord.PIOGGIA
        }
    }
    // Millimetri senza un codice bagnato: capita ai bordi di un fronte, e
    // ignorarli direbbe "asciutto" sopra una colonna disegnata.
    if ((precipitation ?: 0.0) > 0.0) {
        return if ((snowfall ?: 0.0) > 0.0) RainWord.NEVE else RainWord.PIOGGIA
    }
    return null
}

/**
 * I tratti bagnati della giornata, in ordine.
 *
 * [from] taglia via le ore gia' passate: su **oggi** la frase deve guardare
 * avanti, se no alle diciotto direbbe "asciutto fino alle sedici", che e' una
 * previsione del passato. Sugli altri giorni non si taglia niente.
 *
 * **Due tratti separati da una sola ora asciutta si fondono.** Senza, una
 * normale giornata di fronte si spezza in cinque o sei tratti e la frase diventa
 * un inventario invece di una risposta.
 */
internal fun rainRuns(
    hours: List<HourForecast>,
    forcedCode: Int? = null,
    from: Int? = null,
): List<RainRun> {
    val window = hours.filter { from == null || it.time.hour >= from }
    if (window.isEmpty()) return emptyList()

    val raw = mutableListOf<RainRun>()
    var open: RainRun? = null
    for (hour in window) {
        val word = hour.wetWord(forcedCode)
        val mm = hour.precipitation ?: 0.0
        val h = hour.time.hour
        val current = open
        when {
            word == null -> {
                if (current != null) raw.add(current)
                open = null
            }
            current == null || h != current.to + 1 -> {
                if (current != null) raw.add(current)
                open = RainRun(from = h, to = h, peak = mm, word = word)
            }
            else -> open = current.copy(
                to = h,
                peak = maxOf(current.peak, mm),
                word = strongerWord(current.word, word),
            )
        }
    }
    open?.let { raw.add(it) }

    val merged = mutableListOf<RainRun>()
    for (run in raw) {
        val last = merged.lastOrNull()
        // Un solo buco asciutto in mezzo vuol dire `run.from - last.to == 2`.
        if (last != null && run.from - last.to <= 2) {
            merged[merged.lastIndex] = last.copy(
                to = run.to,
                peak = maxOf(last.peak, run.peak),
                word = strongerWord(last.word, run.word),
            )
        } else {
            merged.add(run)
        }
    }
    return merged
}

/**
 * Fra due parole per la stessa cosa vince il temporale.
 *
 * Un tratto che comincia con la pioggia e finisce con un temporale e' un
 * temporale, e chiamarlo pioggia perche' e' cominciato cosi' toglierebbe
 * proprio l'unica cosa per cui vale la pena leggerlo.
 */
private fun strongerWord(a: RainWord, b: RainWord): RainWord =
    if (a == RainWord.TEMPORALE || b == RainWord.TEMPORALE) RainWord.TEMPORALE else a

/**
 * L'aggettivo dell'intensita', o **nulla** se non c'e' niente da cui ricavarlo.
 *
 * Le soglie sono la scala d'uso comune dei servizi regionali italiani: debole
 * sotto i due millimetri all'ora, moderata fino a sei, forte fino a dieci, e
 * sopra si parla di nubifragio. Non e' la scala americana (2,5 / 7,6 / 50), che
 * e' tarata sull'intensita' **istantanea** di un pluviometro: una casella oraria
 * e' gia' una media su un'ora, e li' quella scala sottostima di un gradino
 * buono. Torna anche il conto con l'app stessa - dieci millimetri all'ora per
 * quattro ore fanno i quaranta con cui `DerivedAlerts` emette una gialla.
 *
 * **Picco a zero vuol dire nessun aggettivo**, non "debole": il codice dice che
 * piove e i millimetri non dicono quanto (trappola #14), e dedurre "debole" da
 * un'assenza e' inventare un valore.
 */
internal fun intensityWord(peak: Double, snow: Boolean): String? {
    if (peak <= 0.0) return null
    val (light, medium, heavy) = if (snow) {
        Triple(DEBOLE_CMH, MODERATA_CMH, FORTE_CMH)
    } else {
        Triple(DEBOLE_MMH, MODERATA_MMH, FORTE_MMH)
    }
    return when {
        peak < light -> "debole"
        peak < medium -> "moderata"
        peak < heavy -> "forte"
        else -> "molto forte"
    }
}

private const val DEBOLE_MMH = 2.0
private const val MODERATA_MMH = 6.0
private const val FORTE_MMH = 10.0

private const val DEBOLE_CMH = 1.0
private const val MODERATA_CMH = 3.0
private const val FORTE_CMH = 8.0

/**
 * La riga in parole sopra la fascia: quando piove, in italiano.
 *
 * **Non in maiuscolo spaziato**, ed e' una decisione gia' presa altrove in
 * questo stesso file di schede: *"e' una frase intera, e una frase in maiuscolo
 * spaziato si compita invece di leggersi"*. Qui si legge.
 *
 * Torna **nulla** quando non c'e' una giornata su cui dire qualcosa - la lista
 * vuota, che non e' un caso difensivo: con un modello a corto raggio il giorno
 * esiste e le sue ore no. Chi chiama dice quello, invece di una frase sbagliata.
 */
internal fun rainSentence(
    hours: List<HourForecast>,
    forcedCode: Int? = null,
    from: Int? = null,
): String? {
    if (hours.isEmpty()) return null

    val runs = rainRuns(hours, forcedCode, from)
    val window = hours.filter { from == null || it.time.hour >= from }
    val firstHour = window.firstOrNull()?.time?.hour ?: return null
    val lastHour = window.lastOrNull()?.time?.hour ?: return null

    if (runs.isEmpty()) {
        val chance = window.mapNotNull { it.precipProbability }.maxOrNull() ?: 0
        // Asciutto **ma non escluso**: una probabilita' alta con zero millimetri
        // e' una giornata su cui il modello non se la sente, e dire solo
        // "asciutta" nasconderebbe l'unica cosa utile che sa.
        return if (chance >= CHANCE_WORTH_SAYING) {
            "Giornata asciutta, ma il modello non lo esclude ($chance%)."
        } else {
            "Giornata asciutta."
        }
    }

    val snow = runs.all { it.word == RainWord.NEVE }
    val peak = runs.maxOf { it.peak }
    val strongest = intensityWord(peak, snow)

    if (runs.size > 1) {
        val word = runs.map { it.word }.reduce(::strongerWord)
        val noun = capitalised(word.noun)
        val span = "fra le ${runs.first().from} e le ${clock(runs.last().to + 1)}"
        val tail = strongest
            ?.takeIf { word.takesAdjective }
            ?.let { ", al massimo $it" }
            .orEmpty()
        return "$noun a tratti $span$tail."
    }

    val run = runs.single()
    val adjective = strongest?.takeIf { run.word.takesAdjective }
    val noun = run.word.noun
    val withAdjective = if (adjective == null) noun else "$noun $adjective"

    return when {
        run.from <= firstHour && run.to >= lastHour ->
            "${capitalised(noun)} per tutta la giornata${strongestTail(adjective)}."

        run.from == run.to ->
            "Un'ora di $noun verso le ${run.from}."

        run.from <= firstHour ->
            "${capitalised(withAdjective)} fino alle ${clock(run.to + 1)}, poi asciutto."

        run.to >= lastHour ->
            "Asciutto fino alle ${run.from}, poi $withAdjective."

        else ->
            "${capitalised(noun)} dalle ${run.from} alle ${clock(run.to + 1)}" +
                (adjective?.let { ", $it" } ?: "") + "."
    }
}

/** "a tratti forte" e non "forte": su tutta la giornata il picco e' un momento. */
private fun strongestTail(adjective: String?): String =
    adjective?.let { ", a tratti $it" }.orEmpty()

/** Le ventiquattro non esistono: la giornata finisce a mezzanotte. */
private fun clock(hour: Int): Int = if (hour >= 24) 0 else hour

private fun capitalised(text: String): String =
    text.replaceFirstChar { it.uppercase() }

/**
 * Sotto questa probabilita' una giornata asciutta e' semplicemente asciutta.
 *
 * Trenta per cento: sotto, dirlo aggiungerebbe un dubbio a ogni giornata di
 * sole, e un avviso che compare sempre non e' un avviso.
 */
private const val CHANCE_WORTH_SAYING = 30
