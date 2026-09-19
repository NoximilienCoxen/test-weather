package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.SunClock
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA_LARGE
import io.github.noximiliencoxen.caelum.ui.theme.readableOn

/**
 * La tavolozza di Sala, rifatta sul sistema **Organic**.
 *
 * Sostituisce la carta acquerello e i token Broadsheet (ciano/magenta/giallo di
 * quadricromia) con cio' che il prototipo `Caelum.dc.html` chiama per nome: un
 * cielo a sfumatura che cambia con l'ora e col tempo, colline morbide in fondo,
 * pannelli a grande raggio appoggiati sopra, e un accento terracotta al posto
 * del ciano.
 *
 * **Il cielo e' tornato a essere un cielo.** La versione precedente lo aveva
 * sostituito con una pagina che scuriva a scatti, perche' quella direzione
 * (acquerello) lo chiedeva. Questa no: qui il fondo *e'* il tempo, e tutto il
 * resto galleggia sopra.
 *
 * Due caratteri, come nel sistema: **Caprasimo** per i titoli e i numeri
 * grandi, **Figtree** per tutto il resto.
 */
private val Caprasimo = FontFamily(Font(R.font.caprasimo_400, FontWeight.Normal))

private val Figtree = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
    Font(R.font.figtree_700, FontWeight.Bold),
)

private fun hex(v: Long) = Color(0xFF000000L or v)

/** I token del sistema Organic, copiati dal suo `styles.css`. */
object SalaTokens {
    val bg = Color(0xFFF5EAD8)
    val surface = Color(0xFFEBDDC5)
    val text = Color(0xFF201E1D)

    val neutral100 = Color(0xFFF9F4ED)
    val neutral200 = Color(0xFFEEE7DB)
    val neutral300 = Color(0xFFDCD3C4)
    val neutral400 = Color(0xFFC0B6A5)
    val neutral500 = Color(0xFFA19786)
    val neutral600 = Color(0xFF82796A)
    val neutral700 = Color(0xFF645C50)
    val neutral800 = Color(0xFF474238)
    val neutral900 = Color(0xFF2E2B25)

    val accent100 = Color(0xFFFFF2EB)
    val accent200 = Color(0xFFFFE1D0)
    val accent300 = Color(0xFFFFC6A5)
    val accent400 = Color(0xFFF6A06B)
    val accent500 = Color(0xFFD67F48)
    val accent600 = Color(0xFFB2622D)
    val accent700 = Color(0xFF8C491A)
    val accent800 = Color(0xFF643312)
    val accent900 = Color(0xFF402310)

    val verde200 = Color(0xFFE1EECC)
    val verde300 = Color(0xFFCCDBB2)
    val verde400 = Color(0xFFAEBF92)
    val verde500 = Color(0xFF8FA073)
    val verde600 = Color(0xFF728157)
    val verde700 = Color(0xFF56633F)
    val verde900 = Color(0xFF272E1B)

    /**
     * Il blu non e' un colore di Organic, e serviva lo stesso: pioggia, chicchi
     * e fiocchi non si raccontano in terracotta. Sono le stesse tinte scelte a
     * mano nel prototipo, tenute qui perche' **si sappia che sono un'aggiunta**
     * e non token del sistema.
     */
    val acqua = Color(0xFF5B8AA5)
    val acquaChiara = Color(0xFF7EA7BD)
    val acquaScura = Color(0xFF4F7D97)
    val ghiaccioChiaro = Color(0xFFDCECF4)
    val ghiaccioScuro = Color(0xFF31607D)

    /**
     * La luna ha colori suoi e li tiene nei due temi: un corpo celeste non ha
     * il colore dell'inchiostro della pagina che lo mostra. Lezione della
     * passata precedente, e vale identica sul cielo nuovo.
     */
    val lunaLuce = Color(0xFFF9F4ED)
    val lunaMezzo = Color(0xFFE2D9C7)
    val lunaBordo = Color(0xFF8A8376)
    val lunaOmbra = Color(0xFF080C14)
}

/** Le quattro ore della giornata su cui e' costruita la tavolozza di Sala. */
enum class SalaPhase { ALBA, GIORNO, TRAMONTO, NOTTE }

/**
 * Il tempo, ridotto alle sei famiglie che la tavolozza distingue.
 *
 * **C'era `GRANDINE` e adesso c'e' `NEVE`, e non e' un rinominare.** La
 * grandine da sola non esiste nei codici WMO che questa app riceve: gli unici
 * due che la nominano sono 96 e 99, e tutti e due dicono *temporale con
 * grandine*, cioe' [TEMPORALE_GRANDINE]. Quello che finiva in `GRANDINE` erano
 * i granuli di neve (77) e i rovesci di neve (85, 86) - neve, non ghiaccio -
 * mentre la neve vera e propria (71, 73, 75) finiva in [PIOGGIA] e sopra il
 * primo fiocco si leggeva **"Pioggia nella notte"**.
 *
 * Il difetto non era solo di parole: la scena chiedeva il ghiaccio all'enum e
 * la neve alla famiglia WMO, quindi su 85 e 86 cadevano **tutte e due insieme**
 * - chicchi e fiocchi nello stesso cielo, dallo stesso codice.
 */
enum class SalaCondition { SERENO, NUVOLOSO, PIOGGIA, NEVE, TEMPORALE, TEMPORALE_GRANDINE }

/**
 * La fase del giorno da cui parte la tavolozza, ricavata dal cielo vero.
 *
 * Non e' l'orologio a deciderla come nel prototipo, ma l'altezza reale del
 * sole nella localita' mostrata: gli stessi confini che [SkyState.of] gia'
 * usa per far comparire sole e luna, cosi' il cielo cambia tinta esattamente
 * quando cambiano loro.
 */
fun salaPhaseOf(sky: SkyState): SalaPhase = when {
    sky.altitude <= -0.42f -> SalaPhase.NOTTE
    sky.altitude >= 0.22f -> SalaPhase.GIORNO
    sky.evening < 0.5f -> SalaPhase.ALBA
    else -> SalaPhase.TRAMONTO
}

/** Le due fasi che si stanno attraversando, e quanto si e' avanti fra loro. */
@Immutable
data class FaseContinua(val da: SalaPhase, val a: SalaPhase, val avanzamento: Float)

/**
 * La fase del giorno **senza scalini**.
 *
 * [salaPhaseOf] sceglie una delle quattro e butta via il resto: alle cinque e
 * mezza il cielo e' mezza alba, e il fondo lo diceva "notte" fino a un istante
 * prima e "alba" un istante dopo. Qui la stessa informazione resta intera - due
 * fasi e quanto si e' fra loro - e chi disegna mescola.
 *
 * **Non serve una molla per questo, e la differenza conta.** Una molla
 * mostrerebbe i valori di mezzo solo *mentre* passa: fermandosi alle cinque e
 * mezza col cursore delle ore si tornerebbe a una delle quattro caselle. Qui a
 * meta' strada ci si puo' **stare**, perche' meta' alba non e' una transizione
 * verso qualcosa, e' che ore sono.
 *
 * ### L'avvolgimento non ha cuciture, ed e' dimostrabile
 *
 * Le giunture del giro NOTTE -> ALBA -> GIORNO -> TRAMONTO -> NOTTE sono due.
 *
 * A **mezzanotte** entrambe le fasi sono NOTTE e l'avanzamento e' zero: non c'e'
 * niente da attraversare.
 *
 * Al **mezzogiorno solare** il crepuscolo nominato cambia da ALBA a TRAMONTO,
 * perche' `evening` scavalca la meta'. Ma `SunClock.eveningness` e'
 * `smoothstep(0,42 -> 0,58)` sulla frazione di giornata, quindi attraversa la
 * meta' a frazione 0,5 - cioe' **esattamente al mezzogiorno solare**, dove
 * `SunClock.altitude` vale 1,0. Li' tutti e due gli smoothstep qui sotto sono
 * saturi, l'avanzamento vale 1 e la miscela e' cento per cento GIORNO
 * **qualunque** crepuscolo sia nominato.
 */
fun faseContinua(sky: SkyState): FaseContinua {
    val crepuscolo = if (sky.evening >= 0.5f) SalaPhase.TRAMONTO else SalaPhase.ALBA
    // 0 = notte piena, 1 = crepuscolo pieno, 2 = giorno pieno. Le due fasce non
    // si sovrappongono, quindi la somma cresce con l'altezza del sole senza
    // tornare indietro, e le due fasi scelte sono sempre adiacenti nel giro.
    val salita = SunClock.smoothstep(-0.62f, -0.22f, sky.altitude) +
        SunClock.smoothstep(0.02f, 0.34f, sky.altitude)
    return if (salita <= 1f) {
        FaseContinua(SalaPhase.NOTTE, crepuscolo, salita)
    } else {
        FaseContinua(crepuscolo, SalaPhase.GIORNO, salita - 1f)
    }
}

/**
 * Quanto si e' dentro un crepuscolo, da 0 a 1.
 *
 * Serve ai pannelli: nel prototipo alba e tramonto hanno un fondo **caldo**
 * (`#2b2126`) mentre notte e giorno chiuso ce l'hanno freddo (`#141c28`). E'
 * l'unica cosa per cui la fase conta ancora dopo che il cielo ha preso la sua
 * sfumatura, e mescolarla evita che i pannelli scattino di tinta mentre il
 * cielo dietro scorre liscio.
 */
fun crepuscolezza(fase: FaseContinua): Float {
    fun peso(p: SalaPhase) = if (p == SalaPhase.ALBA || p == SalaPhase.TRAMONTO) 1f else 0f
    return lerp(peso(fase.da), peso(fase.a), fase.avanzamento)
}

/**
 * Il codice WMO ridotto alle sei famiglie della tavolozza.
 *
 * **La neve la riconosce [Wmo.family] e non un elenco di numeri scritto qui.**
 * I due elenchi c'erano davvero, ed erano diversi: questo metteva 77, 85 e 86
 * fra la grandine, quello metteva 71, 73, 75, 77, 85 e 86 fra la neve. Da due
 * verita' sullo stesso codice nasceva un cielo in cui cadevano chicchi e
 * fiocchi insieme, e una didascalia che chiamava pioggia una nevicata.
 *
 * Una sola verita', e sta dove stanno i codici.
 */
fun salaConditionOf(code: Int?): SalaCondition = when {
    code == null -> SalaCondition.SERENO
    code == 96 || code == 99 -> SalaCondition.TEMPORALE_GRANDINE
    code == 95 -> SalaCondition.TEMPORALE
    Wmo.family(code) == Wmo.Family.NEVE -> SalaCondition.NEVE
    code >= 51 -> SalaCondition.PIOGGIA
    code >= 1 -> SalaCondition.NUVOLOSO
    else -> SalaCondition.SERENO
}

// -- Il cielo ---------------------------------------------------------------
//
// Le cinque colonne sono i cinque gradi di chiusura del prototipo (`liv`), da
// cielo aperto a fronte di temporale; le tre righe di ogni cella sono le
// fermate della sfumatura, in alto, a un terzo e in fondo. La tabella e' il
// porting uno a uno di `SKY` in `Caelum.dc.html`: e' tarata a mano, colonna per
// colonna, e non va "semplificata" in una formula.

private val CieloNotte = listOf(
    listOf(hex(0x0D1420), hex(0x172032), hex(0x262A38)),
    listOf(hex(0x101726), hex(0x1B2437), hex(0x2B2F3D)),
    listOf(hex(0x161B26), hex(0x222834), hex(0x34363E)),
    listOf(hex(0x191D24), hex(0x272B33), hex(0x3A3A3C)),
    listOf(hex(0x14171D), hex(0x1F232A), hex(0x2E3033)),
)

private val CieloAlba = listOf(
    listOf(hex(0x4A4566), hex(0xC9703F), hex(0xF8CDA3)),
    listOf(hex(0x514C6A), hex(0xC4744A), hex(0xF2C7A3)),
    listOf(hex(0x5B586E), hex(0xB4785A), hex(0xE2C4AB)),
    listOf(hex(0x5C5C66), hex(0x8E7B74), hex(0xCDC3B7)),
    listOf(hex(0x3F424D), hex(0x5C5A5E), hex(0x8F8A84)),
)

private val CieloGiorno = listOf(
    listOf(hex(0x6FB8E0), hex(0x9CCEE8), hex(0xF2E4CD)),
    listOf(hex(0x7CBEE2), hex(0xAED4E4), hex(0xEEE7DB)),
    listOf(hex(0x93A7B4), hex(0xB3BCC0), hex(0xDCD8CD)),
    listOf(hex(0x8A9099), hex(0xA2A5A4), hex(0xCAC6BD)),
    listOf(hex(0x4A525E), hex(0x666B71), hex(0x8E8D88)),
)

private val CieloTramonto = listOf(
    listOf(hex(0x5A4A6B), hex(0xD67F48), hex(0xFFC6A5)),
    listOf(hex(0x5F5170), hex(0xCD7F52), hex(0xF6C3A4)),
    listOf(hex(0x5E5A68), hex(0xA97F66), hex(0xDCC3AE)),
    listOf(hex(0x55555E), hex(0x827A75), hex(0xBDB4AA)),
    listOf(hex(0x3A3D47), hex(0x565459), hex(0x857F7C)),
)

/** Il cielo della neve non ha gradi: nevica, e il cielo e' quello. */
private val CieloNeve: Map<SalaPhase, List<Color>> = mapOf(
    SalaPhase.NOTTE to listOf(hex(0x1B2330), hex(0x2A3240), hex(0x3F444C)),
    SalaPhase.ALBA to listOf(hex(0x5A5A6A), hex(0x8F8484), hex(0xCFC6BD)),
    SalaPhase.GIORNO to listOf(hex(0xB9C4CC), hex(0xCDD3D4), hex(0xE8E4DB)),
    SalaPhase.TRAMONTO to listOf(hex(0x5C5560), hex(0x93837E), hex(0xCCBFB4)),
)

private fun tavolaDi(fase: SalaPhase): List<List<Color>> = when (fase) {
    SalaPhase.NOTTE -> CieloNotte
    SalaPhase.ALBA -> CieloAlba
    SalaPhase.GIORNO -> CieloGiorno
    SalaPhase.TRAMONTO -> CieloTramonto
}

/**
 * Quanto e' chiuso il cielo, sulla scala a cinque gradini del prototipo, ma
 * **continua**.
 *
 * Il prototipo ha un selettore con nove voci e ogni voce porta il suo `liv`
 * intero: passando da "poco coperto" a "coperto" il cielo cambiava in un
 * fotogramma. Qui i due estremi sono gli stessi, ma in mezzo si passa - la
 * nuvolosita' oraria vera e' un numero da 0 a 100, e buttarla in cinque
 * caselle era proprio il passaggio che `scenaBersaglio` ha smesso di fare per
 * le nuvole.
 *
 * Il fronte del temporale resta uno scalino dichiarato: un temporale e' un
 * fronte, non una nuvolosita' piu' alta, e a meta' strada fra "chiuso" e
 * "temporale" non c'e' nessun cielo vero da mostrare.
 */
fun livelloCielo(copertura: Float, tempesta: Float): Float =
    lerp(copertura.coerceIn(0f, 1f) * 3f, 4f, tempesta.coerceIn(0f, 1f))

/** Le tre fermate del cielo per una fase sola, al livello di chiusura dato. */
private fun stopsDiFase(fase: SalaPhase, livello: Float, neve: Float): List<Color> {
    val tavola = tavolaDi(fase)
    val l = livello.coerceIn(0f, (tavola.size - 1).toFloat())
    val basso = l.toInt().coerceAtMost(tavola.size - 2)
    val t = l - basso
    val sotto = tavola[basso]
    val sopra = tavola[basso + 1]
    val nevoso = CieloNeve.getValue(fase)
    return List(3) { i -> lerp(lerp(sotto[i], sopra[i], t), nevoso[i], neve.coerceIn(0f, 1f)) }
}

/**
 * Le tre fermate del cielo per una fase **a meta' strada**.
 *
 * Si mescolano i **risultati** delle due tabelle, non le tabelle: quelle sono il
 * porting uno a uno del prototipo, tarate a mano, e restano intatte. Il `lerp`
 * di Compose passa per Oklab, quindi i valori di mezzo non ingrigiscono come
 * farebbe una media sui canali.
 */
fun cieloStops(fase: FaseContinua, livello: Float, neve: Float = 0f): List<Color> {
    if (fase.avanzamento <= 0f) return stopsDiFase(fase.da, livello, neve)
    if (fase.avanzamento >= 1f) return stopsDiFase(fase.a, livello, neve)
    val a = stopsDiFase(fase.da, livello, neve)
    val b = stopsDiFase(fase.a, livello, neve)
    return List(3) { lerp(a[it], b[it], fase.avanzamento) }
}

/**
 * La sfumatura del cielo, con le fermate del prototipo: in cima, a un terzo, a
 * tre quarti, e poi ferma fino in fondo perche' sotto ci sono le colline.
 */
fun cieloBrush(stops: List<Color>): Brush = Brush.verticalGradient(
    0.00f to stops[0],
    0.34f to stops[1],
    0.74f to stops[2],
    1.00f to stops[2],
)

/**
 * Quanto e' scuro il tema, da 0 (chiaro) a 1 (scuro pieno).
 *
 * **Lo scalino non e' un difetto di resa: e' una garanzia sull'insieme degli
 * stati raggiungibili.** Nessun valore del cielo porta l'interfaccia nella
 * fascia di mezzo in cui ne' l'inchiostro scuro ne' quello chiaro reggono il
 * fondo. Appianarlo per renderlo continuo butterebbe via la garanzia - e con
 * una barra delle ore su cui ci si puo' **parcheggiare** alle cinque e mezza,
 * "brevemente illeggibile" diventerebbe "illeggibile finche' non ci si sposta".
 * La cura non e' appianare lo scalino, e' **attraversarlo nel tempo**: il
 * bersaglio della molla sta sempre fuori dalla fascia, quindi la fascia si
 * attraversa e non si abita mai.
 *
 * **Il guidatore e' cambiato col cielo nuovo, e la forma no.** Prima era
 * `dayness`, che si accende molto prima che il sole spunti: sul fondo di carta
 * andava bene, ma il cielo dell'alba del prototipo e' un viola scuro con una
 * fascia arancione, e li' l'inchiostro scuro sparisce. Adesso guida quanto e'
 * **giorno pieno** - lo stesso smoothstep che decide la fase GIORNO in
 * [faseContinua] - cosi' alba e tramonto stanno col tema scuro, come nel
 * prototipo (`chiaroScuro`).
 */
fun temaScuro(sky: SkyState): Float {
    val giorno = SunClock.smoothstep(0.02f, 0.34f, sky.altitude)
    val t = (1f - giorno).coerceIn(0f, 1f)
    return if (t < 0.42f) t * 0.5f else 0.62f + (t - 0.42f) * 0.655f
}

/**
 * Il temporale in pieno giorno vuole il tema scuro, e non e' un vezzo: sotto
 * un fronte il cielo del prototipo (`liv` 4) e' plumbeo a ogni ora, e
 * l'inchiostro scuro ci si perde.
 *
 * E' una soglia netta e non una rampa, apposta: **un bersaglio a meta' strada
 * cadrebbe dentro la fascia illeggibile** che lo scalino di [temaScuro] esiste
 * per saltare. Da soglia, la molla la attraversa nel tempo come tutte le altre.
 */
fun temaScuroPerTempesta(condition: SalaCondition): Boolean = when (condition) {
    SalaCondition.TEMPORALE, SalaCondition.TEMPORALE_GRANDINE -> true
    // **La neve non e' piu' qui dentro, e ci stava per sbaglio.** Ci stava
    // perche' era etichettata "grandine", e una cella di grandine porta con se'
    // il buio del fronte che la fa. Una nevicata e' il contrario: e' la
    // giornata piu' **chiara** dell'anno, perche' il bianco che sta per terra
    // rimanda su tutta la luce che riceve. Il cielo della neve, in tavola, e'
    // infatti quello piu' chiaro di tutti.
    else -> false
}

/**
 * Le tinte dell'interfaccia: inchiostro, accento, pannelli, colline.
 *
 * Tutto quanto sta **sopra** il cielo e non dentro. Ogni campo e' gia' il
 * risultato di numeri animati, quindi una sala puo' leggerlo e disegnarlo
 * senza sapere niente di fasi e di molle.
 */
@Immutable
data class SalaPalette(
    /** Quanto e' scuro il tema, da 0 a 1. Era un booleano, e ogni tinta della
     *  galleria ci scattava sopra; da qui in poi si interpola. */
    val buio: Float,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    /** L'accento: terracotta sul chiaro, pesca sullo scuro. */
    val accent: Color,
    /** Cio' che si scrive **sopra** l'accento. */
    val accentInk: Color,
    /** Il fondo dei riquadri dentro un pannello. */
    val chip: Color,
    /** La maniglia, i binari, i divisori: il grigio dell'interfaccia. */
    val maniglia: Color,
    /** Il pannello a cassetto, translucido sul cielo. */
    val panel: Color,
    /** Lo stesso pannello dove non ci puo' essere trasparenza (la maniglia
     *  della barra delle ore, il disco dell'aria). */
    val panelSolido: Color,
    /** Il fondo delle schermate che coprono il cielo per intero. */
    val schermoPieno: Color,
    /**
     * L'inchiostro di cio' che sta **fuori dai pannelli**.
     *
     * La barra delle ore, la riga che spiega il gesto, il giorno mostrato: non
     * hanno un pannello sotto, hanno le colline. Il difetto e' arrivato da chi
     * l'app la usa fuori: al sole diretto quelle righe sparivano, perche' un
     * grigio all'ottanta percento su un verde medio ha un contrasto che al
     * chiuso basta e in pieno giorno no.
     *
     * Non e' un colore scelto a mano: e' [ink] passato per `readableOn` contro
     * la collina piu' avanti, cioe' spinto fino alla soglia AA e non oltre. La
     * palette si costruisce una volta per fotogramma del tema, che e'
     * esattamente dove `Contrast.kt` dice di metterlo.
     */
    val inkSuCielo: Color,
    /** Come [inkSuCielo], per l'accento: l'ora sopra la barra. */
    val accentSuCielo: Color,
    val collina1: Color,
    val collina2: Color,
    val collina3: Color,
    val tronco: Color,
    val fronda: Color,
) {
    /** Per chi davvero non puo' mescolare: lo stile delle icone di sistema e' o
     *  chiaro o scuro, non c'e' una via di mezzo da dichiarare. */
    val dark: Boolean get() = buio > 0.5f
}

/**
 * Le tinte complete, a partire da numeri **gia' animati**.
 *
 * Non legge il cielo ne' la fase: quelli li ha gia' risolti e smorzati
 * `SalaShell`, che e' l'unico posto in cui l'animazione del tema deve vivere.
 * Qui si assembla e basta, ed e' questo che permette a un colore che passa di
 * arrivare fino alle sale senza che nessuna se ne accorga.
 *
 * @param dk quanto e' scuro il tema, gia' passato per la sua molla.
 * @param crepuscolo quanto si e' dentro alba o tramonto, gia' animato: decide
 *   se i pannelli scuri sono caldi (bruno) o freddi (blu notte).
 * @param chiusura quanto e' chiuso il cielo (0..4): spegne le colline quando il
 *   sole non le tocca piu'.
 */
fun salaPalette(dk: Float, crepuscolo: Float, chiusura: Float): SalaPalette {
    // **L'inchiostro passa su una finestra piu' stretta del tema.** Se seguisse
    // `dk` per intero, per tutto il tragitto ci sarebbe un inchiostro a meta'
    // strada fra il nero e il bianco, e a meta' strada nessuno dei due si
    // legge. Stringendolo attorno all'attraversamento, i due inchiostri si
    // sovrappongono solo nei pochi centesimi di secondo in cui anche il tema
    // sta attraversando la fascia che lo scalino esiste apposta per saltare.
    val buio = SunClock.smoothstep(0.40f, 0.60f, dk)
    val inkBase = lerp(SalaTokens.neutral900, SalaTokens.neutral100, buio)

    // I due fondi scuri del prototipo: bruno nei crepuscoli, blu notte altrove.
    val scuroCaldo = Color(0xFF2B2126)
    val scuroFreddo = Color(0xFF141C28)
    val pieno = lerp(scuroFreddo, scuroCaldo, crepuscolo.coerceIn(0f, 1f))

    // Le colline si spengono quando il cielo si chiude: nel prototipo sono due
    // terne distinte (`liv >= 2`), qui si passa da una all'altra.
    val chiuso = ((chiusura - 1f) / 1.4f).coerceIn(0f, 1f)
    val verdi = listOf(
        lerp(hex(0xCCDBB2), hex(0xC2C8B4), chiuso),
        lerp(hex(0xAEBF92), hex(0xA7AE96), chiuso),
        lerp(hex(0x8FA073), hex(0x8B9179), chiuso),
    )
    // Di notte le colline sono quasi nere, e nei crepuscoli tengono un fondo
    // di terra bruciata invece del verde freddo.
    val scure = listOf(
        lerp(hex(0x2E3528), hex(0x4B4738), crepuscolo),
        lerp(hex(0x272E1B), hex(0x3A3A2C), crepuscolo),
        lerp(hex(0x1E2417), hex(0x2A2C21), crepuscolo),
    )

    val fondoFuori = lerp(verdi[2], scure[2], buio)
    // Il fondo su cui cade quasi tutto il testo: il pannello, nella sua
    // versione senza trasparenza. Il pannello vero e' translucido e lascia
    // passare il cielo, quindi il contrasto reale e' un po' peggiore di questo
    // - ragione in piu' per non stare sul filo della soglia.
    val fondoPannello = lerp(SalaTokens.neutral100, pieno, buio)

    return SalaPalette(
        buio = buio,
        ink = inkBase,
        // **I due inchiostri smorzati non si smorzano piu' a occhio.**
        // Erano due trasparenze scelte a mano - ottanta e sessanta per cento -
        // e sul pannello chiaro il secondo dava poco meno di quattro a uno:
        // abbastanza al chiuso, non abbastanza al sole, ed e' al sole che
        // qualcuno ha provato a leggere l'ora sotto le colonne. Restano
        // smorzati, ma **fino alla soglia e non oltre**: `readableOn` parte da
        // quella trasparenza e schiarisce o scurisce solo quanto serve.
        inkSoft = inkBase.copy(alpha = lerp(0.78f, 0.80f, buio)).readableOn(fondoPannello),
        inkFaint = inkBase.copy(alpha = lerp(0.60f, 0.62f, buio)).readableOn(fondoPannello),
        accent = lerp(SalaTokens.accent600, SalaTokens.accent300, buio),
        accentInk = lerp(SalaTokens.neutral100, SalaTokens.neutral900, buio),
        chip = lerp(
            SalaTokens.neutral900.copy(alpha = 0.07f),
            SalaTokens.neutral100.copy(alpha = 0.12f),
            buio,
        ),
        maniglia = lerp(
            SalaTokens.neutral900.copy(alpha = 0.16f),
            SalaTokens.neutral100.copy(alpha = 0.24f),
            buio,
        ),
        panel = lerp(SalaTokens.neutral100.copy(alpha = 0.90f), pieno.copy(alpha = 0.82f), buio),
        panelSolido = fondoPannello,
        schermoPieno = lerp(Color(0xFFF4ECE0), pieno, buio),
        // Il fondo su cui cadono davvero: la collina piu' avanti, che e' la
        // piu' scura delle tre e quindi il caso peggiore per un inchiostro
        // scuro. AA_LARGE e non AA perche' sono maiuscoletti in grassetto e
        // cifre grandi, cioe' proprio cio' che la norma chiama testo grande;
        // chiedere 4.5 li spingerebbe al bianco pieno anche a mezzogiorno.
        inkSuCielo = inkBase.readableOn(fondoFuori, CONTRAST_AA_LARGE),
        accentSuCielo = lerp(SalaTokens.accent600, SalaTokens.accent300, buio)
            .readableOn(fondoFuori, CONTRAST_AA_LARGE),
        collina1 = lerp(verdi[0], scure[0], buio),
        collina2 = lerp(verdi[1], scure[1], buio),
        collina3 = fondoFuori,
        tronco = lerp(hex(0x56633F), hex(0x151A10), buio),
        fronda = lerp(hex(0x728157), hex(0x1A2013), buio),
    )
}

/**
 * La tipografia di Sala: Caprasimo per cio' che si guarda, Figtree per cio'
 * che si legge.
 *
 * Le misure sono quelle del prototipo, che e' disegnato su 411 x 914 - cioe'
 * gia' in punti Android di un telefono di riferimento.
 */
object SalaType {
    /** Il nome della citta' in cima, e i titoli delle schermate di servizio. */
    val citta = TextStyle(fontFamily = Caprasimo, fontSize = 22.sp, lineHeight = 24.sp)

    /** Il titolo di ogni sala, in cima al pannello. */
    val cardTitle = TextStyle(
        fontFamily = Caprasimo,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.015).em,
    )

    /** Il titolo della schermata di benvenuto. */
    val pageTitle = TextStyle(
        fontFamily = Caprasimo,
        fontSize = 38.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    )

    /** Il numero grande di una sala: i millimetri, l'indice UV, il vento. */
    val numeroSala = TextStyle(fontFamily = Caprasimo, fontSize = 44.sp, lineHeight = 40.sp)

    /**
     * MAIUSCOLETTO SPAZIATO: le etichette sopra ogni valore.
     *
     * **Sono stati provati undici punti e sono tornati dieci.** Il ragionamento
     * era buono - l'app si guarda in strada, col sole di taglio, e dieci punti
     * di maiuscoletto reggono lo schermo vicino e non la luce piena - ma queste
     * etichette stanno in **celle da un terzo di pannello**, e undici punti le
     * troncavano: "PROBABILITÀ" diventava "PROBABIL", "INTENSITÀ" diventava
     * "INTENSIT". Una parola tagliata si legge peggio di una parola piccola.
     *
     * Il respiro se l'e' preso da due parti: `CellaValore` ha ridotto il
     * proprio margine interno, e la spaziatura fra le lettere e' scesa da un
     * decimo di em a sessantacinque millesimi. **Non e' bastato lo stesso**, e
     * dopo tre giri di CI "PROBABILITÀ" e' diventata "PROBAB.": nove caratteri
     * in un terzo di pannello sono il tetto, e undici non ci stanno per quanto
     * si stringa.
     *
     * Chi aggiunge un'etichetta piu' lunga la vedra' finire coi puntini -
     * `CellaValore` mette `Ellipsis` apposta - e la strada sara' accorciare la
     * parola, non stringere ancora: qui non c'e' rimasto niente da stringere.
     *
     * La leggibilita' di queste righe arriva percio' dal contrasto e non dalla
     * taglia: `inkSoft` e `inkFaint` passano da `readableOn`, e [microLabel] -
     * che non sta in cella ma sotto le colonne - e' cresciuto davvero.
     */
    val sectionLabel = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.065.em,
    )

    /** Come [sectionLabel], ma per le etichette piu' minute dentro le celle. */
    val microLabel = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.1.em,
    )

    /** Il valore sotto un'etichetta. */
    val value = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)

    /** Il corpo delle didascalie. */
    val body = TextStyle(fontFamily = Figtree, fontSize = 14.sp, lineHeight = 21.sp)

    /** Una riga di servizio: piu' piccola del corpo, stessa voce. */
    val footnote = TextStyle(fontFamily = Figtree, fontSize = 13.sp, lineHeight = 20.sp)

    /** Il nome di una riga o di un interruttore. */
    val rowTitle = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp)

    /** La nota sotto il nome di una riga. */
    val rowNote = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp)

    /** L'ora sopra la maniglia, il giorno di una colonna. */
    val hourLabel = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.06.em,
    )

    /** La massima di un giorno nella striscia. */
    val giornoMax = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp)

    /** La minima, smorzata sotto la massima. */
    val giornoMin = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp)

    /** Il testo di una pastiglia. */
    val pill = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp)

    /** Il numero dei gradi, e ogni altro numero che si guarda invece di leggerlo. */
    fun giant(sizeSp: Int): TextStyle = TextStyle(
        fontFamily = Caprasimo,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 0.8f).sp,
        letterSpacing = (-0.05).em,
    )
}

