package com.caelumweather.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.caelumweather.data.OraMare
import com.caelumweather.data.StatoDelMare
import com.caelumweather.data.Wmo
import com.caelumweather.lingua.Lingue
import com.caelumweather.lingua.tr
import com.caelumweather.prefs.TempUnit
import com.caelumweather.ui.sala.SalaPalette
import com.caelumweather.ui.sala.SalaTokens
import com.caelumweather.ui.sala.SalaType
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Il mare in Sala VI, sotto la pressione: solo per le localita' di costa.
 *
 * Sta col vento perche' l'onda e' vento che ha avuto tempo: la stessa aria che
 * la rosa mostra qui sopra, vista dopo qualche ora sull'acqua.
 *
 * **Il riquadro con l'onda si muove, ed e' un'eccezione dichiarata come il
 * cielo**: l'altezza del disegno segue i metri, il passo segue il periodo, e
 * chi ha chiesto meno movimento ([movimento] falso) la vede ferma.
 */
@Composable
internal fun SezioneMare(
    ora: OraMare,
    unita: TempUnit,
    palette: SalaPalette,
    movimento: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = tr("IL MARE", "THE SEA", "DAS MEER", "LA MER", "EL MAR"),
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OndaDisegnata(
                metri = ora.onda,
                periodo = ora.periodo,
                palette = palette,
                movimento = movimento,
                modifier = Modifier
                    .width(96.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.chip)
                    // E' un disegno dei numeri accanto: letto ad alta voce
                    // sarebbe una ripetizione.
                    .clearAndSetSemantics { },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = rigaOnde(ora, unita), style = SalaType.rowTitle, color = palette.ink)
                Text(
                    text = rigaAcqua(ora, unita),
                    style = SalaType.rowNote,
                    color = palette.inkSoft,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        // **Non e' una `Didascalia`, apposta**: quelle spariscono con i testi
        // brevi, e questa non e' un commento ai numeri - e' cio' che dice a
        // cosa non servono. Resta in tutti e due i modi.
        Text(
            text = tr(
                "Stima di un modello al largo: sotto costa onde e acqua possono essere diverse. " +
                    "Non usarla per navigare né per decidere un bagno: valgono i bollettini e le bandiere.",
                "A model estimate for open water: near the shore waves and water can differ. " +
                    "Do not use it for navigation or to decide on a swim: bulletins and beach flags come first.",
                "Schätzung eines Modells für die offene See: In Küstennähe können Wellen und Wasser anders sein. Nicht zum Navigieren und nicht für die Entscheidung über ein Bad verwenden: Es gelten die Seewetterberichte und die Flaggen am Strand.",
                "Estimation d'un modèle au large : près du rivage, les vagues et l'eau peuvent être différentes. Ne l'utilisez pas pour naviguer ni pour décider d'une baignade : les bulletins et les drapeaux de plage font foi.",
                "Estimación de un modelo mar adentro: cerca de la orilla las olas y el agua pueden ser distintas. No la uses para navegar ni para decidir un baño: mandan los boletines y las banderas de la playa.",
            ),
            style = SalaType.rowNote,
            color = palette.inkSoft,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "Poco mosso · onde di 0,3 m da ESE": puro, per poterlo provare. */
internal fun rigaOnde(ora: OraMare, unita: TempUnit): String {
    val metri = ora.onda ?: return tr("Onde non disponibili per quest'ora", "Waves unavailable for this hour", "Wellen für diese Stunde nicht verfügbar", "Vagues indisponibles pour cette heure", "Olas no disponibles para esta hora")
    val stato = StatoDelMare.di(metri)?.label?.replaceFirstChar { it.uppercase() }
    // Chi legge i gradi Fahrenheit legge anche i piedi: e' la stessa scelta.
    val altezza = if (unita == TempUnit.FAHRENHEIT) {
        String.format(Lingue.locale, "%.1f ft", metri * 3.28084)
    } else {
        String.format(Lingue.locale, "%.1f m", metri)
    }
    val da = ora.direzione?.let { tr(" da ${Wmo.windDirection(it)}", " from ${Wmo.windDirection(it)}", " aus ${Wmo.windDirection(it)}", " de ${Wmo.windDirection(it)}", " del ${Wmo.windDirection(it)}") } ?: ""
    return listOfNotNull(stato, tr("onde di $altezza$da", "$altezza waves$da", "Wellen von $altezza$da", "vagues de $altezza$da", "olas de $altezza$da")).joinToString(" · ")
}

/** "Acqua 16° · un'onda ogni 5 s". */
internal fun rigaAcqua(ora: OraMare, unita: TempUnit): String {
    val acqua = ora.acqua?.let { tr("acqua ${unita.from(it).roundToInt()}°", "water ${unita.from(it).roundToInt()}°", "Wasser ${unita.from(it).roundToInt()}°", "eau ${unita.from(it).roundToInt()}°", "agua ${unita.from(it).roundToInt()}°") }
    val periodo = ora.periodo?.takeIf { it >= 1.0 }?.let {
        tr("un'onda ogni ${it.roundToInt()} s", "a wave every ${it.roundToInt()} s", "eine Welle alle ${it.roundToInt()} s", "une vague toutes les ${it.roundToInt()} s", "una ola cada ${it.roundToInt()} s")
    }
    return listOfNotNull(acqua, periodo).joinToString(" · ")
        .ifEmpty { tr("temperatura dell'acqua non disponibile", "water temperature unavailable", "Wassertemperatur nicht verfügbar", "température de l'eau indisponible", "temperatura del agua no disponible") }
}

/**
 * Due profili d'onda che scorrono, uno davanti all'altro.
 *
 * L'ampiezza cresce coi metri fino a tre (oltre, il riquadro e' pieno e basta);
 * il passo si allunga col periodo, come fa il mare lungo. La fase si legge
 * **dentro il disegno**: scorrere ridipinge, non ricompone.
 */
@Composable
private fun OndaDisegnata(
    metri: Double?,
    periodo: Double?,
    palette: SalaPalette,
    movimento: Boolean,
    modifier: Modifier = Modifier,
) {
    val secondi = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(movimento) {
        if (!movimento) return@LaunchedEffect
        val inizio = withFrameNanos { it }
        while (true) withFrameNanos { secondi.floatValue = (it - inizio) / 1_000_000_000f }
    }
    val forza = ((metri ?: 0.0) / 3.0).coerceIn(0.0, 1.0).toFloat()
    val lungo = (((periodo ?: 4.0) - 2.0) / 10.0).coerceIn(0.0, 1.0).toFloat()
    val davanti = SalaTokens.acqua
    val dietro = davanti.copy(alpha = 0.38f)
    val profilo = remember { Path() }
    Canvas(modifier = modifier) {
        val t = secondi.floatValue
        val ampiezza = (2f + forza * 11f).dp.toPx()
        val passo = size.width * (0.55f + lungo * 0.75f)
        // Il mare lungo passa piu' piano: un'onda intera ogni periodo, ma
        // rallentata, perche' a grandezza vera attraverserebbe il riquadro in
        // un lampo.
        val velocita = passo / (2.2f + lungo * 3.2f)
        fun strato(colore: Color, livello: Float, sfasamento: Float, scala: Float) {
            profilo.rewind()
            profilo.moveTo(0f, size.height)
            var x = 0f
            val passoX = 3.dp.toPx()
            while (x <= size.width + passoX) {
                val angolo = ((x + t * velocita * scala) / passo) * 2f * PI.toFloat() + sfasamento
                // Un po' di seconda armonica: la cresta si stringe e il cavo
                // si allarga, come un'onda e non come una sinusoide da libro.
                val y = sin(angolo) + 0.22f * sin(2f * angolo + 0.6f)
                profilo.lineTo(x, size.height * livello - y * ampiezza * scala)
                x += passoX
            }
            profilo.lineTo(size.width, size.height)
            profilo.close()
            drawPath(profilo, colore)
        }
        strato(dietro, livello = 0.52f, sfasamento = 1.9f, scala = 0.8f)
        strato(davanti.copy(alpha = 0.85f), livello = 0.64f, sfasamento = 0f, scala = 1f)
    }
}
