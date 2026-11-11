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
import com.caelumweather.data.EstremoMarea
import com.caelumweather.data.Mare
import com.caelumweather.data.OraMare
import com.caelumweather.data.TipoMarea
import com.caelumweather.data.prossimiEstremi
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
    /** Tutte le ore del mare, per le maree; nullo nelle catture che impongono l'onda. */
    mare: Mare? = null,
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
        mare?.let { SezioneMarea(it, ora.time, unita, palette) }
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

/**
 * Le maree: la curva del livello nelle ventiquattro ore dall'ora scelta, con
 * alte e basse segnate, e sotto le prossime in parole.
 *
 * Compare solo se la localita' ha una marea da dire ([prossimiEstremi] torna
 * vuota nei mari chiusi e sui laghi, dove il livello si muove di pochi
 * centimetri). Il punto sulla curva e' l'ora scelta, quindi trascinando la
 * barra delle ore si vede dove sta la marea in quel momento.
 */
@Composable
private fun SezioneMarea(
    mare: Mare,
    da: java.time.LocalDateTime,
    unita: TempUnit,
    palette: SalaPalette,
) {
    val estremi = remember(mare, da) { prossimiEstremi(mare, da, quanti = 4) }
    if (estremi.isEmpty()) return
    val finestra = remember(mare, da) {
        mare.ore.filter { !it.time.isBefore(da) && it.time.isBefore(da.plusHours(24)) && it.livello != null }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = tr("LE MAREE", "TIDES", "GEZEITEN", "LES MARÉES", "LAS MAREAS"),
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        if (finestra.size >= 3) {
            CurvaDellaMarea(
                livelli = finestra.map { it.livello!!.toFloat() },
                estremi = estremi.filter { it.quando.isBefore(da.plusHours(24)) },
                inizio = da,
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.chip)
                    // Disegno dei numeri qui sotto: letto ad alta voce sarebbe
                    // una ripetizione.
                    .clearAndSetSemantics { },
            )
        }
        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            estremi.forEach { e ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = rigaEstremo(e, da, unita),
                        style = SalaType.rowTitle,
                        color = palette.ink,
                    )
                }
            }
        }
        Text(
            text = tr(
                "Maree da un modello globale: nei porti e nelle lagune, e con vento o pressione insoliti, il livello vero può scostarsi di decine di centimetri.",
                "Tides from a global model: in harbours and lagoons, and with unusual wind or pressure, the real level can differ by tens of centimetres.",
                "Gezeiten aus einem globalen Modell: in Häfen und Lagunen sowie bei ungewöhnlichem Wind oder Luftdruck kann der echte Pegel um Dutzende Zentimeter abweichen.",
                "Marées issues d'un modèle global : dans les ports et les lagunes, et par vent ou pression inhabituels, le niveau réel peut différer de plusieurs dizaines de centimètres.",
                "Mareas de un modelo global: en puertos y lagunas, y con viento o presión inusuales, el nivel real puede diferir en decenas de centímetros.",
            ),
            style = SalaType.rowNote,
            color = palette.inkSoft,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "Alta alle 14:20 · +0,32 m" (o "domani alle 02:05"): puro, per poterlo provare. */
internal fun rigaEstremo(e: EstremoMarea, da: java.time.LocalDateTime, unita: TempUnit): String {
    val tipo = if (e.tipo == TipoMarea.ALTA) tr("Alta", "High", "Hochwasser", "Pleine mer", "Pleamar") else tr("Bassa", "Low", "Niedrigwasser", "Basse mer", "Bajamar")
    val ora = String.format(Lingue.locale, "%02d:%02d", e.quando.hour, e.quando.minute)
    val giorno = when (java.time.temporal.ChronoUnit.DAYS.between(da.toLocalDate(), e.quando.toLocalDate()).toInt()) {
        0 -> ""
        1 -> tr(" domani", " tomorrow", " morgen", " demain", " mañana")
        else -> " +${java.time.temporal.ChronoUnit.DAYS.between(da.toLocalDate(), e.quando.toLocalDate())}"
    }
    val livello = if (unita == TempUnit.FAHRENHEIT) {
        String.format(Lingue.locale, "%+.1f ft", e.metri * 3.28084)
    } else {
        String.format(Lingue.locale, "%+.2f m", e.metri)
    }.replace('-', '\u2212')
    return "$tipo $ora$giorno · $livello"
}

@Composable
private fun CurvaDellaMarea(
    livelli: List<Float>,
    estremi: List<EstremoMarea>,
    inizio: java.time.LocalDateTime,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    val colore = SalaTokens.acqua
    val minimo = livelli.min()
    val massimo = livelli.max().coerceAtLeast(minimo + 0.05f)
    val linea = remember { Path() }
    val riempi = remember { Path() }
    Canvas(modifier = modifier) {
        val margine = 10.dp.toPx()
        val alto = size.height - margine * 2
        fun x(i: Float) = margine + (size.width - margine * 2) * (i / (livelli.size - 1).toFloat())
        fun y(v: Float) = margine + alto * (1f - (v - minimo) / (massimo - minimo))
        linea.rewind(); riempi.rewind()
        riempi.moveTo(x(0f), size.height)
        livelli.forEachIndexed { i, v ->
            if (i == 0) linea.moveTo(x(0f), y(v)) else linea.lineTo(x(i.toFloat()), y(v))
            riempi.lineTo(x(i.toFloat()), y(v))
        }
        riempi.lineTo(x((livelli.size - 1).toFloat()), size.height)
        riempi.close()
        drawPath(riempi, colore.copy(alpha = 0.22f))
        drawPath(linea, colore, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        // I punti di alta e bassa.
        estremi.forEach { e ->
            val ore = java.time.Duration.between(inizio, e.quando).toMinutes() / 60f
            if (ore < 0f || ore > livelli.size - 1) return@forEach
            drawCircle(colore, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(ore), y(e.metri.toFloat())))
        }
        // L'ora scelta: il primo punto, dove la curva comincia.
        drawCircle(palette.ink, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(0f), y(livelli.first())))
    }
}
