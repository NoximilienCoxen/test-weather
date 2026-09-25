package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
 * Il polline, in fondo alla sala dell'aria: tre famiglie per tre giorni, in una
 * tabella.
 *
 * **Una riga per famiglia, non una scheda.** La prima versione copiava Google -
 * tre pastiglie per scegliere la famiglia e tre colonne alte coi giorni - e da
 * sola occupava mezzo schermo per dire tre numeri. Qui le tre famiglie si
 * leggono insieme, che e' anche la domanda vera: "c'e' qualcosa che mi fa
 * male?", non "com'e' l'erba?".
 *
 * Si mostra **solo se ci sono dati**. Il modello e' europeo: altrove l'API
 * lascia i campi nulli, e tre "assente" disegnati su un dato che non c'e'
 * sarebbero la stessa bugia per cui questa sezione non esisteva prima.
 *
 * Sotto, una riga sola che dice quali piante sono: di norma quelle della
 * famiglia peggiore di oggi, oppure quelle della riga toccata.
 */
@Composable
internal fun SezionePolline(giorni: List<GiornoPolline>, palette: SalaPalette, modifier: Modifier = Modifier) {
    if (giorni.isEmpty()) return
    val oggi = giorni.first().livelli
    val peggiore = oggi.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    var scelto by rememberSaveable(giorni.first().giorno) { mutableStateOf(peggiore) }

    Column(modifier = modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "IL POLLINE",
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.weight(1f),
            )
            giorni.forEachIndexed { i, giorno ->
                Text(
                    text = if (i == 0) "oggi" else giorno.giorno.dayOfWeek.italiano().take(3),
                    style = SalaType.microLabel,
                    color = palette.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = SPAZIO_PASTIGLIE).width(LARGA_PASTIGLIA),
                )
            }
        }
        TipoPolline.entries.forEach { tipo ->
            val attivo = tipo == scelto
            val colore = if (attivo) palette.accent else palette.ink
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .clickable { scelto = if (attivo) peggiore else tipo }
                    .semantics {
                        contentDescription = tipo.nome + ": " + giorni.joinToString(", ") { g ->
                            g.livelli[tipo]?.let(::nomeLivelloPolline) ?: "nessun dato"
                        }.lowercase()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(16.dp)) { iconaPolline(tipo, colore) }
                Text(
                    text = tipo.nome,
                    style = if (attivo) SalaType.rowTitle else SalaType.hourLabel,
                    color = colore,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                giorni.forEach { giorno -> PastigliaLivello(giorno.livelli[tipo], palette) }
            }
        }
        val spiegato = scelto
        Didascalia(
            when {
                spiegato != null && spiegato == peggiore ->
                    "Oggi il più alto: ${spiegato.nome.lowercase()}, ${nomeLivelloPolline(oggi[spiegato] ?: 0).lowercase()}. " +
                        spiegato.cosa
                spiegato != null -> spiegato.cosa
                else -> "Oggi nell'aria non c'è polline che dia fastidio."
            },
            palette,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Il livello di un giorno: il numero dentro una pastiglia del suo colore. */
@Composable
private fun PastigliaLivello(livello: Int?, palette: SalaPalette) {
    val pieno = livello != null && livello > 0
    Text(
        text = livello?.toString() ?: "–",
        style = SalaType.pill,
        color = when {
            !pieno -> palette.inkSoft
            livello!! >= 4 -> SalaTokens.neutral100
            else -> SalaTokens.neutral900
        },
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(start = SPAZIO_PASTIGLIE)
            .width(LARGA_PASTIGLIA)
            .clip(CircleShape)
            .background(if (pieno) coloreLivello(livello) else palette.maniglia)
            .padding(vertical = 4.dp),
    )
}

private val LARGA_PASTIGLIA = 38.dp
private val SPAZIO_PASTIGLIE = 6.dp

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
