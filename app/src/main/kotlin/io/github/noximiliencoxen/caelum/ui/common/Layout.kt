package io.github.noximiliencoxen.caelum.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le misure della schermata, ricavate una volta e passate in giro.
 *
 * Prima erano numeri fissi sparsi nei file: la cifra del dettaglio alta 180dp, i
 * grafici 200 e 260, le linguette dei giorni larghe 84. Su un telefono corto la
 * colonna andava in fuori misura, su un tablet restava tutto minuscolo in mezzo
 * al vuoto, e chi ha il carattere di sistema ingrandito vedeva le etichette
 * tagliate.
 *
 * Tre fasce e non un calcolo continuo: fra 360 e 400 punti di larghezza non c'e'
 * niente da adattare, e un layout che cambia a ogni pixel di larghezza e' un
 * layout che nessuno ha mai visto in due schermi uguali.
 *
 * Non serve `material3-window-size-class`: e' un artefatto in piu' nel catalogo
 * per tre soglie che qui si leggono in due righe.
 *
 * **Erano nove misure, adesso sono tre.** Le altre sei - `heroFraction`,
 * `chartHeight`, `tallChartHeight`, `dayTabWidth`, `statColumns` e `landscape`
 * col suo `sideBySide` - descrivevano il foglio di dettaglio e le griglie di
 * statistiche che il feed ha sostituito. Nessuna era piu' letta da nessuno:
 * i consumatori reali di questo file, in tutto il progetto, sono quattro righe
 * fra `SectionCard` e `AlertsSheet`. Restavano pero' da tenere allineate su
 * quattro rami, e ogni ramo era un invito a tararle.
 *
 * Con loro se n'e' andata anche la distinzione fra ritratto e orizzontale, che
 * in `CONTESTO.md` sezione 8 e' ancora scritta fra le cose non fatte: quando le
 * schede orizzontali si faranno davvero, la misura che serve si scrivera'
 * sapendo cosa deve reggere, invece di aspettarla ferma da mesi.
 */
@Immutable
data class MeteoLayout(
    /** Margine laterale del contenuto. */
    val gutter: Dp,
    /** Spazio fra due blocchi consecutivi. */
    val gap: Dp,
    val compact: Boolean,
)

/**
 * Le misure per lo schermo corrente.
 *
 * Legge `LocalConfiguration`, che e' in punti indipendenti dalla densita' e
 * cambia da solo alla rotazione: non serve un `BoxWithConstraints` attorno a
 * tutto, e soprattutto non serve che ogni schermata se lo misuri per conto suo.
 */
@Composable
fun rememberMeteoLayout(): MeteoLayout {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val landscape = width > height
    return remember(width, height) {
        when {
            landscape -> MeteoLayout(gutter = 28.dp, gap = 14.dp, compact = false)
            width < 360 -> MeteoLayout(gutter = 14.dp, gap = 10.dp, compact = true)
            width >= 600 -> MeteoLayout(gutter = 32.dp, gap = 20.dp, compact = false)
            else -> MeteoLayout(gutter = 20.dp, gap = 14.dp, compact = false)
        }
    }
}

/** L'area minima toccabile che Material pretende, e che qui mancava ovunque. */
val MinTouchTarget: Dp = 48.dp
