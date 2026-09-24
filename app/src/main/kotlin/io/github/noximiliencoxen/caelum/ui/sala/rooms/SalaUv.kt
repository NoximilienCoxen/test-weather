package io.github.noximiliencoxen.caelum.ui.sala.rooms

import io.github.noximiliencoxen.caelum.lingua.Lingua
import io.github.noximiliencoxen.caelum.lingua.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.noximiliencoxen.caelum.ui.sala.rooms.uv.OmbraSole
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.CellaValore
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.RigaSenzaOre
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.oraDueCifre
import io.github.noximiliencoxen.caelum.ui.sala.oraPiena
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sala VII — I raggi UV: l'indice ora per ora, sulle ore in cui esiste.
 *
 * Legge le ventiquattro ore vere e **lo stesso asse di tutte le altre**: nel
 * prototipo questa schermata aveva un cursore suo, separato, sulle sole ore
 * 6-21, e due cursori che dicono l'ora sono due verita' da tenere in fase.
 */
@Composable
fun SalaUvScreen(
    state: UiState,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Falso quando chi usa l'app ha chiesto meno movimento: il riflesso non
     *  passa, e il numero resta a dire quello che ha sempre detto. */
    movimento: Boolean = true,
) {
    // Le ore del giorno mostrato: vedi la nota in Sala III.
    val ore = state.shownHours
    val scelta = state.selectedHour
    val corrente = ore.getOrNull(scelta)?.uvIndex ?: 0.0
    // Le ore in cui il sole c'e': fuori da quelle l'indice vale zero a ogni
    // latitudine, e quindici colonne a zero non sono un grafico.
    val finestra = ore.indices.filter { ore[it].time.hour in 5..20 }
    val picco = ore.indices.maxByOrNull { ore[it].uvIndex ?: 0.0 }
    val ozono = state.air?.ozone

    // **Il riflesso avvisa dove il numero non basta.** Cinque e' la soglia in
    // cui la scala mondiale dell'OMS passa da "moderato" ad "alto", cioe' dove
    // la protezione smette di essere consigliata e diventa necessaria. Sopra
    // quel valore la scheda luccica ogni tanto: non aggiunge un dato - il
    // numero e la parola ci sono gia' - aggiunge il fatto che uno sguardo di
    // passaggio se ne accorga.
    //
    // Si accende sul valore **mostrato**, non sul picco del giorno: scorrendo
    // la barra fino alle tre di notte il riflesso deve spegnersi, perche' li'
    // l'indice e' zero e un avviso di sole alle tre di notte e' un avviso che
    // insegna a ignorare gli avvisi.
    PannelloSala(palette = palette, modifier = modifier, bagliore = corrente >= 5.0 && movimento) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tr("I raggi UV", "UV rays"), style = SalaType.cardTitle, color = palette.ink)
                Text(
                    text = nomeUv(corrente),
                    style = SalaType.rowTitle,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = String.format(Lingua.locale, "%.1f", corrente),
                style = SalaType.giant(46),
                color = palette.ink,
            )
        }

        if (ore.isEmpty()) RigaSenzaOre(palette, Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            finestra.forEach { indice ->
                val valore = ore[indice].uvIndex ?: 0.0
                // La scala e' l'indice stesso, non il massimo di giornata: un
                // 2 che tocca il soffitto perche' oggi non si va oltre
                // racconterebbe un sole che non c'e'.
                val quota = (valore / 11.0).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelectHour(indice) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((3f + quota * 80f).dp)
                            .clip(CircleShape)
                            .background(if (indice == scelta) palette.accent else coloreUv(valore, palette)),
                    )
                    // **Una tacca ogni tre ore, e l'ora scelta sempre.**
                    //
                    // Prima erano tutte e sedici: sedici colonne in duecento
                    // punti fanno dieci punti a colonna, e "05" ne vuole
                    // dodici, quindi la scala si leggeva "0 0 0 0 0 10 11 12" -
                    // le prime cinque ore tagliate al primo carattere. Poi sono
                    // diventate le sole ore pari **piu' quella scelta**, ed e'
                    // li' che stava il difetto vero: con l'ora scelta dispari -
                    // le tredici, che e' l'ora in cui uno guarda i raggi UV - la
                    // scala diventava `... 12 13 14 ...`, tre numeri in trenta
                    // punti, uno addosso all'altro. Chi l'ha vista ha detto
                    // esattamente quello che si vedeva: che i numeri non si
                    // leggevano.
                    //
                    // Ogni tre ore le tacche sono cinque - 06, 09, 12, 15, 18 -
                    // e ognuna ha tre colonne per se'. L'ora scelta resta
                    // un'eccezione perche' non e' una tacca della scala: e' la
                    // risposta alla domanda "dove sono". Ma quando cade
                    // **accanto** a una tacca, a spostarsi e' la tacca: la scala
                    // sa contare da se' anche senza il 12, mentre la risposta a
                    // "dove sono" non ha nessun altro posto in cui stare.
                    //
                    // **La stringa vuota non e' pigrizia: e' la correzione.**
                    // Scritto con un `if` attorno al `Text`, il grafico si e'
                    // rotto sul telefono e non in nessuno scatto precedente:
                    // la riga qui sopra allinea le colonne **in basso**, e una
                    // colonna senza etichetta e' piu' corta di una con
                    // etichetta. Allineate in basso, le barre con l'etichetta
                    // salivano di tutta l'altezza dell'etichetta, e
                    // l'istogramma diventava una fila di barre a quote
                    // alternate - un grafico che mente sui propri valori.
                    //
                    // Un `Text` vuoto occupa comunque la propria interlinea,
                    // quindi tutte e sedici le colonne restano alte uguale.
                    // L'alternativa - una casella d'altezza fissa - sarebbe
                    // l'interlinea copiata a mano in un secondo posto, e le
                    // due copie divergerebbero al primo che tocca il corpo.
                    val ora = ore[indice].time.hour
                    // Due colonne di distanza e non una: a una colonna - dieci
                    // punti - due etichette da dodici si toccano, ed e' la
                    // sovrapposizione che si stava correggendo.
                    val libera = abs(indice - scelta) >= 2
                    val mostra = indice == scelta || (ora % 3 == 0 && libera)
                    // **L'etichetta esce dalla propria colonna, apposta.**
                    // Dimezzare le etichette non e' bastato: la colonna resta
                    // larga poco piu' di dieci punti, e li' dentro "12" ci sta
                    // mentre "06" no - la cifra uno e' piu' stretta delle
                    // altre, ed e' bastato quello perche' meta' scala si
                    // leggesse e meta' no.
                    //
                    // `unbounded` le lascia misurare la propria larghezza vera
                    // e sbordare, centrata. Puo' farlo **perche' le colonne
                    // dispari un'etichetta non ce l'hanno**: lo spazio in cui
                    // sborda e' vuoto per costruzione.
                    Text(
                        text = if (mostra) oraDueCifre(ora) else "",
                        style = SalaType.microLabel,
                        // **L'inchiostro tenue era la meta' del difetto.**
                        // `inkFaint` e' il grigio delle etichette dentro una
                        // cella, dove sopra c'e' sempre un valore nero a fare
                        // da appiglio; qui sotto le colonne non c'e' nient'altro
                        // da leggere, e dieci punti di corpo in grigio chiaro
                        // su carta chiara si guardano senza vederli. Una scala
                        // e' fatta per essere letta.
                        color = if (indice == scelta) palette.accent else palette.inkSoft,
                        maxLines = 1,
                        modifier = Modifier.wrapContentWidth(
                            align = Alignment.CenterHorizontally,
                            unbounded = true,
                        ),
                    )
                }
            }
        }

        ore.getOrNull(scelta)?.let { oraMostrata ->
            val altezzaSole = remember(oraMostrata.time, state.place, state.forecast?.utcOffsetSeconds) {
                OmbraSole.altezza(
                    oraMostrata.time,
                    state.place.latitude,
                    state.place.longitude,
                    state.forecast?.utcOffsetSeconds ?: 0,
                )
            }
            ScenaOmbra(
                altezzaSole = altezzaSole,
                mattina = oraMostrata.time.hour < 13,
                palette = palette,
                onTrascina = { frazione ->
                    // Il dito sulla scena scorre le stesse ore del grafico:
                    // la barra in fondo e questa scena restano allineate
                    // perche' scelgono la stessa cosa.
                    if (finestra.isNotEmpty()) {
                        val i = (frazione * finestra.size).toInt().coerceIn(0, finestra.lastIndex)
                        if (finestra[i] != scelta) onSelectHour(finestra[i])
                    }
                },
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = frase(oraMostrata.time.hour, altezzaSole),
                style = SalaType.rowTitle,
                color = palette.ink,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Didascalia(
            consiglio(corrente),
            palette,
            modifier = Modifier.padding(top = 15.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore(
                etichetta = tr("PICCO", "PEAK"),
                valore = picco?.let { oraPiena(ore[it].time.hour) } ?: "--",
                palette = palette,
            )
            CellaValore(etichetta = tr("AL SOLE", "IN THE SUN"), valore = esposizione(corrente), palette = palette)
            CellaValore(
                etichetta = tr("OZONO", "OZONE"),
                valore = ozono?.let { "${it.roundToInt()} µg/m³" } ?: "--",
                palette = palette,
            )
        }
    }
}

/**
 * Una persona e la sua ombra, con il sole all'altezza vera di quell'ora.
 *
 * L'indice UV e' un numero astratto; l'ombra no. **La regola dell'ombra** -
 * quando l'ombra e' piu' corta di chi la fa, il sole scotta - e' la stessa che
 * insegnano i dermatologi, e qui la si vede allungarsi e accorciarsi
 * trascinando il dito sulla scena da mattina a sera.
 *
 * Il sole sta a sinistra la mattina e a destra il pomeriggio - guardando a sud,
 * come si guarda il sole dall'Italia - e l'ombra va dalla parte opposta.
 */
@Composable
private fun ScenaOmbra(
    altezzaSole: Double,
    mattina: Boolean,
    palette: SalaPalette,
    onTrascina: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lunghezza = OmbraSole.lunghezzaRelativa(altezzaSole)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.chip)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onTrascina(it.x / size.width) },
                ) { cambio, _ ->
                    cambio.consume()
                    onTrascina(cambio.position.x / size.width)
                }
            }
            .semantics {
                contentDescription = if (lunghezza == null) {
                    tr("Il sole è sotto l'orizzonte.", "The sun is below the horizon.")
                } else {
                    tr("Sole a ${altezzaSole.roundToInt()} gradi, ombra lunga ${"%.1f".format(Lingua.locale, lunghezza)} volte la persona. Trascina per cambiare ora.", "Sun at ${altezzaSole.roundToInt()} degrees, shadow ${"%.1f".format(Lingua.locale, lunghezza)} times the person's height. Drag to change the hour.")
                }
            },
    ) {
        val terra = size.height * 0.80f
        val altezzaPersona = size.height * 0.52f
        val xPersona = size.width * 0.5f
        drawLine(
            color = palette.maniglia,
            start = Offset(16.dp.toPx(), terra),
            end = Offset(size.width - 16.dp.toPx(), terra),
            strokeWidth = 2.dp.toPx(),
        )

        // Il sole, su un quarto di cerchio: alto col sole alto.
        if (lunghezza != null) {
            val angolo = Math.toRadians(altezzaSole.coerceIn(0.0, 90.0))
            val raggio = size.width * 0.42f
            val lato = if (mattina) -1f else 1f
            val sole = Offset(
                xPersona + lato * (raggio * cos(angolo)).toFloat(),
                terra - (size.height * 0.72f * sin(angolo)).toFloat(),
            )
            drawCircle(SalaTokens.accent400, radius = 9.dp.toPx(), center = sole)

            // L'ombra, dalla parte opposta, lunga quanto dice il conto e
            // tagliata al bordo della scena.
            val fine = (xPersona - lato * (altezzaPersona * lunghezza).toFloat())
                .coerceIn(16.dp.toPx(), size.width - 16.dp.toPx())
            drawLine(
                color = palette.ink.copy(alpha = 0.28f),
                start = Offset(xPersona, terra),
                end = Offset(fine, terra),
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // La persona: corpo e testa, due tratti pieni.
        drawLine(
            color = palette.ink,
            start = Offset(xPersona, terra),
            end = Offset(xPersona, terra - altezzaPersona * 0.78f),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(palette.ink, radius = altezzaPersona * 0.11f, center = Offset(xPersona, terra - altezzaPersona * 0.92f))
    }
}

/** Quello che la scena dice, in una frase. */
private fun frase(ora: Int, altezzaSole: Double): String {
    val lunghezza = OmbraSole.lunghezzaRelativa(altezzaSole)
        ?: return tr("Alle ${oraDueCifre(ora)} il sole è sotto l'orizzonte.", "At ${oraDueCifre(ora)} the sun is below the horizon.")
    val quanto = when {
        lunghezza < 1.0 -> tr("più corta di te: il sole scotta", "shorter than you: the sun burns")
        lunghezza < 1.3 -> tr("lunga circa quanto te", "about as long as you")
        lunghezza >= OmbraSole.MASSIMA -> tr("lunghissima", "very long")
        else -> tr("${"%.1f".format(Lingua.locale, lunghezza)} volte te", "${"%.1f".format(Lingua.locale, lunghezza)} times you")
    }
    return tr("Alle ${oraDueCifre(ora)} sole a ${altezzaSole.roundToInt()}°, l'ombra è $quanto.", "At ${oraDueCifre(ora)} sun at ${altezzaSole.roundToInt()}°, the shadow is $quanto.")
}

/** I nomi della scala mondiale: gli stessi cinque gradini di ogni bollettino. */
private fun nomeUv(valore: Double): String = when {
    valore >= 11 -> tr("Estremo", "Extreme")
    valore >= 8 -> tr("Molto alto", "Very high")
    valore >= 6 -> tr("Alto", "High")
    valore >= 3 -> tr("Moderato", "Moderate")
    valore > 0.2 -> tr("Basso", "Low")
    else -> tr("Assente", "None")
}

private fun coloreUv(valore: Double, palette: SalaPalette): Color = when {
    valore >= 8 -> SalaTokens.accent700
    valore >= 6 -> SalaTokens.accent500
    valore >= 3 -> SalaTokens.accent400
    valore > 0.2 -> SalaTokens.verde400
    else -> palette.maniglia
}

/**
 * Quanto si sta al sole prima di scottarsi.
 *
 * E' la regola pratica del tempo di eritema per una pelle chiara non protetta:
 * circa duecento diviso l'indice, in minuti. Un ordine di grandezza, e va detto
 * come tale - non un timer.
 */
private fun esposizione(valore: Double): String = when {
    valore <= 0.2 -> tr("libera", "unlimited")
    else -> "~${(200.0 / valore).roundToInt().coerceAtMost(240)} min"
}

private fun consiglio(valore: Double): String = when {
    valore >= 8 -> tr("Indice molto alto: nelle ore centrali servono cappello, occhiali e crema ad alto fattore, e l'ombra quando c'è.", "Very high index: around midday you need a hat, sunglasses and high-factor sunscreen, and shade when there is any.")
    valore >= 6 -> tr("Serve protezione: crema ad alto fattore e pause all'ombra nelle ore centrali.", "Protection needed: high-factor sunscreen and breaks in the shade around midday.")
    valore >= 3 -> tr("Protezione consigliata se si resta fuori a lungo, soprattutto in quota o sull'acqua.", "Protection advised if you stay out long, especially at altitude or on water.")
    valore > 0.2 -> tr("L'esposizione è sicura per tempi lunghi: nessuna protezione necessaria.", "Exposure is safe for long periods: no protection needed.")
    else -> tr("Sole sotto l'orizzonte: nessuna radiazione ultravioletta.", "Sun below the horizon: no ultraviolet radiation.")
}
