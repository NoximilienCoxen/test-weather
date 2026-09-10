package io.github.noximiliencoxen.caelum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType

/**
 * I dati chiave della scheda, in fila e separati da un filo verticale.
 *
 * **Il filo e' tutta la differenza fra tre colonne e una riga.** Prima i tre
 * numeri erano tre `Column` con `SpaceBetween` in mezzo: si leggevano come tre
 * oggetti appoggiati sotto la scheda, e su schermo largo si allontanavano tanto
 * da non stare piu' insieme. Un divisore sottile li dichiara parti di un blocco
 * solo, che e' cio' che chiede una composizione da galleria - la didascalia sta
 * sotto il quadro ed e' **una** didascalia, non tre.
 *
 * Il filo prende l'altezza dalle colonne (`IntrinsicSize.Min`) invece di averne
 * una scritta a mano: con il carattere di sistema ingrandito le due righe
 * crescono, e un divisore alto un numero fisso resterebbe un trattino in mezzo
 * al vuoto. E' inserito sopra e sotto, cosi' non tocca ne' l'etichetta ne' il
 * bordo della scheda.
 *
 * **Le etichette usano [MeteoType.kicker]**, lo stesso stile dell'occhiello in
 * cima alla scheda: sono lo stesso mestiere ai due capi della composizione -
 * dire di cosa si sta parlando, in piccolo e tracciato largo. I valori usano
 * `metric`, che porta le cifre a larghezza fissa: tre numeri affiancati che
 * cambiano scorrendo le ore devono restare incolonnati invece di ballare.
 *
 * I colori si leggono da `LocalMeteoColors`, che dentro la scheda in vetro e'
 * gia' quella derivata dal vetro: qui non c'e' niente da sapere sul fondo, e
 * questo componente funziona sia sul cielo sia sul vetro senza saperlo.
 */
@Composable
fun MetricsBar(
    entries: List<Pair<String, String>>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val colors = LocalMeteoColors.current
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        entries.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(RULE)
                        .fillMaxHeight()
                        .padding(vertical = RULE_INSET)
                        .background(colors.line.copy(alpha = RULE_ALPHA)),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MeteoType.kicker,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = value,
                    style = MeteoType.metric,
                    color = accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}

private val RULE = 1.dp
private val RULE_INSET = 3.dp
private const val RULE_ALPHA = 0.45f
