package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import io.github.noximiliencoxen.caelum.prefs.CaptionStyle

/**
 * Le didascalie di Sala I: titolo e corpo per ognuna delle ventiquattro
 * combinazioni fase x tempo, portate dal prototipo. Sono testo, non dati —
 * i numeri che le accompagnano (temperatura, ora, percepiti) restano quelli
 * veri della previsione.
 */
private val SalaTitles: Map<SalaCondition, Map<SalaPhase, String>> get() = mapOf(
    SalaCondition.SERENO to mapOf(
        SalaPhase.ALBA to tr("Alba limpida sopra la pianura", "Clear dawn over the plain"),
        SalaPhase.GIORNO to tr("Pieno sole, aria calda", "Full sun, warm air"),
        SalaPhase.TRAMONTO to tr("Tramonto senza una nuvola", "Sunset without a cloud"),
        SalaPhase.NOTTE to tr("Notte serena, aria ferma", "Clear night, still air"),
    ),
    SalaCondition.NUVOLOSO to mapOf(
        SalaPhase.ALBA to tr("Luce filtrata dalle nubi alte", "Light filtered by high clouds"),
        SalaPhase.GIORNO to tr("Nuvole di passaggio", "Passing clouds"),
        SalaPhase.TRAMONTO to tr("Cielo coperto verso sera", "Overcast towards evening"),
        // Diceva "niente stelle", e da questo giro le stelle si vedono
        // **attraverso** le nuvole - le nubi non le spengono, le coprono. Una
        // didascalia che smentisce cio' che si ha sotto gli occhi toglie
        // credito anche alle altre sei.
        SalaPhase.NOTTE to tr("Notte coperta, poche stelle", "Cloudy night, few stars"),
    ),
    SalaCondition.PIOGGIA to mapOf(
        SalaPhase.ALBA to tr("Piove dalle prime luci", "Raining since first light"),
        SalaPhase.GIORNO to tr("Rovescio di metà pomeriggio", "Mid-afternoon shower"),
        SalaPhase.TRAMONTO to tr("Pioggia fino a notte", "Rain until night"),
        SalaPhase.NOTTE to tr("Pioggia nella notte", "Rain in the night"),
    ),
    // **Qui c'era la grandine, e sopra una nevicata si leggeva "Pioggia".** I
    // codici della neve - 71, 73, 75 - cadevano in PIOGGIA, e quelli dei
    // rovesci di neve - 77, 85, 86 - in una "grandine" che i codici WMO di
    // questa app non hanno mai contenuto. Adesso la neve ha le sue parole, e la
    // grandine resta dov'e' davvero: dentro il temporale che la fa.
    SalaCondition.NEVE to mapOf(
        SalaPhase.ALBA to tr("Neve dalle prime luci", "Snow since first light"),
        SalaPhase.GIORNO to tr("Nevica", "Snowing"),
        SalaPhase.TRAMONTO to tr("Neve fino a sera", "Snow until evening"),
        SalaPhase.NOTTE to tr("Neve nella notte", "Snow in the night"),
    ),
    SalaCondition.TEMPORALE to mapOf(
        SalaPhase.ALBA to tr("Temporale all'alba", "Thunderstorm at dawn"),
        SalaPhase.GIORNO to tr("Temporale sul pomeriggio", "Afternoon thunderstorm"),
        SalaPhase.TRAMONTO to tr("Temporale al tramonto", "Thunderstorm at sunset"),
        SalaPhase.NOTTE to tr("Temporale notturno", "Night thunderstorm"),
    ),
    SalaCondition.TEMPORALE_GRANDINE to mapOf(
        SalaPhase.ALBA to tr("Temporale e grandine all'alba", "Thunderstorm and hail at dawn"),
        SalaPhase.GIORNO to tr("Temporale con grandine", "Thunderstorm with hail"),
        SalaPhase.TRAMONTO to tr("Temporale e grandine a sera", "Thunderstorm and hail in the evening"),
        SalaPhase.NOTTE to tr("Temporale e grandine di notte", "Thunderstorm and hail at night"),
    ),
)

private val SalaBodies: Map<SalaCondition, String> get() = mapOf(
    SalaCondition.SERENO to tr("Cielo aperto e visibilità ottima.", "Open sky and excellent visibility."),
    SalaCondition.NUVOLOSO to tr("Nubi medie che coprono il sole a intervalli. Non portano pioggia, ma tengono la temperatura ferma.", "Mid-level clouds covering the sun at times. No rain, but they keep the temperature steady."),
    SalaCondition.PIOGGIA to tr("Pioggia in corso: i millimetri e la probabilità ora per ora sono in Sala III.", "Rain falling: millimetres and probability hour by hour are in Room III."),
    SalaCondition.NEVE to tr("Neve in caduta: fondo scivoloso e visibilità ridotta, soprattutto dove non passa nessuno.", "Snow falling: slippery ground and poor visibility, especially where nobody passes."),
    SalaCondition.TEMPORALE to tr("Fulminazione attiva. Meglio non stare all'aperto fino a mezz'ora dopo l'ultimo tuono.", "Active lightning. Better stay indoors until half an hour after the last thunder."),
    SalaCondition.TEMPORALE_GRANDINE to tr("Cella temporalesca con grandine: raffiche improvvise e visibilità ridotta.", "Storm cell with hail: sudden gusts and poor visibility."),
)

fun salaTitle(condition: SalaCondition, phase: SalaPhase): String =
    SalaTitles.getValue(condition).getValue(phase)

/**
 * I corpi che una fase sola smentirebbe.
 *
 * Il corpo di NUVOLOSO parlava del sole coperto "a intervalli" anche alle due
 * di notte, sotto un titolo che diceva *Notte coperta*. Vale qui la stessa
 * ragione gia' scritta sopra per quel titolo: una didascalia contraddetta da
 * cio' che si ha sotto gli occhi toglie credito anche alle altre cinque. La
 * tabella resta per condizione — e' li' che i corpi si somigliano — e questa
 * elenca le poche caselle in cui la fase cambia le parole.
 */
private val SalaBodiesPerFase: Map<Pair<SalaCondition, SalaPhase>, String> get() = mapOf(
    (SalaCondition.NUVOLOSO to SalaPhase.NOTTE) to
        tr("Nubi medie che scoprono le stelle a tratti. Non portano pioggia, ma trattengono il calore del giorno.", "Mid-level clouds revealing the stars at times. No rain, but they hold in the day's warmth."),
)

fun salaBody(condition: SalaCondition, phase: SalaPhase): String =
    SalaBodiesPerFase[condition to phase] ?: SalaBodies.getValue(condition)

fun SalaCondition.label(): String = when (this) {
    SalaCondition.SERENO -> tr("sereno", "clear")
    SalaCondition.NUVOLOSO -> tr("nuvoloso", "cloudy")
    SalaCondition.PIOGGIA -> tr("pioggia", "rain")
    SalaCondition.NEVE -> tr("neve", "snow")
    SalaCondition.TEMPORALE -> tr("temporale", "thunderstorm")
    SalaCondition.TEMPORALE_GRANDINE -> tr("temporale con grandine", "thunderstorm with hail")
}

fun SalaPhase.label(): String = when (this) {
    SalaPhase.ALBA -> tr("alba", "dawn")
    SalaPhase.GIORNO -> tr("giorno", "day")
    SalaPhase.TRAMONTO -> tr("tramonto", "sunset")
    SalaPhase.NOTTE -> tr("notte", "night")
}

// ─────────────────────────────────────────────────────────────────────────────
// Le didascalie, e l'interruttore che finalmente le comanda
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Quanto lunghe le vuole chi guarda.
 *
 * **La preferenza c'era da sempre e non la leggeva nessuno.** Nelle impostazioni
 * si sceglieva fra "Brevi" e "Complete", la scelta sopravviveva alla chiusura
 * dell'app, e nelle sette sale non cambiava una parola. Un comando che non
 * comanda niente non e' neutro: insegna a non fidarsi anche degli altri, e in
 * una schermata di impostazioni gli altri sono tutti li' accanto.
 *
 * Passa da un local e non da sei parametri: il corpo del testo lo scrivono sei
 * sale diverse, e infilare la preferenza in sei firme avrebbe voluto dire
 * toccarle tutte ogni volta che cambia. `compositionLocalOf` e non
 * `staticCompositionLocalOf` perche' questo valore **cambia** mentre l'app e'
 * aperta, ed e' esattamente il caso per cui i due si distinguono.
 */
val LocalDidascalie = compositionLocalOf { CaptionStyle.COMPLETE }

/**
 * Il corpo di una didascalia: sparisce quando si sono chieste brevi.
 *
 * Sparisce **il corpo e non il titolo**: chi chiede didascalie brevi vuole meno
 * parole, non meno informazione. Titolo e riga dei dati restano, e sono loro a
 * dire che tempo fa; il paragrafo e' quello che spiega, e lo si puo' togliere
 * senza perdere un fatto.
 */
@Composable
fun Didascalia(testo: String, palette: SalaPalette, modifier: Modifier = Modifier) {
    if (LocalDidascalie.current == CaptionStyle.BREVI) return
    Text(text = testo, style = SalaType.body, color = palette.inkSoft, modifier = modifier)
}

// ─────────────────────────────────────────────────────────────────────────────
// Le ore, gia' scritte
// ─────────────────────────────────────────────────────────────────────────────
//
// **Un giorno ha ventiquattro ore e le loro etichette sono sempre le stesse.**
// Erano un `String.format` ciascuna, e le colonne sono tante: sedici nella sala
// dei raggi, dodici nella pioggia, dodici nel vento, quattro nell'aria, una
// nella barra. `format` non e' una sostituzione di caratteri - compila il
// modello, cerca la posizione decimale della lingua in uso e costruisce due
// oggetti intermedi - e qui lo faceva a ogni ricomposizione, cioe' a ogni
// fotogramma mentre il cielo si muove.
//
// Un effetto collaterale che vale la pena dire: `%d` scrive le cifre **della
// lingua del telefono**, quindi su un telefono in arabo la barra delle ore
// usciva in cifre indo-arabe sotto un testo italiano. Qui escono sempre le
// stesse, come gia' fanno i decimali, che passano apposta da `Locale.ITALY`.

/** Le ventiquattro ore a due cifre: `00`, `01`, ... `23`. */
private val OreDueCifre: Array<String> = Array(24) { if (it < 10) "0$it" else "$it" }

/** Le stesse con i minuti: `00:00`, `01:00`, ... `23:00`. */
private val OrePiene: Array<String> = Array(24) { "${OreDueCifre[it]}:00" }

/**
 * L'ora a due cifre, senza minuti: l'etichetta sotto una colonna.
 *
 * Stringe fra 0 e 23 invece di lasciar passare un indice fuori posto: chi la
 * chiama passa sempre un `LocalDateTime.hour`, ma un'etichetta sbagliata e'
 * meno grave di una schermata che si chiude.
 */
fun oraDueCifre(ora: Int): String = OreDueCifre[ora.coerceIn(0, 23)]

/** L'ora piena, `HH:00`: l'etichetta grande della barra e le celle "PICCO". */
fun oraPiena(ora: Int): String = OrePiene[ora.coerceIn(0, 23)]
