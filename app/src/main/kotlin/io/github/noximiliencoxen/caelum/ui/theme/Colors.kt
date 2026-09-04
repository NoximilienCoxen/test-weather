package io.github.noximiliencoxen.caelum.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.noximiliencoxen.caelum.data.SkyState
import kotlin.math.min
import kotlin.math.pow

/**
 * Un tema solo, che segue l'ora invece del sistema.
 *
 * I due temi separati erano il problema, non la soluzione. Col chiaro il sole
 * bianco spariva nel fondo bianco, e di notte non si capiva se quella palla
 * fosse il sole o la luna. Chiaro e scuro non erano nemmeno una scelta sensata
 * da offrire: sono la stessa informazione che l'app gia' possiede, chiesta due
 * volte.
 *
 * **Il grigio unico pero' aveva risolto il contrasto buttando via il colore.**
 * Mezzogiorno era grigio per costruzione, alba e tramonto viravano su un malva
 * fangoso, e sereno e coperto avevano lo stesso identico cielo: il grigio c'era
 * sempre e quindi non diceva niente. Adesso il fondo e' un cielo vero, e riposa
 * su tre decisioni:
 *
 * - **E' una sfumatura, non una tinta.** Zenit sopra e orizzonte sotto, perche'
 *   e' li' che il colore succede: un cielo piatto e' una parete.
 * - **Alba e tramonto sono diversi.** L'altezza del sole e' simmetrica attorno
 *   a mezzogiorno e da sola non li distingue; ci pensa [SkyState.evening].
 * - **Il grigio adesso significa coperto.** Il fondo di prima e' diventato la
 *   tavolozza della nuvolosita', e ci si scivola dentro con `Wmo.cloudiness`.
 *
 * Il sole giallo e la luna bianca continuano a staccare - meglio di prima, anzi:
 * un giallo su un azzurro si vede piu' che su un grigio, che ne era il vicino di
 * luminanza.
 */
@Immutable
data class MeteoColors(
    /**
     * Il tono piatto rappresentativo del cielo: la sfumatura letta a meta'
     * altezza, un filo sotto il centro.
     *
     * Resta il colore che tutto il resto dell'app chiama "il fondo" - le tacche
     * della barra delle ore, il testo dentro la pillola, lo schema Material, le
     * icone delle barre di sistema - e non e' un residuo: ognuno di quei punti
     * ha bisogno di **un** colore, non di due, e questo e' quello giusto da
     * dare. La sfumatura la disegna solo chi dipinge lo sfondo.
     */
    val background: Color,
    /** Il cielo sopra la testa: l'estremo alto della sfumatura del fondo. */
    val skyZenith: Color,
    /** Il cielo all'orizzonte: l'estremo basso, dove il sole lo tinge. */
    val skyHorizon: Color,
    val text: Color,
    val label: Color,
    val line: Color,
    /** Faccia frontale della cifra: satinata, piatta, senza gradiente colorato. */
    val numberFace: Color,
    /** Parete piu' vicina alla faccia frontale. */
    val numberSideNear: Color,
    /** Parete piu' lontana, in fondo allo spessore. */
    val numberSideFar: Color,
    /** Smusso rivolto verso la luce. */
    val numberChamfer: Color,
    /** Quanto stacca l'ombra portata: piu' il fondo e' chiaro, piu' serve. */
    val numberShadowAlpha: Float,
    val pillBackground: Color,
    val pillText: Color,
    /** Sole: nucleo e ombra, gia' mescolati fra il giallo del giorno e il rosso radente. */
    val sunCore: Color,
    val sunShade: Color,
    val moonCore: Color,
    val moonShade: Color,
    /** Nuvola asciutta. */
    val cloudCore: Color,
    val cloudShade: Color,
    /** Nuvola carica di pioggia: e' un altro oggetto, non la stessa piu' scura. */
    val rainCloudCore: Color,
    val rainCloudShade: Color,
    val rain: Color,
    /**
     * Il mappamondo del benvenuto: mare e terre emerse.
     *
     * **Fissi, e non mescolati all'ora del giorno come tutto il resto.** Il
     * mappamondo si vede una volta sola, prima ancora di sapere che tempo fa e
     * dove: non ha un'ora a cui appartenere. E soprattutto e' l'unica cosa
     * dell'app che deve **somigliare a qualcosa di vero** - una sfera bianca con
     * macchie grigie era la luna, non la Terra, e infatti si leggeva cosi'.
     *
     * Il blu e l'ocra non sono tinte nuove: sono gli stessi della pioggia e del
     * sole, smorzati. Un mappamondo da libro di scuola.
     */
    val globeSea: Color,
    val globeSeaShade: Color,
    val globeLand: Color,
)

/** Una fascia di cielo: quello che si vede in alto e quello che si vede in basso. */
private class SkyBand(val zenith: Color, val horizon: Color)

private fun lerpBand(from: SkyBand, to: SkyBand, amount: Float) = SkyBand(
    lerp(from.zenith, to.zenith, amount),
    lerp(from.horizon, to.horizon, amount),
)

/**
 * Le fermate del cielo, ordinate per altezza del sole.
 *
 * Cadono dove cadono le soglie di `SkyState.of`: -0,62 e 0,22 per il giorno,
 * -0,14 e 0,05 per la comparsa del sole, 0,02 e 0,34 per il rosso radente.
 * Fondo, sole e luna cambiano cosi' **negli stessi punti**, che e' il patto
 * dichiarato in `SunClock`. Fermate messe altrove darebbero istanti in cui il
 * cielo e' gia' notte e il sole e' ancora giallo.
 *
 * Nero pieno in cima alla notte no, come prima: una luna bianca ci starebbe
 * sopra come un buco. E' un indaco molto scuro, che e' anche cio' che si vede
 * davvero guardando in su.
 */
private val MorningSky = listOf(
    -1.00f to SkyBand(Color(0xFF0B1020), Color(0xFF131A33)),
    -0.55f to SkyBand(Color(0xFF141B34), Color(0xFF2A2A4C)),
    -0.22f to SkyBand(Color(0xFF2B3163), Color(0xFF5E4A72)),
    -0.05f to SkyBand(Color(0xFF3E5188), Color(0xFFB4707F)),
    0.06f to SkyBand(Color(0xFF5F86B8), Color(0xFFE9A57E)),
    0.30f to SkyBand(Color(0xFF5A97CE), Color(0xFFBCD9EE)),
    1.00f to SkyBand(Color(0xFF5A9BD4), Color(0xFFC9E2F4)),
)

/**
 * La sera.
 *
 * **Notte e pieno giorno sono identici al mattino, di proposito.** La differenza
 * fra un'alba e un tramonto vive solo attorno all'orizzonte, ed e' li' che va
 * spesa: l'alba tira al rosa e resta fredda, il tramonto tira all'arancio e
 * scalda. Gli estremi uguali sono anche cio' che rende invisibile lo scambio fra
 * le due tavolozze, che cade a mezzogiorno.
 */
private val EveningSky = listOf(
    -1.00f to SkyBand(Color(0xFF0B1020), Color(0xFF131A33)),
    -0.55f to SkyBand(Color(0xFF141B34), Color(0xFF2E2440)),
    -0.22f to SkyBand(Color(0xFF2E2F60), Color(0xFF7A3F58)),
    -0.05f to SkyBand(Color(0xFF463F7C), Color(0xFFC9603F)),
    0.06f to SkyBand(Color(0xFF6383B2), Color(0xFFF0A25E)),
    0.30f to SkyBand(Color(0xFF5A97CE), Color(0xFFBCD9EE)),
    1.00f to SkyBand(Color(0xFF5A9BD4), Color(0xFFC9E2F4)),
)

/**
 * Il cielo coperto: e' il fondo grigio di prima, riciclato.
 *
 * Non e' nostalgia. Quel grigio era giusto, sbagliato era che ci fosse sempre:
 * adesso compare quando c'e' qualcosa da coprire, e a quel punto **dice**
 * qualcosa. Sotto un cielo coperto non si vede ne' azzurro ne' tramonto, e
 * l'ora si legge lo stesso perche' chiaro e scuro restano.
 */
private val OvercastDay = SkyBand(Color(0xFF9BA0A8), Color(0xFFC6CBD2))
private val OvercastNight = SkyBand(Color(0xFF1B1E24), Color(0xFF2A2E36))

/**
 * Quanto in fretta il coperto perde la luce.
 *
 * Piu' di uno, cioe' piu' in fretta del cielo sereno: sotto le nuvole non c'e'
 * il sole radente ne' il suo riverbero, e la sera arriva prima. La curva serve
 * anche a scavalcare il **grigio di mezzo**, che e' la luminanza in cui non
 * esiste un colore di testo che regga i due capi della sfumatura: misurato, un
 * coperto alle otto di sera ci si fermava dentro e il contrasto scendeva a
 * 3,65:1.
 */
private const val OVERCAST_DUSK = 1.6f

/**
 * Il cupo del temporale: non e' solo coperto, e' anche piu' buio.
 *
 * Sceso fin qui e non tenuto a mezza strada per la stessa ragione della curva:
 * un grigio intermedio non lo si sa scrivere sopra. Un temporale a mezzogiorno
 * e' scuro davvero, quindi il colore giusto e' anche quello leggibile.
 */
private val Gloom = Color(0xFF1E222A)

/** Quanto il temporale scurisce, al massimo. */
private const val GLOOM_DEPTH = 0.62f

/**
 * Il cielo a una certa altezza del sole, dentro una delle due rampe.
 *
 * Interpolazione fra la fermata precedente e la successiva, estremi bloccati:
 * la stessa forma di `temperatureTint` nel dettaglio, che e' il modo in cui in
 * questa app si legge una tabella di colori.
 */
private fun sample(stops: List<Pair<Float, SkyBand>>, altitude: Float): SkyBand {
    if (altitude <= stops.first().first) return stops.first().second
    for (index in 1 until stops.size) {
        val (edge, band) = stops[index]
        if (altitude > edge) continue
        val (previousEdge, previousBand) = stops[index - 1]
        val amount = (altitude - previousEdge) / (edge - previousEdge)
        return lerpBand(previousBand, band, amount)
    }
    return stops.last().second
}

/**
 * Quanti passi puo' cedere la sfumatura prima di arrendersi a una tinta piatta.
 */
private const val NARROW_STEPS = 6

private class LegibleSky(val band: SkyBand, val text: Color)

/**
 * La sfumatura, stretta quel tanto che serve perche' ci si possa scrivere sopra.
 *
 * C'e' un caso in cui **nessun** colore di testo regge tutti e due i capi: quando
 * uno sta sopra e l'altro sotto la luminanza di mezzo, il bianco perde in cima e
 * il nero perde in fondo, e non esiste una terza risposta. Misurato, capita per
 * il due per cento dei momenti della giornata: il parzialmente nuvoloso attorno
 * all'alba, il coperto attorno al tramonto.
 *
 * Li' e' **la sfumatura a cedere, non la leggibilita'**: si avvicina al proprio
 * tono medio finche' un testo esiste, e al limite diventa la tinta piatta di
 * prima. Cede poco - quasi sempre un passo su sei - e cede in momenti che sono
 * gia' i meno colorati della giornata, quindi non si perde niente che valesse la
 * pena tenere.
 *
 * Il tono medio non si muove mentre la sfumatura si stringe: i due capi gli si
 * avvicinano insieme. Cosi' tutto cio' che nell'app legge "il fondo" resta il
 * colore che era.
 */
private fun legibleSky(band: SkyBand, wanted: Color): LegibleSky {
    val middle = lerp(band.zenith, band.horizon, 0.55f)
    val flat = SkyBand(middle, middle)
    var best = LegibleSky(band, wanted)
    var bestWorst = -1f
    for (step in 0..NARROW_STEPS) {
        val tried = if (step == 0) band else lerpBand(band, flat, step / NARROW_STEPS.toFloat())
        val text = wanted.readableOnBoth(tried.zenith, tried.horizon)
        val worst = min(text.contrastRatio(tried.zenith), text.contrastRatio(tried.horizon))
        if (worst >= CONTRAST_AA) return LegibleSky(tried, text)
        if (worst > bestWorst) {
            bestWorst = worst
            best = LegibleSky(tried, text)
        }
    }
    return best
}

private val NightText = Color(0xFFF4F5F7)
private val DayText = Color(0xFF181B20)

private val SunYellowCore = Color(0xFFFFDE59)
private val SunYellowShade = Color(0xFFE39A0C)
private val SunRedCore = Color(0xFFFF8A4C)
private val SunRedShade = Color(0xFFC9331D)

/**
 * Colori del momento.
 *
 * @param cloudiness quanto e' coperto, da `Wmo.cloudiness`. Ha un default
 *   perche' due chiamanti un cielo non ce l'hanno: la palette di partenza qui
 *   sotto e la schermata di configurazione del widget, che mostra un giorno
 *   qualunque e non un momento.
 *
 * Non c'e' cache e non serve: e' una manciata di interpolazioni, e viene
 * ricalcolata solo quando cambia l'ora scelta.
 */
fun skyColors(sky: SkyState, cloudiness: Float = 0f): MeteoColors {
    val day = sky.dayness

    // Il cielo che ci sarebbe se non ci fosse niente in mezzo.
    val clear = lerpBand(
        sample(MorningSky, sky.altitude),
        sample(EveningSky, sky.altitude),
        sky.evening,
    )

    // Poi ci si mette il tempo che fa. La nuvolosita' porta al grigio, e il
    // solo decile alto - pioggia forte e temporale - scurisce ancora: un
    // acquazzone non e' un cielo coperto piu' chiaro, e' un cielo sotto cui si
    // accende la luce.
    val cover = cloudiness.coerceIn(0f, 1f)
    val gloom = ((cover - 0.90f) / 0.10f).coerceIn(0f, 1f) * GLOOM_DEPTH
    val overcast = lerpBand(OvercastNight, OvercastDay, day.pow(OVERCAST_DUSK))
    val painted = SkyBand(
        lerp(lerp(clear.zenith, overcast.zenith, cover), Gloom, gloom),
        lerp(lerp(clear.horizon, overcast.horizon, cover), Gloom, gloom),
    )
    // ── Il contrasto, garantito invece che sperato ──────────────────────────
    //
    // Fondo e testo si interpolano su **due scale diverse** - il fondo dalla
    // notte al giorno, il testo da bianco sporco a quasi nero - e a un certo
    // punto della giornata si incrociano. Misurato con la formula WCAG sulla
    // matematica di prima: a `day` = 0,6 il contrasto scendeva a **1,01:1**,
    // cioe' testo della stessa luminanza del fondo. Succedeva ogni giorno, per
    // un'ora buona, e non l'aveva mai visto nessuno perche' gli scatti della CI
    // coprivano le due ore estreme - che sono le due in cui il contrasto e' al
    // meglio.
    //
    // **La soglia si chiede sui due capi della sfumatura e non sul tono medio.**
    // Da quando il fondo e' un cielo, sotto il testo non c'e' piu' un colore
    // solo: in cima c'e' lo zenit, in fondo l'orizzonte, e a meta' pomeriggio
    // distano fra loro piu' di due a uno. Un testo corretto sulla media li
    // regge tutti e due appena appena, e "appena appena" e' come si torna al
    // difetto di prima con un'altra faccia.
    //
    // La correzione sta **qui e non nei chiamanti**: la schermata principale,
    // la barra delle ore, il benvenuto e la scultura leggono tutti questi
    // colori, e correggerli uno per uno vorrebbe dire dimenticarne uno.
    val legible = legibleSky(painted, lerp(NightText, DayText, day))
    val band = legible.band
    val text = legible.text
    // Il tono piatto e' la sfumatura letta un filo sotto meta' altezza, che e'
    // dove sta il grosso di cio' che ci si scrive sopra.
    val background = lerp(band.zenith, band.horizon, 0.55f)
    // L'etichetta resta un gradino sotto il testo principale, ma non scende
    // mai sotto la soglia, a nessuna delle due altezze.
    val label = text.mutedOnBoth(band.zenith, band.horizon)
    // La linea e' un segno, non una scritta: le basta la soglia del testo
    // grande, se no diventerebbe indistinguibile dall'etichetta.
    val line = lerp(text, background, 0.55f)
        .readableOnBoth(band.zenith, band.horizon, CONTRAST_AA_LARGE)

    return MeteoColors(
        background = background,
        skyZenith = band.zenith,
        skyHorizon = band.horizon,
        text = text,
        label = label,
        line = line,
        numberFace = Color(0xFFFFFFFF),
        numberSideNear = Color(0xFFE9EAEE),
        // Costante e scura: e' il lato in ombra, e deve staccare dal fondo a
        // qualunque ora, altrimenti a mezzogiorno il volume si perde.
        numberSideFar = Color(0xFF43464C),
        numberChamfer = Color(0xFFDFE1E5),
        numberShadowAlpha = 0.12f * day,
        
        pillBackground = text,
        pillText = background,
        sunCore = lerp(SunYellowCore, SunRedCore, sky.redness),
        sunShade = lerp(SunYellowShade, SunRedShade, sky.redness),
        moonCore = Color(0xFFF6F7F9),
        moonShade = Color(0xFF9AA0AA),
        cloudCore = Color(0xFFFFFFFF),
        cloudShade = Color(0xFFBFC4CC),
        rainCloudCore = Color(0xFF9BA1AB),
        rainCloudShade = Color(0xFF474C56),
        rain = Color(0xFF3C8DF5),
        globeSea = Color(0xFF8CBCE8),
        globeSeaShade = Color(0xFF2E5C92),
        globeLand = Color(0xFFCFA255),
    )
}

/**
 * Iridescenza: azzurro, rosa e giallo tenui, mai saturi. Le fasce trasparenti
 * interposte servono a spezzare il bordo, cosi' la rifrazione non gira uniforme
 * attorno a tutta la sagoma e resta entro il dieci-quindici per cento di
 * superficie.
 */
val IridescenceStops: List<Color> = listOf(
    Color(0x00FFFFFF),
    Color(0xFF9FD2E8),
    Color(0x00FFFFFF),
    Color(0xFFE3B9CE),
    Color(0x00FFFFFF),
    Color(0xFFEDE3B4),
    Color(0x00FFFFFF),
)

val LocalMeteoColors = staticCompositionLocalOf { skyColors(SkyState.Giorno) }





