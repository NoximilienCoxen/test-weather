package io.github.noximiliencoxen.caelum.ui.temperature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.ui.common.centerOn
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La striscia dei giorni, in cima al foglio del dettaglio.
 *
 * **Veniva da `DayDetailScreen`, che non c'e' piu'.** Il giorno non era una
 * schermata: era un asse. Le sei pagine leggevano gia' `state.selectedDay` -
 * `pageDay`, `pageHours`, `detailHour` sono li' da sempre - e nessuno gliene
 * passava mai uno diverso da oggi, mentre accanto viveva una seconda schermata
 * che ripeteva grafico, statistiche e probabilita' per dire le stesse cose di
 * un altro giorno. Adesso l'asse e' dichiarato: qui si sceglie **quale
 * giorno**, il carosello sceglie **quale grandezza**, e le due domande non si
 * contendono niente.
 *
 * **Sta dove stavano i pallini del carosello**, e non e' uno scambio a caso: i
 * pallini dicevano su quale pagina si fosse, che e' la stessa cosa che dice
 * gia' la fila di pillole sopra, accesa e portata al centro dal
 * trascinamento. Al loro posto c'e' l'unica cosa che nessuno diceva - quale
 * giorno si sta guardando - e l'altezza del foglio non cambia di un punto.
 *
 * `animateScrollToItem` porterebbe la linguetta al **bordo** d'ingresso e non
 * al centro: aprendo sabato ci si ritrovava la sua linguetta incollata a
 * sinistra, senza piu' modo di sapere dove si fosse nella settimana.
 * [centerOn] misura da `layoutInfo` e la porta davvero in mezzo.
 */
@Composable
internal fun DayStrip(
    days: List<DayForecast>,
    selected: Int,
    width: Dp,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    val listState = rememberLazyListState()
    // Animato, al contrario di quando c'era il carosello dei giorni: li' la
    // striscia inseguiva un dito che stava gia' muovendo la pagina, e animare
    // avrebbe voluto dire due movimenti sovrapposti. Qui il giorno cambia di
    // scatto - un tocco su una linguetta, o uno su una colonna della settimana
    // nella schermata sotto - e senza animazione la striscia si teletrasporta.
    LaunchedEffect(listState, days.size, selected) {
        runCatching { listState.centerOn(selected.toFloat(), animate = true) }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(days, key = { _, day -> day.date.toString() }) { index, day ->
            val active = index == selected
            val color = if (active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .width(width)
                    .clickable(
                        role = Role.Tab,
                        onClickLabel = SCEGLI,
                        onClick = { onSelect(index) },
                    )
                    // Una voce sola per chi ascolta: letta a pezzi sarebbe
                    // "sabato", "13", "set", tre fermate per una data.
                    // `clearAndSetSemantics` cancella anche l'azione del
                    // `clickable`, quindi va rimessa a mano.
                    .clearAndSetSemantics {
                        contentDescription = "${day.label} ${day.date.dayOfMonth}"
                        role = Role.Tab
                        onClick(label = SCEGLI) { onSelect(index); true }
                    }
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    maxLines = 1,
                )
                Text(
                    text = "%02d".format(day.date.dayOfMonth),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    maxLines = 1,
                )
                Text(
                    text = day.date.format(MONTH_FORMAT).uppercase(Locale.ITALIAN),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                )
                Spacer(
                    Modifier
                        .padding(top = 8.dp)
                        .width(32.dp)
                        .height(2.dp)
                        .background(if (active) color else Color.Transparent),
                )
            }
        }
    }
}

private val MONTH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)

private const val SCEGLI = "Mostra questo giorno"
