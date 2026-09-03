package com.forli.meteo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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
 */
@Immutable
data class MeteoLayout(
    /** Margine laterale del contenuto. */
    val gutter: Dp,
    /** Spazio fra due blocchi consecutivi. */
    val gap: Dp,
    /** Quanta altezza dello schermo prende la cifra del dettaglio. */
    val heroFraction: Float,
    /** Altezza dei grafici a piena larghezza. */
    val chartHeight: Dp,
    /** Altezza del grafico orario del dettaglio di un giorno. */
    val tallChartHeight: Dp,
    /** Larghezza di una linguetta nella striscia dei giorni. */
    val dayTabWidth: Dp,
    /** Quante colonne per la griglia delle statistiche. */
    val statColumns: Int,
    val compact: Boolean,
    val landscape: Boolean,
) {
    /** In orizzontale non c'e' altezza da spendere: grafico e numeri si affiancano. */
    val sideBySide: Boolean get() = landscape
}

/**
 * Le misure per lo schermo corrente.
 *
 * Legge `LocalConfiguration`, che e' in punti indipendenti dalla densita' e
 * cambia da solo alla rotazione: non serve un `BoxWithConstraints` attorno a
 * tutto, e soprattutto non serve che ogni schermata se lo misuri per conto suo.
 */
@Composable
@ReadOnlyComposable
fun rememberMeteoLayout(): MeteoLayout {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val landscape = width > height
    return remember(width, height) {
        when {
            landscape -> MeteoLayout(
                gutter = 28.dp,
                gap = 14.dp,
                // In orizzontale l'altezza e' la risorsa scarsa: la cifra
                // prende meno, se no il resto non entra.
                heroFraction = 0.32f,
                chartHeight = 170.dp,
                tallChartHeight = 200.dp,
                dayTabWidth = 80.dp,
                statColumns = 4,
                compact = false,
                landscape = true,
            )
            width < 360 -> MeteoLayout(
                gutter = 14.dp,
                gap = 10.dp,
                heroFraction = 0.20f,
                chartHeight = 170.dp,
                tallChartHeight = 220.dp,
                dayTabWidth = 64.dp,
                statColumns = 2,
                compact = true,
                landscape = false,
            )
            width >= 600 -> MeteoLayout(
                gutter = 32.dp,
                gap = 20.dp,
                heroFraction = 0.24f,
                chartHeight = 260.dp,
                tallChartHeight = 320.dp,
                dayTabWidth = 96.dp,
                statColumns = 4,
                compact = false,
                landscape = false,
            )
            else -> MeteoLayout(
                gutter = 20.dp,
                gap = 14.dp,
                heroFraction = 0.22f,
                chartHeight = 200.dp,
                tallChartHeight = 260.dp,
                dayTabWidth = 80.dp,
                statColumns = 3,
                compact = false,
                landscape = false,
            )
        }
    }
}

/** L'area minima toccabile che Material pretende, e che qui mancava ovunque. */
val MinTouchTarget: Dp = 48.dp
