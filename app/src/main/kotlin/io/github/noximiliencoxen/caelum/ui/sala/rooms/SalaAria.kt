package io.github.noximiliencoxen.caelum.ui.sala.rooms

import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.AirBand
import io.github.noximiliencoxen.caelum.data.AirScale
import io.github.noximiliencoxen.caelum.data.OraAria
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.oraDueCifre
import kotlin.math.roundToInt

/**
 * Sala V — L'aria: l'indice, e i quattro inquinanti che lo compongono.
 *
 * Quattro e non cinque come nel prototipo: i pollini non stanno nell'endpoint
 * base di Open-Meteo, e tre pastiglie con dentro un numero inventato sarebbero
 * state la parte piu' convincente della schermata e l'unica falsa.
 */
@Composable
fun SalaAriaScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val aria = state.air
    val banda = aria?.band
    val tinta = coloreBanda(banda)

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                // L'anello dice **quanto** prima di dire quale: la porzione
                // colorata cresce con l'indice, e il resto resta il grigio
                // dell'interfaccia.
                val quota = ((aria?.index ?: 0) / 100f).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.size(96.dp)) {
                    val spessore = 11.dp.toPx()
                    drawCircle(
                        color = palette.maniglia,
                        radius = size.minDimension / 2f - spessore / 2f,
                        style = Stroke(width = spessore),
                    )
                    if (quota > 0f) {
                        drawArc(
                            color = tinta,
                            startAngle = -90f,
                            sweepAngle = 360f * quota,
                            useCenter = false,
                            topLeft = Offset(spessore / 2f, spessore / 2f),
                            size = Size(size.width - spessore, size.height - spessore),
                            style = Stroke(width = spessore),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = aria?.index?.toString() ?: "--",
                        style = SalaType.cardTitle,
                        color = palette.ink,
                    )
                    Text(text = "AQI", style = SalaType.microLabel, color = palette.inkFaint)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("L'aria", "Air"), style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = banda?.label?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ?: if (state.airUnavailable) tr("Non disponibile", "Not available") else tr("In arrivo", "On its way"),
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Didascalia(
                    descrizione(banda, state.airUnavailable),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ── L'andamento della giornata ───────────────────────────────────────
        //
        // **Il dato c'era e non si chiedeva.** Lo stesso endpoint che da' il
        // valore di adesso da' anche le ventiquattro ore, e questa sala
        // mostrava un numero solo: "27, discreta" - vero, e muto. Un indice che
        // alle otto vale venti e alle quattordici sessanta racconta una
        // giornata; detto una volta sola non dice **quando uscire**, che e'
        // l'unica domanda che ci si fa guardando l'aria.
        val ore = remember(aria, state.selectedDay, state.detailDay?.date) {
            val giorno = state.detailDay?.date
            aria?.oreOggi.orEmpty().filter { giorno == null || it.ora.toLocalDate() == giorno }
        }
        if (ore.size >= 6 && aria != null) {
            Text(
                text = tr("NELLA GIORNATA", "THROUGH THE DAY"),
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
            )
            AndamentoAria(
                ore = ore,
                // **La scala dev'essere quella del numero in cima.** L'indice
                // europeo va da zero a cento, quello americano da zero a
                // cinquecento: colorare le colonne col metro sbagliato
                // dipingerebbe di verde una giornata pessima, o viceversa.
                scala = aria.scale,
                oraScelta = state.detailHour?.time?.hour,
                palette = palette,
            )
            Didascalia(
                consiglioOrario(ore, state.detailHour?.time?.hour),
                palette,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // ── Gli inquinanti, col loro limite ──────────────────────────────────
        val dominante = aria?.dominante
        Text(
            text = tr("RISPETTO AL LIMITE OMS", "AGAINST THE WHO LIMIT"),
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // **I limiti sono quelli dell'OMS del 2021, non quelli di legge.**
            // Erano le soglie europee - venticinque per il PM 2,5, cinquanta
            // per il PM 10 - che sono il confine di cio' che e' punibile, non
            // di cio' che fa male: l'OMS mette quindici e quarantacinque. A chi
            // sta decidendo se andare a correre serve il secondo metro.
            // Il confronto e' sul **valore**, non sul nome: due stringhe
            // uguali scritte in due file diversi si scollano al primo che le
            // ritocca, e il grassetto finirebbe sulla riga sbagliata senza che
            // niente si rompa.
            BarraInquinante("PM 2.5", aria?.pm25, 15.0, palette, dominante?.valore == aria?.pm25)
            BarraInquinante("PM 10", aria?.pm10, 45.0, palette, dominante?.valore == aria?.pm10)
            BarraInquinante("O₃", aria?.ozone, 100.0, palette, dominante?.valore == aria?.ozone)
            BarraInquinante(
                "NO₂",
                aria?.nitrogenDioxide,
                25.0,
                palette,
                dominante?.valore == aria?.nitrogenDioxide,
            )
        }
        if (dominante != null) {
            // L'indice europeo e' il **peggiore** dei suoi componenti, non la
            // loro media: dire "27, discreta" senza dire chi l'ha deciso lascia
            // fuori la parte utile.
            Didascalia(
                tr(
                    "Oggi l'indice lo decide " + dominante.inFrase +
                        ", al " + (dominante.quota * 100).roundToInt() + "% del limite.",
                    "Today the index is driven by " + dominante.inFrase +
                        ", at " + (dominante.quota * 100).roundToInt() + "% of the limit.",
                ),
                palette,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * L'indice ora per ora, in colonne.
 *
 * Stessa forma del grafico dei raggi UV, e non per pigrizia: sono la stessa
 * domanda - **a che ora conviene uscire** - e due disegni diversi per la stessa
 * domanda costringono a impararli tutti e due.
 *
 * Le colonne partono tutte dalla stessa linea di base, che sembra ovvio e non
 * lo era: nella sala UV le colonne con etichetta si alzavano di quanto misurava
 * l'etichetta, e il grafico diceva una cosa falsa per un errore di
 * impaginazione (CONTESTO 16.2).
 */
@Composable
private fun AndamentoAria(
    ore: List<OraAria>,
    scala: AirScale,
    oraScelta: Int?,
    palette: SalaPalette,
) {
    val massimo = (ore.maxOfOrNull { it.indice } ?: 1).coerceAtLeast(20)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ore.forEach { o ->
                val quota = (o.indice.toFloat() / massimo).coerceIn(0.08f, 1f)
                val mia = oraScelta != null && o.ora.hour == oraScelta
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(quota)
                        .clip(CircleShape)
                        .background(
                            coloreBanda(scala.band(o.indice))
                                .copy(alpha = if (mia) 1f else 0.55f),
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ore.forEach { o ->
                Text(
                    text = if (o.ora.hour % 6 == 0) oraDueCifre(o.ora.hour) else "",
                    style = SalaType.microLabel,
                    color = if (oraScelta != null && o.ora.hour == oraScelta) palette.accent
                    else palette.inkFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    // **La casella di un'ora e' piu' stretta della sua
                    // etichetta.** Ventiquattro colonne su seicento pixel fanno
                    // venticinque pixel l'una, e "06" ne vuole di piu': il
                    // primo scatto mostrava uno zero sopra e un sei sotto.
                    // `unbounded` lascia il testo sforare nelle caselle
                    // accanto, che sono vuote per costruzione - le etichette
                    // stanno una ogni sei ore.
                    //
                    // E' lo stesso difetto del grafico UV di CONTESTO 16.2, in
                    // un'altra forma: li' l'etichetta alzava la colonna, qui la
                    // colonna stringe l'etichetta. Tutte e due nascono dal dare
                    // a un'etichetta la larghezza del dato che descrive.
                    modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/**
 * Una riga che dice **quando**, non com'e'.
 *
 * Il valore e la banda li dicono gia' il numero e la parola sopra. Qui serve
 * l'unica cosa che i due non dicono: fra le ore che restano, quale e' la
 * migliore. Guarda avanti dall'ora scelta e non su tutta la giornata, perche'
 * un consiglio che indica le sei del mattino alle sette di sera e' un consiglio
 * per ieri.
 */
private fun consiglioOrario(ore: List<OraAria>, oraScelta: Int?): String {
    val da = oraScelta ?: ore.firstOrNull()?.ora?.hour ?: return ""
    val avanti = ore.filter { it.ora.hour >= da }
    if (avanti.size < 3) return tr("La giornata è quasi finita: l'indice non cambia più di molto.", "The day is nearly over: the index won't change much.")
    val migliore = avanti.minByOrNull { it.indice } ?: return ""
    val peggiore = avanti.maxByOrNull { it.indice } ?: return ""
    if (peggiore.indice - migliore.indice < 8) {
        return tr("L'aria resta com'è per tutte le ore che restano: nessun momento è migliore di un altro.", "The air stays as it is for the rest of the day: no hour is better than another.")
    }
    return tr("Fra le ore che restano la migliore è verso le %02d:00, la peggiore verso le %02d:00.", "Of the hours left, the best is around %02d:00, the worst around %02d:00.")
        .format(migliore.ora.hour, peggiore.ora.hour)
}

@Composable
private fun BarraInquinante(
    nome: String,
    valore: Double?,
    soglia: Double,
    palette: SalaPalette,
    /** Quello che comanda l'indice: il nome va in grassetto, non in un colore
     *  in piu'. La barra ha gia' un colore che dice quanto, e un secondo
     *  colore che dice quale si leggerebbe come un terzo valore. */
    dominante: Boolean = false,
) {
    val quota = valore?.let { (it / soglia).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val tinta = if (quota > 0.7f) SalaTokens.accent400 else SalaTokens.verde400
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = nome,
            style = if (dominante) SalaType.rowTitle else SalaType.hourLabel,
            color = palette.ink,
            modifier = Modifier.width(56.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(CircleShape)
                .background(palette.maniglia),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(quota)
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(tinta),
            )
        }
        Text(
            text = valore?.let { "${it.roundToInt()} µg/m³" } ?: "--",
            style = SalaType.rowNote,
            color = palette.inkSoft,
            textAlign = TextAlign.End,
            modifier = Modifier.width(66.dp),
        )
    }
}

private fun coloreBanda(banda: AirBand?): Color = when (banda) {
    null -> SalaTokens.neutral400
    AirBand.BUONA, AirBand.DISCRETA -> SalaTokens.verde400
    AirBand.MEDIA -> SalaTokens.accent400
    AirBand.SCARSA -> SalaTokens.accent500
    else -> SalaTokens.accent700
}

private fun descrizione(banda: AirBand?, nonDisponibile: Boolean): String = when {
    nonDisponibile -> tr("La misura dell'aria non è arrivata: la stazione più vicina non ha risposto.", "The air measurement didn't arrive: the nearest station didn't answer.")
    banda == null -> tr("La misura dell'aria sta arrivando.", "The air measurement is on its way.")
    banda == AirBand.BUONA -> tr("Particolato basso: nessuna precauzione necessaria, nemmeno per chi è sensibile.", "Low particulates: no precautions needed, even for sensitive people.")
    banda == AirBand.DISCRETA -> tr("Aria accettabile: chi ha problemi respiratori eviti lo sforzo prolungato all'aperto.", "Acceptable air: people with breathing problems should avoid long exertion outdoors.")
    banda == AirBand.MEDIA -> tr("Chi è sensibile faccia attenzione: meglio rimandare l'attività intensa all'aperto.", "Sensitive people take care: better postpone intense outdoor activity.")
    banda == AirBand.SCARSA -> tr("Aria scarsa: limitare lo sforzo all'aperto, soprattutto nelle ore centrali.", "Poor air: limit outdoor exertion, especially around midday.")
    else -> tr("Aria pessima: restare al chiuso quando possibile e tenere le finestre chiuse.", "Very poor air: stay indoors when possible and keep windows shut.")
}
