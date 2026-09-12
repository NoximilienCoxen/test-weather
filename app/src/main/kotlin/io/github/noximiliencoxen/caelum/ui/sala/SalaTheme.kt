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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * La carta: la tavolozza acquerello di Sala, sostituisce il cielo a sfumatura
 * di `ui/theme/Colors.kt` per l'intera navigazione principale.
 *
 * Il fondo non e' piu' un cielo continuo: e' una pagina che scurisce con le
 * ore, a scatti (vedi [paperDarkness]), sopra la quale galleggiano tre
 * macchie d'acquerello il cui colore dipende dalla fase del giorno e dal
 * tempo (vedi [washColors]). Font: Source Serif 4, l'unico dell'intera Sala.
 */
private val SourceSerif4 = FontFamily(
    Font(R.font.source_serif_300, FontWeight.Light),
    Font(R.font.source_serif_400, FontWeight.Normal),
    Font(R.font.source_serif_600, FontWeight.SemiBold),
    Font(R.font.source_serif_700, FontWeight.Bold),
)

/** I token di colore del sistema Broadsheet da cui e' stata esportata Sala v3. */
object SalaTokens {
    val bg = Color(0xFFF3F2F2)
    val text = Color(0xFF201E1D)

    val accent = Color(0xFF0088B0)
    val accent2 = Color(0xFFD6006C)
    val processYellow = Color(0xFFEDBB00)

    val neutral100 = Color(0xFFF8F4F4)
    val neutral200 = Color(0xFFEAE7E7)
    val neutral300 = Color(0xFFD7D3D3)
    val neutral400 = Color(0xFFBAB6B6)
    val neutral900 = Color(0xFF2D2B2B)

    val accent200 = Color(0xFFCBEEFF)
    val accent300 = Color(0xFF99E0FF)
    val accent400 = Color(0xFF62C5EE)
    val accent700 = Color(0xFF006786)
    val accent800 = Color(0xFF004961)
    val accent900 = Color(0xFF0A303E)

    val accent2_200 = Color(0xFFFFDEE6)
    val accent2_300 = Color(0xFFFFC0D0)
    val accent2_400 = Color(0xFFFF90B1)
    val accent2_500 = Color(0xFFFF458E)
    val accent2_700 = Color(0xFFAA0B56)
    val accent2_800 = Color(0xFF790E3D)

    /**
     * Carta scurita, non nera: il nero neutro raffredda tutto, il giallo di
     * quadricromia le tiene addosso il calore della stampa.
     */
    val paperDark: Color = lerp(processYellow, neutral900, 0.86f)

    // ── La luna ha colori suoi, e li tiene nei due temi ──────────────────────
    //
    // **Prima la luna prendeva in prestito l'inchiostro del testo**, e in tema
    // chiaro il risultato era assurdo: la parte **illuminata** veniva dipinta
    // col nero del corpo del testo, e il disco in ombra - un grigio chiarissimo
    // al ventiquattro per cento di opacita' - spariva dentro la carta. Al
    // novilunio non restava niente sullo schermo, e la sala dichiarava
    // "0 % illuminata" sotto un disco pieno.
    //
    // Il difetto non era il contrasto ma il **prestito**: un corpo celeste non
    // ha il colore dell'inchiostro della pagina che lo mostra. La luna e' la
    // stessa cosa di giorno e di notte, in tema chiaro e in tema scuro; cambia
    // solo cio' che ha intorno. Quindi due tinte fisse, e nessun ramo sul tema.

    /** La faccia al sole: avorio caldo, non bianco. Un bianco puro su carta
     *  chiara e' invisibile, e su carta scura sembra un foro. */
    val lunaLuce = Color(0xFFF6F1E6)

    /** La faccia in ombra: ardesia vera. Deve reggere **sulla carta chiara**,
     *  che e' il caso che prima non reggeva. */
    val lunaOmbra = Color(0xFF3B4450)
}

/** Le quattro ore della giornata su cui e' costruita la tavolozza di Sala. */
enum class SalaPhase { ALBA, GIORNO, TRAMONTO, NOTTE }

/** Il tempo, ridotto alle sei famiglie che la tavolozza distingue. */
enum class SalaCondition { SERENO, NUVOLOSO, PIOGGIA, GRANDINE, TEMPORALE, TEMPORALE_GRANDINE }

private val WetConditions = setOf(
    SalaCondition.PIOGGIA,
    SalaCondition.GRANDINE,
    SalaCondition.TEMPORALE,
    SalaCondition.TEMPORALE_GRANDINE,
)

/**
 * La fase del giorno da cui parte la tavolozza, ricavata dal cielo vero.
 *
 * Non e' l'orologio a deciderla come nel prototipo, ma l'altezza reale del
 * sole nella localita' mostrata: gli stessi confini che [SkyState.of] gia'
 * usa per far comparire sole e luna, cosi' la carta cambia tinta esattamente
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
 * mezza il cielo e' mezza alba, e la carta lo diceva "notte" fino a un istante
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
 * Le giunture del giro NOTTE → ALBA → GIORNO → TRAMONTO → NOTTE sono due.
 *
 * A **mezzanotte** entrambe le fasi sono NOTTE e l'avanzamento e' zero: non c'e'
 * niente da attraversare.
 *
 * Al **mezzogiorno solare** il crepuscolo nominato cambia da ALBA a TRAMONTO,
 * perche' `evening` scavalca la meta'. Ma `SunClock.eveningness` e'
 * `smoothstep(0,42 → 0,58)` sulla frazione di giornata, quindi attraversa la
 * meta' a frazione 0,5 - cioe' **esattamente al mezzogiorno solare**, dove
 * `SunClock.altitude` vale 1,0. Li' tutti e due gli smoothstep qui sotto sono
 * saturi, l'avanzamento vale 1 e la miscela e' cento per cento GIORNO
 * **qualunque** crepuscolo sia nominato. L'identita' del crepuscolo conta solo
 * finche' l'avanzamento e' sotto 1; `evening` gira solo quando vale 1 esatto.
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

/** Il codice WMO ridotto alle sei famiglie della tavolozza. */
fun salaConditionOf(code: Int?): SalaCondition = when {
    code == null -> SalaCondition.SERENO
    code == 96 || code == 99 -> SalaCondition.TEMPORALE_GRANDINE
    code == 95 -> SalaCondition.TEMPORALE
    code == 77 || code == 85 || code == 86 -> SalaCondition.GRANDINE
    code >= 51 -> SalaCondition.PIOGGIA
    code >= 1 -> SalaCondition.NUVOLOSO
    else -> SalaCondition.SERENO
}

private val PhaseWash: Map<SalaPhase, List<Pair<Color, Int>>> = mapOf(
    SalaPhase.ALBA to listOf(SalaTokens.accent2_400 to 44, SalaTokens.processYellow to 40, SalaTokens.accent200 to 52),
    SalaPhase.GIORNO to listOf(SalaTokens.accent to 42, SalaTokens.processYellow to 46, SalaTokens.accent200 to 54),
    SalaPhase.TRAMONTO to listOf(SalaTokens.processYellow to 50, SalaTokens.accent2_500 to 40, SalaTokens.accent2_200 to 50),
    SalaPhase.NOTTE to listOf(SalaTokens.accent900 to 56, SalaTokens.accent2_800 to 44, SalaTokens.accent800 to 38),
)

private val PhaseTint: Map<SalaPhase, Color> = mapOf(
    SalaPhase.ALBA to SalaTokens.accent2_400,
    SalaPhase.GIORNO to SalaTokens.processYellow,
    SalaPhase.TRAMONTO to SalaTokens.accent2_500,
    SalaPhase.NOTTE to SalaTokens.accent900,
)

private val PhaseTintCool: Map<SalaPhase, Color> = mapOf(
    SalaPhase.ALBA to SalaTokens.accent2_300,
    SalaPhase.GIORNO to SalaTokens.accent200,
    SalaPhase.TRAMONTO to SalaTokens.accent2_400,
    SalaPhase.NOTTE to SalaTokens.accent900,
)

/** Una macchia condizionata dal tempo: colore proprio, intensita', e quanto resta se' stessa. */
private data class WashOverride(val color: Color, val strength: Int, val share: Float)

private data class CondWash(val mul: Float = 1f, val a: WashOverride? = null, val b: WashOverride? = null)

private val CondWashTable: Map<SalaCondition, CondWash> = mapOf(
    SalaCondition.SERENO to CondWash(),
    SalaCondition.NUVOLOSO to CondWash(mul = 0.8f, a = WashOverride(SalaTokens.neutral400, 38, 0.5f)),
    SalaCondition.PIOGGIA to CondWash(
        mul = 0.95f,
        a = WashOverride(SalaTokens.accent700, 46, 0.65f),
        b = WashOverride(SalaTokens.accent300, 46, 0.5f),
    ),
    SalaCondition.GRANDINE to CondWash(
        mul = 0.92f,
        a = WashOverride(SalaTokens.accent400, 48, 0.6f),
        b = WashOverride(SalaTokens.neutral200, 54, 0.45f),
    ),
    SalaCondition.TEMPORALE to CondWash(
        mul = 1f,
        a = WashOverride(SalaTokens.accent900, 44, 0.7f),
        b = WashOverride(SalaTokens.accent2_700, 34, 0.55f),
    ),
    SalaCondition.TEMPORALE_GRANDINE to CondWash(
        mul = 1f,
        a = WashOverride(SalaTokens.accent900, 48, 0.72f),
        b = WashOverride(SalaTokens.neutral300, 42, 0.5f),
    ),
)

/**
 * Il colore delle tre macchie d'acquerello per la fase e il tempo dati.
 *
 * Le prime due possono venire tinte dal tempo (pioggia, grandine, temporale...),
 * mescolate verso il colore della fase cosi' la macchia non stona con l'ora del
 * giorno; la terza resta sempre quella della sola fase. Di notte le macchie
 * pesano un quarto in piu', perche' sulla carta scurita un colore debole
 * sparisce.
 */
private fun washDiFase(phase: SalaPhase, condition: SalaCondition, intensity: Float): List<Color> {
    val base = PhaseWash.getValue(phase)
    val override = CondWashTable[condition] ?: CondWash()
    val mul = override.mul * (if (phase == SalaPhase.NOTTE) 1.25f else 1f) * intensity

    fun blob(index: Int, wash: WashOverride?): Color {
        val (ink, strength) = if (wash != null) {
            val partner = if (condition in WetConditions) {
                PhaseTintCool.getValue(phase)
            } else {
                PhaseTint.getValue(phase)
            }
            // color-mix(in srgb, ci share%, partner) = share*ci + (1-share)*partner.
            lerp(partner, wash.color, wash.share) to wash.strength
        } else {
            base[index]
        }
        val alpha = min(78, (strength * mul).roundToInt()).coerceAtLeast(0) / 100f
        return ink.copy(alpha = alpha)
    }

    return listOf(blob(0, override.a), blob(1, override.b), blob(2, null))
}

/**
 * Le tre macchie per una fase **a meta' strada**.
 *
 * Si mescolano i **risultati** delle due tabelle, non le tabelle: quelle sono il
 * porting uno a uno del prototipo, tarate a mano, e restano intatte. Il `lerp`
 * di Compose passa per Oklab, quindi i valori di mezzo non ingrigiscono come
 * farebbe una media sui canali.
 *
 * **Da non "semplificare":** dentro [washDiFase] c'e' un moltiplicatore che vale
 * un quarto in piu' di notte. Li' e' giusto - ogni capo si calcola con la
 * propria fase, e il `lerp` se lo porta dietro. Sollevarlo qui, al livello
 * mescolato, rimetterebbe uno scalino del venticinque per cento esattamente
 * dove si sta lavorando per toglierlo.
 */
fun washColors(fase: FaseContinua, condition: SalaCondition, intensity: Float = 1f): List<Color> {
    if (fase.avanzamento <= 0f) return washDiFase(fase.da, condition, intensity)
    if (fase.avanzamento >= 1f) return washDiFase(fase.a, condition, intensity)
    val a = washDiFase(fase.da, condition, intensity)
    val b = washDiFase(fase.a, condition, intensity)
    return List(3) { lerp(a[it], b[it], fase.avanzamento) }
}

/**
 * Quanto e' scura la carta, da 0 (piena) a 1 (scurita nel cuore della notte).
 *
 * L'inchiostro non segue con continuita': scatta una volta sola appena la
 * carta supera la soglia, saltando la fascia intermedia — quella in cui
 * nessun colore di testo reggerebbe il fondo. `dayness` e' gia' la stessa
 * transizione morbida (0 notte, 1 giorno pieno) che governa comparsa di sole
 * e luna altrove in app.
 */
fun paperDarkness(dayness: Float): Float {
    val t = (1f - dayness).coerceIn(0f, 1f)
    return if (t < 0.42f) t * 0.5f else 0.62f + (t - 0.42f) * 0.655f
}

/** Le tinte di una sala: fondo, inchiostro, velo e macchie, gia' pronte da disegnare. */
@Immutable
data class SalaPalette(
    /**
     * Quanto e' scura la carta, da 0 a 1.
     *
     * **Era un booleano, e ogni tinta della galleria ci scattava sopra**: un
     * `if (dark)` per l'inchiostro, uno per l'accento, uno per il velo, uno per
     * la grana. Attraversando il crepuscolo si ribaltavano tutti nello stesso
     * fotogramma. Da qui in poi si interpola.
     */
    val buio: Float,
    val ground: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val inkAccent: Color,
    val veil: Brush,
    val wash: List<Color>,
) {
    /** Per chi davvero non puo' mescolare: lo stile delle icone di sistema e' o
     *  chiaro o scuro, non c'e' una via di mezzo da dichiarare. */
    val dark: Boolean get() = buio > 0.5f
}

/**
 * Il velo che appoggia il testo sulla carta piatta, a qualunque grado di buio.
 *
 * Erano due pennelli distinti, uno per tema, e passare dall'uno all'altro era un
 * lampo. Differiscono in tre cose - la tinta, la **posizione delle fermate** e
 * l'opacita' - e tutte e tre si interpolano pulite, quindi ce n'e' uno solo.
 */
private fun velo(buio: Float): Brush {
    val tinta = lerp(SalaTokens.bg, SalaTokens.paperDark, buio)
    fun dove(chiaro: Float, scuro: Float) = lerp(chiaro, scuro, buio)
    fun quanto(chiaro: Float, scuro: Float) = lerp(chiaro, scuro, buio)
    return Brush.verticalGradient(
        0.00f to tinta.copy(alpha = quanto(0.26f, 0.34f)),
        dove(0.22f, 0.26f) to tinta.copy(alpha = 0f),
        dove(0.40f, 0.46f) to tinta.copy(alpha = 0f),
        dove(0.62f, 0.66f) to tinta.copy(alpha = quanto(0.74f, 0.82f)),
        dove(0.84f, 0.86f) to tinta,
    )
}

/**
 * Le tinte complete di una sala, a partire da numeri **gia' animati**.
 *
 * Non legge piu' il cielo, la fase ne' il tema: quelli li ha gia' risolti e
 * smorzati [io.github.noximiliencoxen.caelum.ui.sala.SalaShell], che e' l'unico
 * posto in cui l'animazione del tema deve vivere. Qui si assembla e basta, e
 * questa e' la ragione per cui un colore che passa puo' arrivare fin qui senza
 * che nessuna sala se ne accorga.
 *
 * @param dk quanto e' scura la carta, gia' passato per la sua molla.
 * @param wash le tre macchie, gia' mescolate fra le fasi e gia' smorzate.
 */
fun salaPalette(dk: Float, wash: List<Color>): SalaPalette {
    // **L'inchiostro passa su una finestra piu' stretta della carta.** Se
    // seguisse `dk` per intero, per tutto il tragitto ci sarebbe un inchiostro a
    // meta' strada fra il nero e il bianco, e a meta' strada nessuno dei due si
    // legge. Stringendolo attorno all'attraversamento, i due inchiostri si
    // sovrappongono solo nei pochi centesimi di secondo in cui anche la carta
    // sta attraversando la fascia che `paperDarkness` esiste apposta per
    // saltare.
    val buio = SunClock.smoothstep(0.40f, 0.60f, dk)
    val inkBase = lerp(SalaTokens.text, SalaTokens.neutral100, buio)
    return SalaPalette(
        buio = buio,
        ground = lerp(SalaTokens.bg, SalaTokens.paperDark, dk),
        ink = inkBase,
        inkSoft = inkBase.copy(alpha = lerp(0.58f, 0.62f, buio)),
        inkFaint = inkBase.copy(alpha = lerp(0.12f, 0.16f, buio)),
        inkAccent = lerp(SalaTokens.accent700, SalaTokens.accent400, buio),
        veil = velo(buio),
        wash = wash,
    )
}

/** La tipografia di Sala: un solo carattere, Source Serif 4, per tutta la navigazione. */
object SalaType {
    val roomLabel = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.1.em,
    )
    val sectionLabel = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.08.em,
    )
    val cardTitle = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.015).em,
    )
    val pageTitle = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Light,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.025).em,
    )
    val weekHeadline = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.015).em,
    )
    val body = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.6.sp,
    )
    val footnote = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )
    val hourLabel = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.08.em,
    )
    val value = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.01).em,
    )
    val toggleLabel = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 22.sp,
    )

    fun giant(sizeSp: Int, weight: FontWeight = FontWeight.Light): TextStyle = TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = weight,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 0.9f).sp,
        letterSpacing = (-0.035).em,
    )
}

/**
 * Sereno o no, per decidere se un simbolo (l'iconcina di una localita', il
 * glifo di un giorno) mostra sole, nuvola, pioggia, grandine o fulmine.
 */
val SalaCondition.isDry: Boolean get() = this == SalaCondition.SERENO

/** Il condition WMO letto direttamente in `SalaCondition`, per chi ha solo il codice. */
fun Wmo.Family.toSalaConditionFallback(): SalaCondition = when (this) {
    Wmo.Family.PIOGGIA -> SalaCondition.PIOGGIA
    Wmo.Family.NEVE -> SalaCondition.PIOGGIA
    Wmo.Family.TEMPORALE -> SalaCondition.TEMPORALE
    Wmo.Family.NUVOLOSO, Wmo.Family.NEBBIA -> SalaCondition.NUVOLOSO
    Wmo.Family.ASCIUTTO -> SalaCondition.SERENO
}
