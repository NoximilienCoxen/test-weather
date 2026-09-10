package io.github.noximiliencoxen.caelum.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.R
import io.github.noximiliencoxen.caelum.ui.render.NumberPalette
import io.github.noximiliencoxen.caelum.ui.render.TemperatureRenderer
import io.github.noximiliencoxen.caelum.ui.render3d.PrismRenderer

val LocalTemperatureRenderer = staticCompositionLocalOf<TemperatureRenderer> { PrismRenderer() }

/** Le tinte delle grandezze, che Material 3 non nomina. Vedi [MeteoAccents]. */
val LocalMeteoAccents = staticCompositionLocalOf { skyColors(io.github.noximiliencoxen.caelum.data.SkyState.Giorno).toAccents() }

fun MeteoColors.toNumberPalette(): NumberPalette = NumberPalette(
    face = numberFace,
    sideNear = numberSideNear,
    sideFar = numberSideFar,
    chamfer = numberChamfer,
    iridescence = IridescenceStops,
    iridescenceAlpha = 0.55f,
    shadowAlpha = numberShadowAlpha,
)

/**
 * Un carattere solo per tutta l'app: Archivo, lo stesso della cifra gigante.
 *
 * Prima l'interfaccia usava il monospace di sistema e la cifra usava Archivo, e
 * le due meta' della schermata non si parlavano. Archivo e' **variabile**, quindi
 * la gerarchia si fa con gli assi invece che con file diversi: le etichette
 * strette e pesanti, i valori normali. Costo zero, perche' il file c'era gia'.
 *
 * **Il monospace non serve piu' a niente.** Restava per un'etichetta sola -
 * l'ora sotto la barra - dove scorrendo "09:00" e "14:00" devono restare
 * incolonnate. Quell'ora adesso sta nella bolla sopra il cursore e usa
 * [MeteoType.metric], che le cifre a larghezza fissa ce le ha per conto suo
 * (`tnum`) senza dover cambiare famiglia: stessa immobilita', ma col carattere
 * dell'app invece che con quello del sistema.
 */
private fun archivo(weight: Int, width: Float): FontFamily = FontFamily(
    Font(
        resId = R.font.archivo_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
        ),
    ),
)

object MeteoType {
    /** La domanda del benvenuto: e' l'unica riga grande che non sia una cifra. */
    val title = TextStyle(
        fontFamily = archivo(weight = 620, width = 88f),
        fontSize = 25.sp,
        letterSpacing = 0.02.em,
        lineHeight = 30.sp,
    )

    /** Un gradino sotto il titolo: le intestazioni delle schede. */
    val heading = TextStyle(
        fontFamily = archivo(weight = 600, width = 86f),
        fontSize = 18.sp,
        letterSpacing = 0.01.em,
        lineHeight = 23.sp,
    )

    val label = TextStyle(
        fontFamily = archivo(weight = 600, width = 82f),
        fontSize = 14.sp,
        letterSpacing = 0.10.em,
    )
    val value = TextStyle(
        fontFamily = archivo(weight = 450, width = 100f),
        fontSize = 14.sp,
        letterSpacing = 0.04.em,
    )
    val caption = TextStyle(
        fontFamily = archivo(weight = 560, width = 78f),
        fontSize = 12.sp,
        letterSpacing = 0.13.em,
    )

    /**
     * I paragrafi che spiegano qualcosa.
     *
     * Minuscolo e senza spaziatura extra, al contrario di tutto il resto: una
     * frase intera in maiuscolo spaziato si compita invece di leggersi, e nelle
     * impostazioni ce n'erano quattro di seguito. Le etichette restano in
     * maiuscolo - li' e' una parola sola e fa da segnale, non da testo.
     */
    val body = TextStyle(
        fontFamily = archivo(weight = 420, width = 100f),
        fontSize = 15.sp,
        letterSpacing = 0.005.em,
        lineHeight = 21.sp,
    )

    /**
     * I valori numerici delle tabelle.
     *
     * Archivo con le cifre a larghezza fissa (`tnum`), non il monospace di
     * sistema: scorrendo le ore i valori devono restare incolonnati, ma finora
     * lo si otteneva con un carattere che non e' quello dell'app e con una
     * spaziatura da orologio digitale, e "-10 °C" ne usciva sfilacciato.
     */
    val metric = TextStyle(
        fontFamily = archivo(weight = 520, width = 92f),
        fontSize = 15.sp,
        letterSpacing = 0.02.em,
        fontFeatureSettings = "tnum",
    )

    // ── Lo stile editoriale del feed ────────────────────────────────────────
    //
    // Due stili soli, e sono quelli che danno alle schede l'aria della
    // didascalia da galleria: un titolo tracciato largo e un occhiello sopra.
    //
    // **La spaziatura si scrive in `em` e non in `sp`, e non e' un dettaglio.**
    // La consegna diceva "tracciamento ampio, `letterSpacing = 0.15.sp`": i due
    // pezzi si contraddicono. `sp` e' una misura assoluta, quindi 0,15sp su un
    // corpo da 17 sono **quindici centesimi di punto**, cioe' un ventesimo di
    // quello che il tema gia' usa per le etichette (`label`, 0,10em = 1,7sp a
    // quel corpo) - il contrario di ampio. In `em` la spaziatura segue il corpo,
    // che e' l'unico modo perche' un titolo resti tracciato uguale a scala del
    // carattere di sistema doppia. Chi rilegge la consegna e rimette `0.15.sp`
    // stringe i titoli invece di allargarli.

    /**
     * Il titolo della scheda: poche parole in maiuscolo, larghe, **in grazie**.
     *
     * E' l'unica cosa dell'app che non e' Archivo, e la deroga e' voluta. Un
     * grottesco stretto in maiuscolo spaziato dice "etichetta"; un serif in
     * maiuscolo spaziato dice "didascalia", ed e' la differenza fra un pannello
     * di controllo e la targhetta accanto a un quadro. Con la scena dipinta
     * sotto, il secondo e' il registro giusto.
     *
     * **`FontFamily.Serif`, cioe' quello di sistema** - Noto Serif su Android -
     * e non un file nel progetto. Non e' un ripiego di comodo: e' un carattere
     * disegnato bene, presente su ogni telefono, che non pesa un byte e non ha
     * bisogno di una licenza. Volendo un serif dal carattere piu' marcato -
     * Playfair Display ha le grazie sottili e il contrasto alto che le riviste
     * usano - basta metterne il file in `res/font/` e cambiare **questa riga**:
     * il resto del tema non se ne accorge.
     */
    val masthead = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight(560),
        fontSize = 18.sp,
        letterSpacing = 0.15.em,
        lineHeight = 23.sp,
    )

    /**
     * L'occhiello sopra il titolo: di chi e di quando parla la scheda.
     *
     * Stesso tracciamento del titolo, corpo piu' piccolo e peso minore: e' la
     * riga che si legge dopo, non prima. La usano anche le etichette della barra
     * delle metriche, che fanno lo stesso mestiere in fondo alla scheda.
     */
    val kicker = TextStyle(
        // **Archivo e non il serif del titolo**, ed e' una scelta: sotto gli
        // undici punti le grazie non si leggono come grazie, si leggono come
        // bordi sporchi. Il contrasto fra le due righe ci guadagna comunque -
        // e' la coppia titolo in grazie piu' occhiello in bastoni che la carta
        // stampata usa da sempre.
        fontFamily = archivo(weight = 560, width = 78f),
        fontSize = 11.sp,
        letterSpacing = 0.15.em,
    )

}

/**
 * La tipografia Material, cosi' i componenti della libreria prendono Archivo
 * senza doverglielo passare a mano uno per uno. E' anche la ragione per cui i
 * `Text` senza `style` esplicito smettono di uscire col carattere di sistema.
 */
private val MeteoTypography = Typography(
    displayLarge = MeteoType.title.copy(fontSize = 44.sp, lineHeight = 50.sp),
    displayMedium = MeteoType.title.copy(fontSize = 36.sp, lineHeight = 42.sp),
    displaySmall = MeteoType.title.copy(fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = MeteoType.title,
    headlineMedium = MeteoType.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = MeteoType.heading,
    titleLarge = MeteoType.heading,
    titleMedium = MeteoType.label,
    titleSmall = MeteoType.caption,
    bodyLarge = MeteoType.body,
    bodyMedium = MeteoType.value,
    bodySmall = MeteoType.caption,
    labelLarge = MeteoType.metric,
    labelMedium = MeteoType.caption,
    labelSmall = MeteoType.caption.copy(fontSize = 11.sp),
)

/**
 * Le forme.
 *
 * Un solo raggio generoso per le schede (18dp, quello che il dettaglio gia'
 * usava) e uno pieno per le pillole. Material ne vuole cinque: gli altri tre
 * scalano fra questi due invece di essere inventati.
 */
private val MeteoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun MeteoTheme(
    colors: MeteoColors,
    content: @Composable () -> Unit,
) {
    val renderer = remember { PrismRenderer() }
    // Lo schema e le tinte costano una manciata di conversioni di gamma per
    // colore: si ricalcolano al cambio d'ora, non a ogni ricomposizione.
    val scheme = remember(colors) { colors.toColorScheme() }
    val accents = remember(colors) { colors.toAccents() }
    CompositionLocalProvider(
        LocalMeteoColors provides colors,
        LocalMeteoAccents provides accents,
        LocalTemperatureRenderer provides renderer,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MeteoTypography,
            shapes = MeteoShapes,
            content = content,
        )
    }
}
