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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.CellaValore
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import java.util.Locale

/**
 * Sala III — La pioggia: le prossime dodici ore, una colonna per ora.
 *
 * Scrive sullo **stesso** `selectHour` della prima schermata: il giorno e' un
 * asse solo per tutta la galleria, e toccare una colonna qui sposta anche il
 * cielo e il numero dei gradi di Sala I.
 */
@Composable
fun SalaPioggiaScreen(
    state: UiState,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ore = state.hours
    val scelta = state.selectedHour
    // Le dodici ore **da quella scelta in avanti**: una finestra che scorre con
    // la barra, non un pezzo fisso di giornata.
    val finestra = (scelta until minOf(scelta + 12, ore.size)).toList()
    val pioggia = finestra.map { ore[it].precipitation ?: 0.0 }
    val massimo = (pioggia.maxOrNull() ?: 0.0).coerceAtLeast(0.4)
    val totale = pioggia.sum()
    val oraScelta = state.hour
    val bagnato = totale > 0.05

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "La pioggia", style = SalaType.cardTitle, color = palette.ink)
                Didascalia(
                    if (bagnato) {
                        "Precipitazioni nelle prossime dodici ore, per un totale di ${totale.virgola()} millimetri."
                    } else {
                        "Nessuna precipitazione attesa nelle prossime dodici ore."
                    },
                    palette,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(text = totale.virgola(), style = SalaType.numeroSala, color = palette.ink)
                Text(
                    text = "mm",
                    style = SalaType.hourLabel,
                    color = palette.accent,
                    modifier = Modifier.padding(start = 3.dp, top = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            finestra.forEachIndexed { i, indice ->
                val mm = pioggia[i]
                val quota = (mm / massimo).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelectHour(indice) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Una colonna a zero resta visibile: una colonna
                            // assente si legge come un dato mancante, non come
                            // un'ora asciutta.
                            .height((3f + quota * 85f).dp)
                            .clip(CircleShape)
                            .background(
                                if (mm > 0.01) {
                                    Brush.verticalGradient(
                                        listOf(SalaTokens.acquaChiara, SalaTokens.acquaScura),
                                    )
                                } else {
                                    Brush.verticalGradient(listOf(palette.maniglia, palette.maniglia))
                                },
                            ),
                    )
                    Text(
                        text = "%02d".format(ore[indice].time.hour),
                        style = SalaType.microLabel,
                        color = if (indice == scelta) palette.accent else palette.inkFaint,
                        maxLines = 1,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore(
                etichetta = "PROBABILITÀ",
                valore = oraScelta?.precipProbability?.let { "$it %" } ?: "--",
                palette = palette,
            )
            CellaValore(etichetta = "INTENSITÀ", valore = intensita(oraScelta?.precipitation), palette = palette)
            CellaValore(etichetta = "SUOLO", valore = if (totale > 4.0) "saturo" else "asciutto", palette = palette)
        }
    }
}

/**
 * Come si chiama questa quantita' d'acqua.
 *
 * Le soglie sono quelle di uso comune in millimetri l'ora: sotto mezzo
 * millimetro e' pioviggine, sopra i quattro e' un rovescio.
 */
private fun intensita(mm: Double?): String = when {
    mm == null || mm <= 0.01 -> "assente"
    mm < 0.5 -> "debole"
    mm < 2.0 -> "moderata"
    mm < 4.0 -> "forte"
    else -> "rovescio"
}

private fun Double.virgola(): String = String.format(Locale.ITALY, "%.1f", this)
