package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.CardTheme
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
fun washColors(phase: SalaPhase, condition: SalaCondition, intensity: Float = 1f): List<Color> {
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
data class SalaPalette(
    val dark: Boolean,
    val ground: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val inkAccent: Color,
    val veil: Brush,
    val wash: List<Color>,
)

private fun darkVeil(paperDark: Color): Brush = Brush.verticalGradient(
    0.00f to paperDark.copy(alpha = 0.34f),
    0.26f to paperDark.copy(alpha = 0f),
    0.46f to paperDark.copy(alpha = 0f),
    0.66f to paperDark.copy(alpha = 0.82f),
    0.86f to paperDark,
)

private fun lightVeil(bg: Color): Brush = Brush.verticalGradient(
    0.00f to bg.copy(alpha = 0.26f),
    0.22f to bg.copy(alpha = 0f),
    0.40f to bg.copy(alpha = 0f),
    0.62f to bg.copy(alpha = 0.74f),
    0.84f to bg,
)

/**
 * Le tinte complete di una sala, per la fase, il tempo e il tema scelti.
 *
 * @param intensity il cursore "intensita' lavaggi": nel prototipo era una
 *   leva di messa a punto, qui resta un moltiplicatore a disposizione di chi
 *   regola l'app, di norma 1.
 */
fun salaPalette(
    sky: SkyState,
    phase: SalaPhase,
    condition: SalaCondition,
    theme: CardTheme,
    intensity: Float = 1f,
): SalaPalette {
    val autoDark = paperDarkness(sky.dayness)
    val dk = when (theme) {
        CardTheme.CHIARO -> 0f
        CardTheme.SCURO -> 1f
        CardTheme.AUTO -> autoDark
    }
    val dark = dk > 0.5f
    val ground = lerp(SalaTokens.bg, SalaTokens.paperDark, dk)
    val inkBase = if (dark) SalaTokens.neutral100 else SalaTokens.text
    return SalaPalette(
        dark = dark,
        ground = ground,
        ink = inkBase,
        inkSoft = inkBase.copy(alpha = if (dark) 0.62f else 0.58f),
        inkFaint = inkBase.copy(alpha = if (dark) 0.16f else 0.12f),
        inkAccent = if (dark) SalaTokens.accent400 else SalaTokens.accent700,
        veil = if (dark) darkVeil(SalaTokens.paperDark) else lightVeil(SalaTokens.bg),
        wash = washColors(phase, condition, intensity),
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
