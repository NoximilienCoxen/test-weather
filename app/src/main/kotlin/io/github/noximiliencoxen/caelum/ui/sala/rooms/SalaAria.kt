package io.github.noximiliencoxen.caelum.ui.sala.rooms

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Il polline, che il prototipo aveva e qui mancava, e' arrivato quando si e'
 * scoperto che lo stesso endpoint dell'aria lo serve, per l'Europa: prima tre
 * pastiglie con dentro un numero inventato sarebbero state la parte piu'
 * convincente della schermata e l'unica falsa. Adesso c'e', col dato vero, e
 * dove il dato non c'e' la sezione non compare.
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
            // **Settantadue punti e non novantasei**: la sala e' cresciuta del
            // polline, e l'anello era la cosa piu' grande della scheda per il
            // dato piu' breve.
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                // L'anello dice **quanto** prima di dire quale: la porzione
                // colorata cresce con l'indice, e il resto resta il grigio
                // dell'interfaccia.
                val quota = ((aria?.index ?: 0) / 100f).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.size(72.dp)) {
                    val spessore = 8.dp.toPx()
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
                Text(text = "L'aria", style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = banda?.label?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ?: if (state.airUnavailable) "Non disponibile" else "In arrivo",
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        // La frase sotto il numero solo quando c'e' qualcosa da fare: con
        // l'aria buona o discreta ripeteva la parola accanto all'anello in tre
        // righe, e tre righe erano un quinto della scheda.
        if (banda == null || banda.ordinal > AirBand.DISCRETA.ordinal || state.airUnavailable) {
            Didascalia(
                descrizione(banda, state.airUnavailable),
                palette,
                modifier = Modifier.padding(top = 8.dp),
            )
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
                text = "NELLA GIORNATA",
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
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
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ── Gli inquinanti, col loro limite ──────────────────────────────────
        //
        // **In una griglia due per due, e senza la frase sotto.** Erano quattro
        // righe a tutta larghezza piu' "oggi l'indice lo decide l'ozono, al
        // novantacinque per cento": la stessa informazione ora la dice la
        // cella in grassetto, con la sua percentuale.
        //
        // **I limiti sono quelli dell'OMS del 2021, non quelli di legge.**
        // Erano le soglie europee - venticinque per il PM 2,5, cinquanta per il
        // PM 10 - che sono il confine di cio' che e' punibile, non di cio' che
        // fa male: l'OMS mette quindici e quarantacinque. A chi sta decidendo
        // se andare a correre serve il secondo metro.
        //
        // Il confronto col dominante e' sul **valore**, non sul nome: due
        // stringhe uguali scritte in due file diversi si scollano al primo che
        // le ritocca.
        val dominante = aria?.dominante
        Text(
            text = "RISPETTO AL LIMITE OMS",
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CellaInquinante("PM 2.5", aria?.pm25, 15.0, palette, dominante?.valore == aria?.pm25, Modifier.weight(1f))
                CellaInquinante("PM 10", aria?.pm10, 45.0, palette, dominante?.valore == aria?.pm10, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CellaInquinante("O₃", aria?.ozone, 100.0, palette, dominante?.valore == aria?.ozone, Modifier.weight(1f))
                CellaInquinante(
                    "NO₂",
                    aria?.nitrogenDioxide,
                    25.0,
                    palette,
                    dominante?.valore == aria?.nitrogenDioxide,
                    Modifier.weight(1f),
                )
            }
        }

        // ── Il polline ───────────────────────────────────────────────────────
        // Dallo stesso endpoint e dalla stessa richiesta: vedi `Polline.kt`.
        SezionePolline(giorni = state.pollineMostrato, palette = palette)
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
    if (avanti.size < 3) return "La giornata è quasi finita: l'indice non cambia più di molto."
    val migliore = avanti.minByOrNull { it.indice } ?: return ""
    val peggiore = avanti.maxByOrNull { it.indice } ?: return ""
    if (peggiore.indice - migliore.indice < 8) {
        return "L'aria resta com'è per tutte le ore che restano: nessun momento è migliore di un altro."
    }
    return "Fra le ore che restano la migliore è verso le %02d:00, la peggiore verso le %02d:00."
        .format(migliore.ora.hour, peggiore.ora.hour)
}

/**
 * Un inquinante in una cella: il nome, il valore, e una barra sottile che dice
 * quanto del limite occupa.
 *
 * Quello che comanda l'indice ha il nome in grassetto e la **percentuale** al
 * posto dei microgrammi: e' l'unico per cui la domanda "quanto manca al
 * limite" ha una risposta che cambia qualcosa.
 */
@Composable
private fun CellaInquinante(
    nome: String,
    valore: Double?,
    soglia: Double,
    palette: SalaPalette,
    dominante: Boolean,
    modifier: Modifier = Modifier,
) {
    val quota = valore?.let { (it / soglia).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val tinta = if (quota > 0.7f) SalaTokens.accent400 else SalaTokens.verde400
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.chip)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = nome,
                style = if (dominante) SalaType.rowTitle else SalaType.hourLabel,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    valore == null -> "--"
                    dominante -> "${(valore / soglia * 100).roundToInt()}%"
                    else -> "${valore.roundToInt()} µg/m³"
                },
                style = SalaType.rowNote,
                color = if (dominante) palette.ink else palette.inkSoft,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(palette.maniglia),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(quota)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(tinta),
            )
        }
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
    nonDisponibile -> "La misura dell'aria non è arrivata: la stazione più vicina non ha risposto."
    banda == null -> "La misura dell'aria sta arrivando."
    banda == AirBand.BUONA -> "Particolato basso: nessuna precauzione necessaria, nemmeno per chi è sensibile."
    banda == AirBand.DISCRETA -> "Aria accettabile: chi ha problemi respiratori eviti lo sforzo prolungato all'aperto."
    banda == AirBand.MEDIA -> "Chi è sensibile faccia attenzione: meglio rimandare l'attività intensa all'aperto."
    banda == AirBand.SCARSA -> "Aria scarsa: limitare lo sforzo all'aperto, soprattutto nelle ore centrali."
    else -> "Aria pessima: restare al chiuso quando possibile e tenere le finestre chiuse."
}
