package io.github.noximiliencoxen.caelum.ui.sala.rooms

import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.noximiliencoxen.caelum.ui.sala.discoLunare
import io.github.noximiliencoxen.caelum.ui.sala.rooms.luna3d.LunaInterattiva
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.MoonPhase
import io.github.noximiliencoxen.caelum.data.MoonSegment
import io.github.noximiliencoxen.caelum.ui.sala.CellaValore
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Sala IV — La luna.
 *
 * La fase e' **quella vera di stanotte**, la stessa che il cielo mostra sopra il
 * pannello: un unico valore le governa tutte e due, quindi le due immagini non
 * possono divergere.
 *
 * **Sorge e cala non ci sono.** Il prototipo li scriveva ("17:12", "04:38") ma
 * Open-Meteo, nei dati che questa applicazione chiede, non li porta: al loro
 * posto ci sono tre numeri veri - quanto e' illuminata, quanti giorni ha, quando
 * torna piena. Un orario inventato sarebbe stato piu' simile al disegno e falso.
 */
@Composable
fun SalaLunaScreen(
    palette: SalaPalette,
    /**
     * Il giorno mostrato, non l'oggi del telefono: scorrendo alla notte di
     * giovedi' anche le fasi che seguono partono da li'. Da questo si ricava
     * la fase con la stessa funzione che la ricava per il cielo, quindi le due
     * immagini non possono divergere: un ingresso, una funzione.
     */
    giorno: LocalDate,
    modifier: Modifier = Modifier,
    /** Falso con le animazioni ridotte: niente molle ne' sensore sulla sfera. */
    movimento: Boolean = true,
) {
    // Quanti giorni avanti o indietro rispetto a quello mostrato, scelti col
    // cursore. Si riparte da zero quando cambia il giorno della shell.
    var spostamento by remember(giorno) { mutableIntStateOf(0) }
    val oggi = giorno.plusDays(spostamento.toLong())
    val fase = MoonPhase.at(oggi)
    val illuminata = MoonPhase.illumination(fase)
    val segmento = MoonSegment.of(fase)
    val eta = MoonPhase.ageDays(fase)
    val prossimaPiena = MoonPhase.nextDate(oggi, 0.5f)

    PannelloSala(palette = palette, modifier = modifier) {
        LunaInterattiva(
            fase = fase,
            descrizione = tr("${segmento.label.lowercase()}, ${(illuminata * 100f).roundToInt()} per cento illuminata", "${segmento.label.lowercase()}, ${(illuminata * 100f).roundToInt()} per cent lit"),
            palette = palette,
            movimento = movimento,
            modifier = Modifier.fillMaxWidth(),
        )

        CursoreGiorni(
            spostamento = spostamento,
            onCambia = { spostamento = it },
            palette = palette,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("La luna", "The moon"), style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = "${segmento.label.lowercase().replaceFirstChar { it.uppercase() }} · " +
                        "${(illuminata * 100f).roundToInt()} %",
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Didascalia(
                    descrizione(segmento),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore(tr("ILLUMINATA", "LIT"), "${(illuminata * 100f).roundToInt()} %", palette)
            CellaValore(tr("ETÀ", "AGE"), tr("${eta.roundToInt()} giorni", "${eta.roundToInt()} days"), palette)
            CellaValore(tr("PIENA", "FULL"), "${prossimaPiena.dayOfMonth} ${prossimaPiena.monthValue.mese()}", palette)
        }

        Text(
            text = tr("LE PROSSIME FASI", "THE NEXT PHASES"),
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Le quattro tappe del ciclo, ognuna alla sua prossima data. Sono
            // quattro **calcoli**, non quattro date scritte a mano.
            listOf(
                0.25f to tr("Primo q.", "First q."),
                0.5f to tr("Piena", "Full"),
                0.75f to tr("Ultimo q.", "Last q."),
                0f to tr("Nuova", "New"),
            ).sortedBy { (obiettivo, _) -> MoonPhase.daysUntil(oggi, obiettivo) }
                .forEach { (obiettivo, nome) ->
                    val quando = MoonPhase.nextDate(oggi, obiettivo)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(palette.chip)
                            .padding(top = 12.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Canvas(modifier = Modifier.size(26.dp)) { disegnaLuna(obiettivo, 1f) }
                        Text(
                            text = nome,
                            style = SalaType.giornoMax,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${quando.dayOfMonth} ${quando.monthValue.mese()}",
                            style = SalaType.giornoMin,
                            color = palette.inkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
        }
    }
}

/**
 * Il cursore dei giorni: un mese intorno al giorno mostrato.
 *
 * Trascinandolo la sfera qui sopra cresce e cala mentre si muove il dito, che e'
 * il modo piu' diretto di vedere un ciclo che dal vero dura un mese. I numeri
 * del pannello seguono, perche' vengono tutti dallo stesso giorno.
 */
@Composable
private fun CursoreGiorni(
    spostamento: Int,
    onCambia: (Int) -> Unit,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    spostamento == 0 -> tr("Oggi", "Today")
                    spostamento == 1 -> tr("Domani", "Tomorrow")
                    spostamento == -1 -> tr("Ieri", "Yesterday")
                    spostamento > 0 -> tr("Fra $spostamento giorni", "In $spostamento days")
                    else -> tr("${-spostamento} giorni fa", "${-spostamento} days ago")
                },
                style = SalaType.rowTitle,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            if (spostamento != 0) {
                Text(
                    text = tr("TORNA A OGGI", "BACK TO TODAY"),
                    style = SalaType.sectionLabel,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCambia(0) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Slider(
            value = spostamento.toFloat(),
            onValueChange = { onCambia(it.roundToInt()) },
            valueRange = -GIORNI_CURSORE.toFloat()..GIORNI_CURSORE.toFloat(),
            steps = GIORNI_CURSORE * 2 - 1,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.chip,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { contentDescription = tr("Giorno della luna", "Day of the moon") },
        )
    }
}

private const val GIORNI_CURSORE = 15

/** L'iconetta della fase sul cursore: la stessa luna del cielo, vedi `discoLunare`. */
private fun DrawScope.disegnaLuna(fase: Float, alpha: Float) {
    discoLunare(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, fase, alpha)
}

private fun descrizione(segmento: MoonSegment): String = when (segmento) {
    MoonSegment.NOVILUNIO -> tr("Il disco è fra noi e il sole: stanotte il cielo resta al buio, ed è la notte giusta per le stelle deboli.", "The disc is between us and the sun: tonight the sky stays dark, the right night for faint stars.")
    MoonSegment.CRESCENTE -> tr("Una falce sottile a occidente, bassa e breve: cala poco dopo il sole.", "A thin sickle in the west, low and brief: it sets soon after the sun.")
    MoonSegment.PRIMO_QUARTO -> tr("Metà disco illuminato, alto a sud dopo il tramonto: cala attorno a mezzanotte.", "Half the disc lit, high in the south after sunset: it sets around midnight.")
    MoonSegment.GIBBOSA_CRESCENTE -> tr("Quasi piena e alta per gran parte della notte: illumina bene fino a notte fonda.", "Nearly full and high for most of the night: it gives good light until late.")
    MoonSegment.PLENILUNIO -> tr("Piena: sorge col tramonto e cala con l'alba, in cielo per tutta la notte.", "Full: it rises at sunset and sets at dawn, in the sky all night.")
    MoonSegment.GIBBOSA_CALANTE -> tr("Ancora larga ma in ritardo: sorge a notte già cominciata e resta fino al mattino.", "Still broad but running late: it rises after nightfall and stays until morning.")
    MoonSegment.ULTIMO_QUARTO -> tr("Metà disco, dall'altra parte: sorge a notte fonda e resta visibile di prima mattina.", "Half the disc, on the other side: it rises late at night and stays visible in the early morning.")
    MoonSegment.CALANTE -> tr("Una falce che precede l'alba, bassa a oriente: l'ultima luce prima del novilunio.", "A sickle ahead of dawn, low in the east: the last light before the new moon.")
}

private fun Int.mese(): String = when (this) {
    1 -> tr("gen", "Jan")
    2 -> tr("feb", "Feb")
    3 -> tr("mar", "Mar")
    4 -> tr("apr", "Apr")
    5 -> tr("mag", "May")
    6 -> tr("giu", "Jun")
    7 -> tr("lug", "Jul")
    8 -> tr("ago", "Aug")
    9 -> tr("set", "Sep")
    10 -> tr("ott", "Oct")
    11 -> tr("nov", "Nov")
    else -> tr("dic", "Dec")
}
