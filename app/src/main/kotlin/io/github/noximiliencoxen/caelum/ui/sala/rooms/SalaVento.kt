package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.RigaSenzaOre
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.oraDueCifre
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.noximiliencoxen.caelum.ui.motion.bussolaDisponibile
import io.github.noximiliencoxen.caelum.ui.motion.rememberBussola
import io.github.noximiliencoxen.caelum.ui.motion.rememberVibrazioniMeteo
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Sala VI — Il vento: da dove viene, quanto forte, e cosa fa nelle prossime sei ore.
 *
 * La rosa non e' un grafico ma un **verso**: la lancetta punta da dove il vento
 * arriva, che e' il modo in cui lo si dice a voce ("viene da nord-est") e non
 * quello in cui lo scrive il dato (i gradi di provenienza).
 */
@Composable
fun SalaVentoScreen(
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
    /** Falso con le animazioni ridotte: niente colpetti ai punti cardinali. */
    movimento: Boolean = true,
) {
    // Le ore del giorno mostrato: vedi la nota in Sala III.
    val ore = state.shownHours
    val scelta = state.selectedHour
    val ora = state.detailHour
    val unita = state.windUnit
    val velocita = ora?.windSpeed
    val raffiche = ora?.windGusts
    val direzione = ora?.windDirection

    val context = LocalContext.current
    val conBussola = remember(context) { bussolaDisponibile(context) }
    var bussolaAccesa by rememberSaveable { mutableStateOf(false) }
    var sguardoManuale by rememberSaveable { mutableFloatStateOf(0f) }
    val bussola by rememberBussola(accesa = bussolaAccesa && conBussola)
    val sguardo = if (bussolaAccesa) bussola ?: 0f else sguardoManuale

    // Un colpetto a ogni punto cardinale attraversato: e' cosi' che la mano
    // sente il nord senza guardare.
    val vibrazioni = rememberVibrazioniMeteo()
    val quadrante = ((sguardo + 45f) / 90f).toInt() % 4
    var quadrantePrima by remember { mutableIntStateOf(quadrante) }
    LaunchedEffect(quadrante) {
        if (quadrante != quadrantePrima && movimento) vibrazioni.scatto()
        quadrantePrima = quadrante
    }

    PannelloSala(palette = palette, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(ROSA)
                    .pointerInput(bussolaAccesa) {
                        if (bussolaAccesa) return@pointerInput
                        detectDragGestures { cambio, _ ->
                            cambio.consume()
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val prima = cambio.previousPosition - c
                            val dopo = cambio.position - c
                            val delta = Math.toDegrees(
                                (atan2(dopo.y, dopo.x) - atan2(prima.y, prima.x)).toDouble(),
                            ).toFloat()
                            // Il gesto sta prima della rotazione, quindi i
                            // punti sono quelli dello schermo e non girano con
                            // la rosa. Il dito che gira in senso orario
                            // gira la rosa in senso orario, cioe' fa guardare
                            // di tanto in senso antiorario.
                            val scarto = ((delta + 540f) % 360f) - 180f
                            sguardoManuale = ((sguardoManuale - scarto) % 360f + 360f) % 360f
                        }
                    }
                    // **La rosa gira, e con lei le lettere e la lancetta.**
                    // Con la bussola accesa la gira il telefono: il nord della
                    // rosa resta sul nord vero, e la lancetta indica da dove
                    // il vento arriva rispetto a dove si sta guardando. Spenta,
                    // la si gira col dito, come una bussola di carta.
                    .rotate(-sguardo)
                    .clip(CircleShape)
                    .background(palette.chip)
                    .semantics {
                        contentDescription = "Rosa dei venti. " + (
                            if (bussolaAccesa) "Segue la bussola del telefono." else "Trascina per girarla."
                            )
                    },
                contentAlignment = Alignment.Center,
            ) {
                listOf("N" to Alignment.TopCenter, "S" to Alignment.BottomCenter,
                    "O" to Alignment.CenterStart, "E" to Alignment.CenterEnd).forEach { (lettera, dove) ->
                    Text(
                        text = lettera,
                        style = SalaType.microLabel,
                        color = if (lettera == "N") palette.accent else palette.inkFaint,
                        modifier = Modifier.align(dove).padding(8.dp),
                    )
                }
                Canvas(
                    modifier = Modifier
                        .size(ROSA)
                        .rotate((direzione?.toFloat() ?: 0f) + 180f),
                ) {
                    // La lancetta parte dal centro e va verso l'alto; la
                    // rotazione la porta dove serve. Mezzo giro in piu' perche'
                    // il dato dice **da dove viene**, non dove va.
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawRoundRect(
                        color = palette.accent,
                        topLeft = Offset(cx - 2.5.dp.toPx(), cy - size.height * 0.42f),
                        size = Size(5.dp.toPx(), size.height * 0.42f),
                        cornerRadius = CornerRadius(2.5.dp.toPx()),
                    )
                    drawCircle(color = palette.accent, radius = 7.dp.toPx())
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Il vento", style = SalaType.cardTitle, color = palette.ink)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = velocita?.let { unita.from(it).roundToInt().toString() } ?: "--",
                        style = SalaType.numeroSala,
                        color = palette.ink,
                    )
                    Text(
                        text = "${unita.label} ${Wmo.windDirection(direzione)}",
                        style = SalaType.hourLabel,
                        color = palette.accent,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                Didascalia(
                    // Le soglie vogliono i km/h; il display vuole l'unita'
                    // scelta. Passarli mescolati faceva dire "burrasca" a
                    // sessanta nodi e "brezza" a sessanta km/h.
                    nota(
                        kmh = velocita?.let { it * 3.6 },
                        raffiche = raffiche?.let { unita.from(it).roundToInt() },
                        unita = unita.label,
                    ),
                    palette,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        RigaBussola(
            sguardo = sguardo,
            direzione = direzione,
            conBussola = conBussola,
            bussolaAccesa = bussolaAccesa,
            onBussola = { bussolaAccesa = it },
            palette = palette,
            modifier = Modifier.padding(top = 14.dp),
        )

        Text(
            text = "LE PROSSIME SEI ORE",
            style = SalaType.sectionLabel,
            color = palette.inkFaint,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
        )
        val finestra = (scelta + 1..scelta + 6).filter { it in ore.indices }
        if (ore.isEmpty()) RigaSenzaOre(palette)
        val massimo = finestra.mapNotNull { ore[it].windSpeed }.maxOrNull()?.coerceAtLeast(0.5) ?: 1.0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            finestra.forEach { indice ->
                val ms = ore[indice].windSpeed ?: 0.0
                val quota = (ms / massimo).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.chip)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = oraDueCifre(ore[indice].time.hour),
                        style = SalaType.microLabel,
                        color = palette.inkSoft,
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((10f + quota * 36f).dp)
                            .clip(CircleShape)
                            .background(
                                if (unita.from(ms) > 24) SalaTokens.accent500 else SalaTokens.verde400,
                            ),
                    )
                    Text(
                        text = unita.from(ms).roundToInt().toString(),
                        style = SalaType.giornoMax,
                        color = palette.ink,
                    )
                }
            }
        }
    }
}

/**
 * Dove si sta guardando, da dove arriva il vento rispetto a quello, e
 * l'interruttore della bussola - che c'e' solo se il telefono ne ha una.
 */
@Composable
private fun RigaBussola(
    sguardo: Float,
    direzione: Double?,
    conBussola: Boolean,
    bussolaAccesa: Boolean,
    onBussola: (Boolean) -> Unit,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (bussolaAccesa) "GUARDI VERSO ${Wmo.windDirection(sguardo.toDouble())}" else "GIRA LA ROSA COL DITO",
                style = SalaType.sectionLabel,
                color = palette.inkFaint,
            )
            if (direzione != null) {
                val lato = latoDelVento(ventoRispettoASguardo(direzione.toFloat(), sguardo))
                Text(
                    text = if (bussolaAccesa) {
                        lato.replaceFirstChar { it.uppercase() }
                    } else {
                        "Guardando verso ${Wmo.windDirection(sguardo.toDouble())}, $lato"
                    },
                    style = SalaType.rowTitle,
                    color = palette.ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (conBussola) {
            Text(
                text = if (bussolaAccesa) "BUSSOLA ACCESA" else "USA LA BUSSOLA",
                style = SalaType.sectionLabel,
                color = if (bussolaAccesa) palette.accentInk else palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (bussolaAccesa) palette.accent else palette.chip)
                    .clickable { onBussola(!bussolaAccesa) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private val ROSA = 120.dp

/**
 * Cosa vuol dire questa velocita', in cose che si vedono fuori.
 *
 * Le soglie sono quelle della scala Beaufort ridotte a tre gradini, e sono in
 * chilometri orari **qualunque** unita' si stia mostrando: il fenomeno non
 * cambia col modo di misurarlo.
 */
private fun nota(kmh: Double?, raffiche: Int?, unita: String): String {
    val coda = raffiche?.let { " Raffiche fino a $it $unita." } ?: ""
    val corpo = when {
        kmh == null -> "Vento non disponibile per quest'ora."
        kmh < 6 -> "Aria quasi ferma: il fumo sale dritto."
        kmh < 20 -> "Brezza leggera: si muovono le foglie, non i rami."
        kmh < 39 -> "Vento teso: i rami si piegano e l'ombrello diventa scomodo."
        kmh < 62 -> "Vento forte: attenzione agli oggetti esposti sui balconi."
        else -> "Burrasca: meglio non stare sotto gli alberi."
    }
    return corpo + coda
}
