package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.CellaValore
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.RigaSenzaOre
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.oraDueCifre
import java.util.Locale

/**
 * Sala III — La pioggia: le prossime dodici ore, una colonna per ora.
 *
 * Scrive sullo **stesso** `selectHour` della prima schermata: il giorno e' un
 * asse solo per tutta la galleria, e toccare una colonna qui sposta anche il
 * cielo e il numero dei gradi di Sala I.
 */
@Composable
fun SalaPioggiaScreen(
    state: UiState,
    palette: SalaPalette,
    onSelectHour: (Int) -> Unit,
    /** Toccare una colonna della settimana cambia il giorno di tutta la galleria. */
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Falso con le animazioni ridotte: niente vibrazioni sotto il dito. */
    movimento: Boolean = true,
) {
    // **Le ore del giorno mostrato, non quelle di oggi.** Toccando giovedi'
    // nella striscia, questa sala parlava ancora di oggi: il giorno e' un asse
    // solo per tutta la galleria, e `shownHours` esiste apposta.
    val ore = state.shownHours
    val scelta = state.selectedHour
    // Le dodici ore **da quella scelta in avanti**: una finestra che scorre con
    // la barra, non un pezzo fisso di giornata.
    //
    // **Sotto `remember`, e non perche' il conto sia caro.** Questa sala e'
    // dentro il carosello, quindi resta composta mentre il cielo si muove: a
    // ogni fotogramma rifaceva una lista di dodici indici, una di dodici
    // numeri, e ne rileggeva il massimo e la somma. Adesso la chiave e' stabile
    // per riferimento - `shownHours` e' sempre la stessa lista - e il conto si
    // rifa' solo cambiando ora o giorno.
    val finestra = remember(ore, scelta) {
        (scelta until minOf(scelta + 12, ore.size)).toList()
    }
    val pioggia = remember(ore, finestra) { finestra.map { ore[it].precipitation ?: 0.0 } }
    val massimo = remember(pioggia) { (pioggia.maxOrNull() ?: 0.0).coerceAtLeast(0.4) }
    val totale = remember(pioggia) { pioggia.sum() }
    // L'ora sotto il dito, dentro la finestra: trascinando sulle colonne si
    // legge ora per ora senza spostare la finestra - spostarla mentre il dito
    // ci passa sopra farebbe scappare la colonna da sotto il dito.
    var sottoIlDito by remember(ore, scelta) { mutableIntStateOf(-1) }
    val vibrazioni = rememberVibrazioniMeteo()
    val oraScelta = finestra.getOrNull(sottoIlDito)?.let { ore[it] } ?: state.detailHour
    val bagnato = totale > 0.05

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "La pioggia", style = SalaType.cardTitle, color = palette.ink)
                Didascalia(
                    if (bagnato) {
                        "Precipitazioni nelle prossime dodici ore, per un totale di ${totale.virgola()} millimetri."
                    } else {
                        "Nessuna precipitazione attesa nelle prossime dodici ore."
                    },
                    palette,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(text = totale.virgola(), style = SalaType.numeroSala, color = palette.ink)
                Text(
                    text = "mm",
                    style = SalaType.hourLabel,
                    color = palette.accent,
                    modifier = Modifier.padding(start = 3.dp, top = 8.dp),
                )
            }
        }

        if (ore.isEmpty()) RigaSenzaOre(palette, Modifier.padding(top = 18.dp))
        if (finestra.isNotEmpty()) {
            val fuoco = ore[finestra.getOrNull(sottoIlDito) ?: finestra.first()]
            Text(
                text = "${oraDueCifre(fuoco.time.hour)}:00 · " +
                    "${(fuoco.precipitation ?: 0.0).virgola()} mm · " +
                    "${fuoco.precipProbability ?: 0} %",
                style = SalaType.sectionLabel,
                color = if (sottoIlDito >= 0) palette.accent else palette.inkFaint,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // **Si trascina il dito sulle colonne** e si legge l'ora sotto:
                // millimetri e probabilita' qui sopra, e sotto le celle. Un
                // colpetto a ogni colonna nuova, perche' la mano senta il
                // passo delle ore anche senza guardare.
                .pointerInput(finestra) {
                    fun indiceIn(x: Float): Int =
                        (x / size.width * finestra.size).toInt().coerceIn(0, finestra.lastIndex)
                    detectHorizontalDragGestures(
                        onDragStart = { punto -> sottoIlDito = indiceIn(punto.x) },
                    ) { cambio, _ ->
                        cambio.consume()
                        val nuovo = indiceIn(cambio.position.x)
                        if (nuovo != sottoIlDito) {
                            sottoIlDito = nuovo
                            if (movimento) vibrazioni.scatto()
                        }
                    }
                },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            finestra.forEachIndexed { i, indice ->
                val mm = pioggia[i]
                val quota = (mm / massimo).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelectHour(indice) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Una colonna a zero resta visibile: una colonna
                            // assente si legge come un dato mancante, non come
                            // un'ora asciutta.
                            .height((3f + quota * 85f).dp)
                            .clip(CircleShape)
                            .background(
                                if (mm > 0.01) {
                                    Brush.verticalGradient(
                                        listOf(SalaTokens.acquaChiara, SalaTokens.acquaScura),
                                    )
                                } else {
                                    Brush.verticalGradient(listOf(palette.maniglia, palette.maniglia))
                                },
                            ),
                    )
                    Text(
                        text = oraDueCifre(ore[indice].time.hour),
                        style = SalaType.microLabel,
                        color = if (i == sottoIlDito || (sottoIlDito < 0 && indice == scelta)) palette.accent else palette.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CellaValore(
                etichetta = "PROBAB.",
                valore = oraScelta?.precipProbability?.let { "$it %" } ?: "--",
                palette = palette,
            )
            CellaValore(etichetta = "INTENSITÀ", valore = intensita(oraScelta?.precipitation), palette = palette)
            CellaValore(etichetta = "SUOLO", valore = if (totale > 4.0) "saturo" else "asciutto", palette = palette)
        }

        HorizontalDivider(
            color = palette.maniglia.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 18.dp, bottom = 16.dp),
        )

        // ── La domanda, e la risposta ────────────────────────────────────────
        //
        // **Qui c'era il radar, ed e' stato tolto.** Rispondeva a "dove piove"
        // per due ore su ventiquattro, solo dentro un rettangolo fra i cinque e
        // i venti gradi di longitudine - le coste disegnate a mano finivano li'
        // - e per ogni altro posto del mondo era un riquadro vuoto con la
        // pioggia che galleggiava sul niente. Le ragioni per esteso stanno in
        // CONTESTO 24.
        //
        // Al suo posto c'e' la domanda che uno si fa davvero guardando la
        // pioggia, e che i dati sanno **sempre** - per tutte le ore e per tutti
        // e sette i giorni: *quando comincia, quanto dura, quanta ne viene*.
        Text(
            text = "QUANDO",
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Tre o quattro filtri su tutte le ore della settimana e quattro
        // `String.format`: a ogni fotogramma, finche' questa sala e' composta.
        // La chiave e' la coppia da cui dipende davvero la frase.
        val quando = remember(state.forecast, state.detailHour?.time) { finestraPioggia(state) }
        Text(
            text = quando,
            style = SalaType.rowTitle,
            color = palette.ink,
        )

        // ── I sette giorni ───────────────────────────────────────────────────
        val giorni = state.forecast?.days.orEmpty()
        if (giorni.size >= 3) {
            Text(
                text = "LA SETTIMANA",
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
            )
            SettimanaDellaPioggia(
                giorni = giorni,
                scelto = state.selectedDay,
                palette = palette,
                onVai = onSelectDay,
            )
        }
    }
}

/**
 * Le colonne dei sette giorni, in millimetri.
 *
 * **Sono toccabili**, e non e' un di piu': la barra in fondo sceglie l'ora ma
 * il giorno si cambia solo da "La settimana", che sta due sale piu' in la'.
 * Vedere che giovedi' piove e non poterci andare da qui vorrebbe dire uscire,
 * scorrere, tornare.
 *
 * L'altezza e' in scala sul giorno piu' bagnato della settimana e non su una
 * soglia fissa: in una settimana da due millimetri l'uno una scala fissa
 * darebbe sette colonne tutte a zero, e una settimana piatta si legge lo stesso
 * dai numeri sotto.
 */
@Composable
private fun SettimanaDellaPioggia(
    giorni: List<DayForecast>,
    scelto: Int,
    palette: SalaPalette,
    onVai: (Int) -> Unit,
) {
    val massimo = giorni.mapNotNull { it.precipitationSum }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        giorni.forEachIndexed { indice, giorno ->
            val mm = giorno.precipitationSum ?: 0.0
            val mio = indice == scelto
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { onVai(indice) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.height(46.dp).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.52f)
                            // Un filo di colonna anche a zero: una colonna alta
                            // zero sparisce, e una colonna sparita si legge
                            // come "non lo so" invece che come "non piove".
                            .fillMaxHeight(((mm / massimo).coerceIn(0.06, 1.0)).toFloat())
                            .clip(CircleShape)
                            .background(
                                if (mm > 0.05) SalaTokens.acqua.copy(alpha = if (mio) 1f else 0.55f)
                                else palette.maniglia,
                            ),
                    )
                }
                Text(
                    text = giorno.label,
                    style = SalaType.microLabel,
                    color = if (mio) palette.accent else palette.inkFaint,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp).wrapContentWidth(unbounded = true),
                )
                Text(
                    text = if (mm > 0.05) mm.virgola() else "–",
                    style = SalaType.microLabel,
                    color = if (mio) palette.ink else palette.inkFaint,
                    maxLines = 1,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/**
 * La frase che dice quando piove, a partire dall'ora scelta.
 *
 * **E' la domanda vera.** Non "quanta acqua fa oggi in totale" - che e' un
 * numero da bollettino - ma *devo uscire adesso, mi bagno?*. Le colonne qui
 * sopra ce l'hanno dentro, ma vanno lette e interpretate; questa riga la
 * risponde.
 *
 * Guarda **avanti** dall'ora scelta, su tutte le ore che il modello ha - sette
 * giorni - e non sul solo giorno mostrato: una pioggia che comincia a
 * mezzanotte e mezza non e' "domani", e' fra novanta minuti.
 *
 * La soglia e' un decimo di millimetro l'ora. Sotto, il modello mette una
 * pioggia che nessuno sente cadere, e annunciarla renderebbe la frase inutile
 * per il novanta per cento delle giornate.
 */
private fun finestraPioggia(state: UiState): String {
    val ore = state.forecast?.allHours.orEmpty()
    val da = state.detailHour?.time ?: return "Non si sa: la previsione oraria non è arrivata."
    val avanti = ore.filter { !it.time.isBefore(da) }
    if (avanti.isEmpty()) return "Oltre questo momento la previsione oraria non arriva."

    val soglia = 0.1
    val bagnate = avanti.takeWhile { (it.precipitation ?: 0.0) >= soglia }
    if (bagnate.isNotEmpty()) {
        // Sta piovendo adesso: quello che serve sapere e' **quando smette**.
        val fine = bagnate.last().time.plusHours(1)
        val quanta = bagnate.sumOf { it.precipitation ?: 0.0 }
        return "Sta piovendo: smette verso le %02d:00, ancora %s mm."
            .format(fine.hour, quanta.virgola())
    }

    val inizio = avanti.firstOrNull { (it.precipitation ?: 0.0) >= soglia }
        ?: return "Nelle ore che il modello copre non è prevista pioggia."

    val finestra = avanti.dropWhile { it.time.isBefore(inizio.time) }
        .takeWhile { (it.precipitation ?: 0.0) >= soglia }
    val quanta = finestra.sumOf { it.precipitation ?: 0.0 }
    val quando = when (val giorni = java.time.Duration.between(da, inizio.time).toHours()) {
        in 0..1 -> "fra poco"
        in 2..11 -> "fra ${giorni} ore"
        else -> if (inizio.time.toLocalDate() == da.toLocalDate()) "oggi" else giornoDi(inizio.time, state)
    }
    val durata = if (finestra.size <= 1) "un'ora scarsa" else "circa ${finestra.size} ore"
    return "Comincia %s, verso le %02d:00: %s, %s mm in tutto."
        .format(quando, inizio.time.hour, durata, quanta.virgola())
}

/** L'etichetta del giorno di un istante, come la scrive la striscia in fondo. */
private fun giornoDi(quando: java.time.LocalDateTime, state: UiState): String =
    state.forecast?.days?.firstOrNull { it.date == quando.toLocalDate() }?.label?.lowercase()
        ?: "più avanti"

/**
 * Come si chiama questa quantita' d'acqua.
 *
 * Le soglie sono quelle di uso comune in millimetri l'ora: sotto mezzo
 * millimetro e' pioviggine, sopra i quattro e' un rovescio.
 */
private fun intensita(mm: Double?): String = when {
    mm == null || mm <= 0.01 -> "assente"
    mm < 0.5 -> "debole"
    mm < 2.0 -> "moderata"
    mm < 4.0 -> "forte"
    else -> "rovescio"
}

private fun Double.virgola(): String = String.format(Locale.ITALY, "%.1f", this)
