package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.CellaValore
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.RigaSenzaOre
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sala VII — I raggi UV: l'indice ora per ora, sulle ore in cui esiste.
 *
 * Legge le ventiquattro ore vere e **lo stesso asse di tutte le altre**: nel
 * prototipo questa schermata aveva un cursore suo, separato, sulle sole ore
 * 6-21, e due cursori che dicono l'ora sono due verita' da tenere in fase.
 */
@Composable
fun SalaUvScreen(
    state: UiState,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Le ore del giorno mostrato: vedi la nota in Sala III.
    val ore = state.shownHours
    val scelta = state.selectedHour
    val corrente = ore.getOrNull(scelta)?.uvIndex ?: 0.0
    // Le ore in cui il sole c'e': fuori da quelle l'indice vale zero a ogni
    // latitudine, e quindici colonne a zero non sono un grafico.
    val finestra = ore.indices.filter { ore[it].time.hour in 5..20 }
    val picco = ore.indices.maxByOrNull { ore[it].uvIndex ?: 0.0 }
    val ozono = state.air?.ozone

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "I raggi UV", style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = nomeUv(corrente),
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = String.format(Locale.ITALY, "%.1f", corrente),
                style = SalaType.giant(46),
                color = palette.ink,
            )
        }

        if (ore.isEmpty()) RigaSenzaOre(palette, Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            finestra.forEach { indice ->
                val valore = ore[indice].uvIndex ?: 0.0
                // La scala e' l'indice stesso, non il massimo di giornata: un
                // 2 che tocca il soffitto perche' oggi non si va oltre
                // racconterebbe un sole che non c'e'.
                val quota = (valore / 11.0).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelectHour(indice) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((3f + quota * 80f).dp)
                            .clip(CircleShape)
                            .background(if (indice == scelta) palette.accent else coloreUv(valore, palette)),
                    )
                    // **Un'etichetta ogni due ore, e prima erano tutte e
                    // sedici.** Sedici colonne in duecento punti fanno dieci
                    // punti a colonna, e "05" ne vuole dodici: il risultato
                    // era una fila di "0" - "0 0 0 0 0 10 11 12" - dove le
                    // prime cinque ore erano tutte tagliate al primo carattere
                    // e l'ultima, "20", pure. Una scala oraria illeggibile
                    // sotto un grafico che si tocca per scegliere l'ora.
                    //
                    // Le colonne restano sedici: sono i dati. A sparire sono
                    // le etichette dispari, che una scala non ha bisogno di
                    // numerare ogni passo. **L'ora scelta fa eccezione**
                    // sempre, perche' quella non e' una tacca della scala: e'
                    // la risposta alla domanda "dove sono".
                    val ora = ore[indice].time.hour
                    if (ora % 2 == 0 || indice == scelta) {
                        // **L'etichetta esce dalla propria colonna, apposta.**
                        // Dimezzare le etichette non e' bastato: la colonna
                        // resta larga poco piu' di dieci punti, e li' dentro
                        // "12" ci sta mentre "06" no - la cifra uno e' piu'
                        // stretta delle altre, ed e' bastato quello perche'
                        // meta' scala si leggesse e meta' no. Coi puntini
                        // messi due commit fa la cosa si e' vista subito;
                        // prima sarebbe stata l'ennesima "0".
                        //
                        // `unbounded` le lascia misurare la propria larghezza
                        // vera e sbordare, centrata. Puo' farlo **perche' le
                        // colonne dispari un'etichetta non ce l'hanno**: lo
                        // spazio in cui sborda e' vuoto per costruzione.
                        Text(
                            text = "%02d".format(ora),
                            style = SalaType.microLabel,
                            color = if (indice == scelta) palette.accent else palette.inkFaint,
                            maxLines = 1,
                            modifier = Modifier.wrapContentWidth(
                                align = Alignment.CenterHorizontally,
                                unbounded = true,
                            ),
                        )
                    }
                }
            }
        }

        Didascalia(
            consiglio(corrente),
            palette,
            modifier = Modifier.padding(top = 15.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore(
                etichetta = "PICCO",
                valore = picco?.let { "%02d:00".format(ore[it].time.hour) } ?: "--",
                palette = palette,
            )
            CellaValore(etichetta = "AL SOLE", valore = esposizione(corrente), palette = palette)
            CellaValore(
                etichetta = "OZONO",
                valore = ozono?.let { "${it.roundToInt()} µg/m³" } ?: "--",
                palette = palette,
            )
        }
    }
}

/** I nomi della scala mondiale: gli stessi cinque gradini di ogni bollettino. */
private fun nomeUv(valore: Double): String = when {
    valore >= 11 -> "Estremo"
    valore >= 8 -> "Molto alto"
    valore >= 6 -> "Alto"
    valore >= 3 -> "Moderato"
    valore > 0.2 -> "Basso"
    else -> "Assente"
}

private fun coloreUv(valore: Double, palette: SalaPalette): Color = when {
    valore >= 8 -> SalaTokens.accent700
    valore >= 6 -> SalaTokens.accent500
    valore >= 3 -> SalaTokens.accent400
    valore > 0.2 -> SalaTokens.verde400
    else -> palette.maniglia
}

/**
 * Quanto si sta al sole prima di scottarsi.
 *
 * E' la regola pratica del tempo di eritema per una pelle chiara non protetta:
 * circa duecento diviso l'indice, in minuti. Un ordine di grandezza, e va detto
 * come tale - non un timer.
 */
private fun esposizione(valore: Double): String = when {
    valore <= 0.2 -> "libera"
    else -> "~${(200.0 / valore).roundToInt().coerceAtMost(240)} min"
}

private fun consiglio(valore: Double): String = when {
    valore >= 8 -> "Indice molto alto: nelle ore centrali servono cappello, occhiali e crema ad alto fattore, e l'ombra quando c'è."
    valore >= 6 -> "Serve protezione: crema ad alto fattore e pause all'ombra nelle ore centrali."
    valore >= 3 -> "Protezione consigliata se si resta fuori a lungo, soprattutto in quota o sull'acqua."
    valore > 0.2 -> "L'esposizione è sicura per tempi lunghi: nessuna protezione necessaria."
    else -> "Sole sotto l'orizzonte: nessuna radiazione ultravioletta."
}
