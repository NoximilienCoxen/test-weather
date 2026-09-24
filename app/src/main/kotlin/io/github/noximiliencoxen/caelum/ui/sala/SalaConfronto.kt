package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.lingua.Lingua
import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Forecast
import io.github.noximiliencoxen.caelum.data.Place
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.data.key
import io.github.noximiliencoxen.caelum.ui.UiState
import kotlin.math.roundToInt

/**
 * Le citta' salvate una accanto all'altra.
 *
 * Una colonna per citta', e le stesse righe alla stessa altezza in ogni
 * colonna: chi confronta scorre con l'occhio **in orizzontale** lungo una riga
 * - la pioggia di oggi a Noceto, a Parma, a Bologna - e le righe devono
 * trovarsi dove l'occhio le aspetta. Il valore migliore di ogni riga (il piu'
 * caldo, il piu' asciutto, il piu' calmo) ha il fondo in accento, cosi' la
 * risposta a "dove si sta meglio oggi?" si vede prima di leggerla.
 *
 * Tocca una colonna: quella citta' diventa quella dell'app.
 */
@Composable
fun SalaConfrontoScreen(
    state: UiState,
    palette: SalaPalette,
    onPick: (Place) -> Unit,
    onClose: () -> Unit,
) {
    val posti = postiDaConfrontare(state)
    val previsioni = posti.map { state.favoritesForecast[it.key] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(top = 12.dp, bottom = 24.dp),
    ) {
        IntestazioneServizio(
            titolo = tr("Confronto", "Compare"),
            palette = palette,
            onIndietro = onClose,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
        if (posti.size < 2) {
            Text(
                text = tr("Salva almeno due località in «Le località» per confrontarle qui.", "Save at least two places in «Locations» to compare them here."),
                style = SalaType.body,
                color = palette.inkSoft,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
            )
            return@Column
        }
        Text(
            text = tr("Oggi, fianco a fianco. Tocca una città per aprirla.", "Today, side by side. Tap a city to open it."),
            style = SalaType.footnote,
            color = palette.inkFaint,
            modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 14.dp),
        )

        val righe = righeConfronto(state)
        Row(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            posti.forEachIndexed { i, posto ->
                ColonnaConfronto(
                    posto = posto,
                    previsione = previsioni[i],
                    corrente = posto.key == state.place.key,
                    righe = righe,
                    migliori = righe.map { riga -> migliore(riga, previsioni) },
                    indice = i,
                    state = state,
                    palette = palette,
                    onClick = { onPick(posto) },
                )
            }
        }
    }
}

/** La citta' che si guarda, se non e' fra le salvate, e poi le salvate. */
internal fun postiDaConfrontare(state: UiState): List<Place> {
    val salvate = state.favorites
    return if (salvate.any { it.key == state.place.key }) salvate else listOf(state.place) + salvate
}

/**
 * Una riga del confronto: come si legge il numero da una previsione, e quale
 * dei numeri e' il migliore (piu' alto o piu' basso). [meglio] nullo: nessuno
 * vince, la riga informa e basta.
 */
internal class RigaConfronto(
    val etichetta: String,
    val valore: (Forecast) -> Double?,
    val testo: (Double) -> String,
    val meglio: Meglio?,
)

internal enum class Meglio { ALTO, BASSO }

private fun righeConfronto(state: UiState): List<RigaConfronto> {
    fun gradi(v: Double) = "${state.unit.from(v).roundToInt()}°"
    return listOf(
        RigaConfronto(tr("ADESSO", "NOW"), { it.current.temperature }, ::gradi, null),
        RigaConfronto(tr("MASSIMA", "HIGH"), { it.days.firstOrNull()?.tempMax }, ::gradi, Meglio.ALTO),
        RigaConfronto(tr("MINIMA", "LOW"), { it.days.firstOrNull()?.tempMin }, ::gradi, null),
        RigaConfronto(
            tr("PIOGGIA", "RAIN"),
            { it.days.firstOrNull()?.precipitationSum },
            { String.format(Lingua.locale, "%.1f mm", it) },
            Meglio.BASSO,
        ),
        RigaConfronto(
            tr("PROBABILITÀ", "CHANCE"),
            { it.days.firstOrNull()?.precipProbability?.toDouble() },
            { "${it.roundToInt()}%" },
            Meglio.BASSO,
        ),
        RigaConfronto(
            tr("VENTO", "WIND"),
            { it.days.firstOrNull()?.windMax },
            { "${state.windUnit.from(it).roundToInt()} ${state.windUnit.label}" },
            Meglio.BASSO,
        ),
        RigaConfronto(
            "UV",
            { it.days.firstOrNull()?.uvMax },
            { String.format(Lingua.locale, "%.1f", it) },
            null,
        ),
    )
}

/**
 * Chi vince la riga, come indice di colonna; nullo se la riga non ha un
 * meglio, se i dati sono meno di due, o se sono tutti uguali (un pareggio
 * non e' una vittoria da segnalare).
 */
internal fun migliore(riga: RigaConfronto, previsioni: List<Forecast?>): Int? {
    val verso = riga.meglio ?: return null
    val valori = previsioni.map { p -> p?.let(riga.valore) }
    val presenti = valori.filterNotNull()
    if (presenti.size < 2 || presenti.distinct().size < 2) return null
    val scelto = if (verso == Meglio.ALTO) presenti.max() else presenti.min()
    return valori.indexOf(scelto)
}

@Composable
private fun ColonnaConfronto(
    posto: Place,
    previsione: Forecast?,
    corrente: Boolean,
    righe: List<RigaConfronto>,
    migliori: List<Int?>,
    indice: Int,
    state: UiState,
    palette: SalaPalette,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (corrente) palette.accent.copy(alpha = 0.16f) else palette.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = posto.name,
            style = SalaType.value,
            color = if (corrente) palette.accent else palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.maniglia),
                contentAlignment = Alignment.Center,
            ) {
                previsione?.let { IconaMeteo(glifoDi(it.current.weatherCode, null), palette) }
            }
            Text(
                text = previsione?.current?.weatherCode?.let { Wmo.condition(it).lowercase() } ?: "…",
                style = SalaType.rowNote,
                color = palette.inkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        righe.forEachIndexed { r, riga ->
            val v = previsione?.let(riga.valore)
            val vince = migliori[r] == indice
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (vince) palette.accent.copy(alpha = 0.22f) else palette.panel.copy(alpha = 0f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(text = riga.etichetta, style = SalaType.microLabel, color = palette.inkFaint, maxLines = 1)
                Text(
                    text = v?.let(riga.testo) ?: if (previsione == null) "…" else "--",
                    style = SalaType.value,
                    color = if (vince) palette.accent else palette.ink,
                    maxLines = 1,
                )
            }
        }
        // I prossimi tre giorni, in piccolo: bastano a dire dove migliora.
        previsione?.days?.drop(1)?.take(3)?.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = g.label, style = SalaType.microLabel, color = palette.inkFaint, modifier = Modifier.width(40.dp))
                Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    IconaMeteo(glifoDi(g.weatherCode, null), palette)
                }
                Text(
                    text = listOfNotNull(
                        g.tempMax?.let { "${state.unit.from(it).roundToInt()}°" },
                        g.tempMin?.let { "${state.unit.from(it).roundToInt()}°" },
                    ).joinToString(" / "),
                    style = SalaType.rowNote,
                    color = palette.inkSoft,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
