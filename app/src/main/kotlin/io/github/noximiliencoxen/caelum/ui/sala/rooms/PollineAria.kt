package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.GiornoPolline
import io.github.noximiliencoxen.caelum.data.TipoPolline
import io.github.noximiliencoxen.caelum.data.nomeLivelloPolline
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.italiano

/**
 * Il polline, in fondo alla sala dell'aria: tre famiglie, tre giorni.
 *
 * Si mostra **solo se ci sono dati**. Il modello e' europeo: altrove l'API
 * lascia i campi nulli, e tre "assente" disegnati su un dato che non c'e'
 * sarebbero la stessa bugia per cui questa sezione non esisteva prima.
 *
 * La famiglia che si apre e' **la peggiore di oggi**: chi guarda il polline
 * vuole sapere prima di tutto quale lo riguarda, e con tutto a zero l'erba,
 * che e' la piu' comune.
 */
@Composable
internal fun SezionePolline(giorni: List<GiornoPolline>, palette: SalaPalette, modifier: Modifier = Modifier) {
    if (giorni.isEmpty()) return
    val peggiore = giorni.first().livelli.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: TipoPolline.ERBA
    var scelto by rememberSaveable(giorni.first().giorno) { mutableStateOf(peggiore) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "IL POLLINE",
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TipoPolline.entries.forEach { tipo ->
                val attivo = tipo == scelto
                val colore = if (attivo) palette.accentInk else palette.ink
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (attivo) palette.accent else palette.maniglia)
                        .clickable { scelto = tipo }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) { iconaPolline(tipo, colore) }
                    Text(
                        text = tipo.nome,
                        style = SalaType.rowNote,
                        color = colore,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            giorni.forEachIndexed { i, giorno ->
                ColonnaPolline(
                    etichetta = if (i == 0) "oggi" else giorno.giorno.dayOfWeek.italiano().take(3),
                    livello = giorno.livelli[scelto],
                    palette = palette,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Didascalia(scelto.cosa, palette, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Un giorno: il livello in cifre e in parole, e una barra che si riempie dal basso. */
@Composable
private fun ColonnaPolline(etichetta: String, livello: Int?, palette: SalaPalette, modifier: Modifier = Modifier) {
    val parola = livello?.let(::nomeLivelloPolline) ?: "Nessun dato"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(palette.chip)
            .padding(vertical = 12.dp)
            .semantics { contentDescription = "$etichetta: polline $parola".lowercase() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = livello?.let { "$it/4" } ?: "—", style = SalaType.value, color = palette.ink)
        Text(
            text = parola,
            style = SalaType.rowNote,
            color = palette.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        val tinta = coloreLivello(livello)
        val traccia = palette.maniglia
        Canvas(modifier = Modifier.padding(vertical = 10.dp).width(26.dp).height(58.dp)) {
            val raggio = CornerRadius(size.width / 2f)
            drawRoundRect(color = traccia, cornerRadius = raggio)
            val quota = ((livello ?: 0) / 4f).coerceIn(0f, 1f)
            if (quota > 0f) {
                // Mai piu' basso di un disco pieno: un "ridotto" deve vedersi.
                val alto = (size.height * quota).coerceAtLeast(size.width)
                drawRoundRect(
                    color = tinta,
                    topLeft = Offset(0f, size.height - alto),
                    size = Size(size.width, alto),
                    cornerRadius = raggio,
                )
            }
        }
        Text(text = etichetta, style = SalaType.rowNote, color = palette.ink)
    }
}

/** Dal verde al rosso bruciato: la stessa tavolozza dell'indice dell'aria. */
internal fun coloreLivello(livello: Int?): Color = when (livello) {
    null, 0 -> SalaTokens.neutral400
    1 -> SalaTokens.verde400
    2 -> SalaTokens.accent400
    3 -> SalaTokens.accent500
    else -> SalaTokens.accent700
}

// ─────────────────────────────────────────────────────────────────────────────
// Le icone
// ─────────────────────────────────────────────────────────────────────────────
//
// **Disegnate qui, non prese da Google.** Le icone dell'app Meteo di Google
// sono sue e senza una licenza che ne permetta il riuso; queste sono tratti
// nostri, arrotondati come il resto delle icone di Sala, su un quadrato di 24
// unita' riscalato alla tela.

/** L'icona della famiglia [tipo], a tratto, nel colore [colore]. */
internal fun DrawScope.iconaPolline(tipo: TipoPolline, colore: Color) {
    val u = size.minDimension / 24f
    val tratto = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    when (tipo) {
        // Tre fili d'erba che escono dallo stesso punto e si piegano.
        TipoPolline.ERBA -> {
            val fili = Path().apply {
                moveTo(12f * u, 21f * u); quadraticTo(12f * u, 11f * u, 9f * u, 4f * u)
                moveTo(12f * u, 21f * u); quadraticTo(15f * u, 13f * u, 20f * u, 8f * u)
                moveTo(12f * u, 21f * u); quadraticTo(8f * u, 15f * u, 3f * u, 12f * u)
            }
            drawPath(fili, colore, style = tratto)
            drawLine(colore, p(5f, 21f), p(19f, 21f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        // Una chioma tonda sopra un tronco, con un ramo.
        TipoPolline.ALBERI -> {
            drawCircle(colore, radius = 6.5f * u, center = p(12f, 9f), style = tratto)
            drawLine(colore, p(12f, 15.5f), p(12f, 21f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(colore, p(12f, 12f), p(14.5f, 9.5f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(colore, p(8f, 21f), p(16f, 21f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        // Un germoglio: il gambo e due foglie a mandorla.
        TipoPolline.ERBACCE -> {
            drawLine(colore, p(12f, 21f), p(12f, 10f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            val foglie = Path().apply {
                moveTo(12f * u, 12f * u)
                quadraticTo(4f * u, 12f * u, 4f * u, 5f * u)
                quadraticTo(11f * u, 5f * u, 12f * u, 12f * u)
                moveTo(12f * u, 10f * u)
                quadraticTo(13f * u, 3f * u, 20f * u, 3f * u)
                quadraticTo(20f * u, 10f * u, 12f * u, 10f * u)
            }
            drawPath(foglie, colore, style = tratto)
        }
    }
}
