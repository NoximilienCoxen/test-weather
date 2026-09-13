package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import kotlin.math.roundToInt

/**
 * Sala VI — Il vento: da dove viene, quanto forte, e cosa fa nelle prossime sei ore.
 *
 * La rosa non e' un grafico ma un **verso**: la lancetta punta da dove il vento
 * arriva, che e' il modo in cui lo si dice a voce ("viene da nord-est") e non
 * quello in cui lo scrive il dato (i gradi di provenienza).
 */
@Composable
fun SalaVentoScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val ore = state.hours
    val scelta = state.selectedHour
    val ora = state.hour
    val unita = state.windUnit
    val velocita = ora?.windSpeed
    val raffiche = ora?.windGusts
    val direzione = ora?.windDirection

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(palette.chip),
                contentAlignment = Alignment.Center,
            ) {
                listOf("N" to Alignment.TopCenter, "S" to Alignment.BottomCenter,
                    "O" to Alignment.CenterStart, "E" to Alignment.CenterEnd).forEach { (lettera, dove) ->
                    Text(
                        text = lettera,
                        style = SalaType.microLabel,
                        color = palette.inkFaint,
                        modifier = Modifier.align(dove).padding(8.dp),
                    )
                }
                Canvas(
                    modifier = Modifier
                        .size(104.dp)
                        .rotate((direzione?.toFloat() ?: 0f) + 180f),
                ) {
                    // La lancetta parte dal centro e va verso l'alto; la
                    // rotazione la porta dove serve. Mezzo giro in piu' perche'
                    // il dato dice **da dove viene**, non dove va.
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawRoundRect(
                        color = palette.accent,
                        topLeft = Offset(cx - 2.5.dp.toPx(), cy - 44.dp.toPx()),
                        size = Size(5.dp.toPx(), 44.dp.toPx()),
                        cornerRadius = CornerRadius(2.5.dp.toPx()),
                    )
                    drawCircle(color = palette.accent, radius = 7.dp.toPx())
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Il vento", style = SalaType.cardTitle, color = palette.ink)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = velocita?.let { unita.from(it).roundToInt().toString() } ?: "--",
                        style = SalaType.numeroSala,
                        color = palette.ink,
                    )
                    Text(
                        text = "${unita.label} ${Wmo.windDirection(direzione)}",
                        style = SalaType.hourLabel,
                        color = palette.accent,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                Didascalia(
                    // Le soglie vogliono i km/h; il display vuole l'unita'
                    // scelta. Passarli mescolati faceva dire "burrasca" a
                    // sessanta nodi e "brezza" a sessanta km/h.
                    nota(
                        kmh = velocita?.let { it * 3.6 },
                        raffiche = raffiche?.let { unita.from(it).roundToInt() },
                        unita = unita.label,
                    ),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Text(
            text = "LE PROSSIME SEI ORE",
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
        )
        val finestra = (scelta + 1..scelta + 6).filter { it in ore.indices }
        val massimo = finestra.mapNotNull { ore[it].windSpeed }.maxOrNull()?.coerceAtLeast(0.5) ?: 1.0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            finestra.forEach { indice ->
                val ms = ore[indice].windSpeed ?: 0.0
                val quota = (ms / massimo).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.chip)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = "%02d".format(ore[indice].time.hour),
                        style = SalaType.microLabel,
                        color = palette.inkSoft,
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((10f + quota * 36f).dp)
                            .clip(CircleShape)
                            .background(
                                if (unita.from(ms) > 24) SalaTokens.accent500 else SalaTokens.verde400,
                            ),
                    )
                    Text(
                        text = unita.from(ms).roundToInt().toString(),
                        style = SalaType.giornoMax,
                        color = palette.ink,
                    )
                }
            }
        }
    }
}

/**
 * Cosa vuol dire questa velocita', in cose che si vedono fuori.
 *
 * Le soglie sono quelle della scala Beaufort ridotte a tre gradini, e sono in
 * chilometri orari **qualunque** unita' si stia mostrando: il fenomeno non
 * cambia col modo di misurarlo.
 */
private fun nota(kmh: Double?, raffiche: Int?, unita: String): String {
    val coda = raffiche?.let { " Raffiche fino a $it $unita." } ?: ""
    val corpo = when {
        kmh == null -> "Vento non disponibile per quest'ora."
        kmh < 6 -> "Aria quasi ferma: il fumo sale dritto."
        kmh < 20 -> "Brezza leggera: si muovono le foglie, non i rami."
        kmh < 39 -> "Vento teso: i rami si piegano e l'ombrello diventa scomodo."
        kmh < 62 -> "Vento forte: attenzione agli oggetti esposti sui balconi."
        else -> "Burrasca: meglio non stare sotto gli alberi."
    }
    return corpo + coda
}
