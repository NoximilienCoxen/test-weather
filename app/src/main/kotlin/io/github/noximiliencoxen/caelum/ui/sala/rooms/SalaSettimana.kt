package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.sala.GiornoSettimana
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaRoom
import io.github.noximiliencoxen.caelum.ui.sala.SalaTokens
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.settimanaDi
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val OraMinuto: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Sala II — La settimana.
 *
 * **Non ripete Sala I, la completa.** Le due si somigliavano troppo: entrambe
 * mostravano sette colonne con massima e minima, e chi scorreva da una all'altra
 * si chiedeva cosa fosse cambiato. Qui sopra ci sono sei riquadri che riassumono
 * la settimana **intera** - pioggia attesa, escursione, vento, UV, luna, aria -
 * e ognuno porta alla schermata che ne parla per esteso; in mezzo la scheda del
 * giorno scelto con tutto cio' che la striscia non ha spazio di dire; sotto la
 * stessa striscia di Sala I, coi millimetri in piu'.
 *
 * La selezione del giorno e' **condivisa**: si tocca qui e Sala I la segue, e
 * viceversa. E' lo stesso asse, non due schermate che si assomigliano.
 */
@Composable
fun SalaSettimanaScreen(
    state: UiState,
    palette: SalaPalette,
    viewModel: WeatherViewModel,
    /** La fase del giorno mostrato, calcolata una volta sola dalla Shell. */
    faseLunare: Float,
    onVai: (SalaRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settimana = remember(state.forecast) { settimanaDi(state.forecast) }
    val scelto = settimana.getOrNull(state.selectedDay)

    PannelloSala(palette = palette, modifier = modifier) {
        Text(text = "La settimana", style = SalaType.cardTitle, color = palette.ink)
        Text(
            text = remember(settimana, state.unit) { sommarioDella(settimana, state) },
            style = SalaType.footnote,
            color = palette.inkSoft,
            modifier = Modifier.padding(top = 6.dp),
        )

        RiepilogoSettimana(
            settimana = settimana,
            state = state,
            faseLunare = faseLunare,
            palette = palette,
            onVai = onVai,
            modifier = Modifier.padding(top = 13.dp),
        )

        if (scelto != null) {
            SchedaGiorno(
                giorno = scelto,
                state = state,
                palette = palette,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "TOCCA UN GIORNO", style = SalaType.sectionLabel, color = palette.inkFaint)
            Text(text = "MAX · MIN · MM", style = SalaType.sectionLabel, color = palette.inkFaint)
        }

        StrisciaGiorni(
            settimana = settimana,
            scelto = state.selectedDay,
            state = state,
            palette = palette,
            onScegli = viewModel::selectDay,
            conMillimetri = true,
        )
    }
}

/**
 * La settimana in una riga, **dai dati e non da una frase scritta a mano**.
 *
 * Il prototipo aveva una frase fissa ("un fronte da ovest da mercoledi'") che
 * descriveva una settimana inventata. Una frase del genere in un'app vera e'
 * una previsione senza fonte: qui si dice solo cio' che i numeri dicono.
 */
private fun sommarioDella(settimana: List<GiornoSettimana>, state: UiState): String {
    if (settimana.isEmpty()) return "Previsione non ancora disponibile."
    val bagnati = settimana.count { (it.mm ?: 0.0) > 0.05 }
    val mm = settimana.sumOf { it.mm ?: 0.0 }
    val minime = settimana.mapNotNull { it.min }
    val massime = settimana.mapNotNull { it.max }
    val escursione = if (minime.isNotEmpty() && massime.isNotEmpty()) {
        val lo = state.unit.from(minime.min()).roundToInt()
        val hi = state.unit.from(massime.max()).roundToInt()
        "escursione fra $lo° e $hi°"
    } else {
        null
    }
    val pioggia = when {
        bagnati == 0 -> "Sette giorni asciutti"
        bagnati == 1 -> "Un giorno con precipitazioni, ${mm.virgola()} mm attesi"
        else -> "$bagnati giorni con precipitazioni, ${mm.virgola()} mm attesi"
    }
    return listOfNotNull(pioggia, escursione).joinToString(", ") + "."
}

private fun Double.virgola(): String = String.format(Locale.ITALY, "%.1f", this)

/**
 * I sei riquadri di riepilogo, e dove portano.
 *
 * **Ognuno e' una porta.** Un numero che riassume la settimana e' utile finche'
 * non si vuole sapere di piu', e a quel punto la domanda successiva e' sempre la
 * stessa: "dove lo vedo per esteso?". Toccando il riquadro ci si arriva, invece
 * di tornare indietro a cercare la schermata giusta nella colonna.
 */
@Composable
private fun RiepilogoSettimana(
    settimana: List<GiornoSettimana>,
    state: UiState,
    faseLunare: Float,
    palette: SalaPalette,
    onVai: (SalaRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mm = settimana.sumOf { it.mm ?: 0.0 }
    val minime = settimana.mapNotNull { it.min }
    val massime = settimana.mapNotNull { it.max }
    val ventoMax = settimana.mapNotNull { it.vento }.maxOrNull()
    val uvMax = settimana.mapNotNull { it.uv }.maxOrNull()
    val luna = MoonPhase.illumination(faseLunare)
    val aria = state.air?.index

    val voci = listOf(
        Riquadro(
            "PIOGGIA",
            "${mm.virgola()} mm",
            SalaTokens.acquaChiara,
            SalaRoom.PIOGGIA,
        ),
        Riquadro(
            "MIN-MAX",
            if (minime.isNotEmpty() && massime.isNotEmpty()) {
                "${state.unit.from(minime.min()).roundToInt()}° – ${state.unit.from(massime.max()).roundToInt()}°"
            } else {
                "--"
            },
            SalaTokens.accent400,
            SalaRoom.OGGI,
        ),
        Riquadro(
            "VENTO",
            ventoMax?.let { "${state.windUnit.from(it).roundToInt()} ${state.windUnit.label}" } ?: "--",
            SalaTokens.verde400,
            SalaRoom.VENTO,
        ),
        Riquadro(
            "PICCO UV",
            uvMax?.virgola() ?: "--",
            SalaTokens.accent500,
            SalaRoom.UV,
        ),
        Riquadro(
            "LUNA",
            "${(luna * 100f).roundToInt()} %",
            SalaTokens.neutral300,
            SalaRoom.LUNA,
        ),
        Riquadro(
            "ARIA",
            aria?.let { "AQI $it" } ?: "--",
            SalaTokens.verde300,
            SalaRoom.ARIA,
        ),
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        voci.chunked(2).forEach { riga ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                riga.forEach { voce ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(palette.chip)
                            .clickable { onVai(voce.sala) }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(voce.tinta))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = voce.nome,
                                style = SalaType.microLabel,
                                color = palette.inkFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // **Una riga sola, e l'unita' nel valore.** Su due
                            // righe i sei riquadri uscivano di altezze diverse,
                            // e sei cose che dicono la stessa cosa devono avere
                            // la stessa forma.
                            Text(
                                text = voce.valore,
                                style = SalaType.rowTitle,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(text = "›", style = SalaType.rowTitle, color = palette.accent)
                    }
                }
            }
        }
    }
}

/**
 * Un riquadro del riepilogo.
 *
 * **I nomi sono corti per forza, non per gusto.** La cella e' meta' pannello
 * meno l'icona, la freccia e i margini: restano una sessantina di punti, cioe'
 * otto caratteri a questo corpo. "ESCURSIONE" e "VENTO MASSIMO" ci uscivano
 * troncati con i puntini, che e' il modo peggiore di dire una parola - si legge
 * come un guasto. Il valore sotto dice comunque di cosa si tratta.
 */
private data class Riquadro(
    val nome: String,
    val valore: String,
    val tinta: Color,
    val sala: SalaRoom,
)

private fun nomeUv(valore: Double): String = when {
    valore >= 8 -> "molto alto"
    valore >= 6 -> "alto"
    valore >= 3 -> "moderato"
    valore > 0 -> "basso"
    else -> "assente"
}

/**
 * La scheda del giorno scelto: quello che la striscia non ha spazio di dire.
 *
 * Alba e tramonto stanno a destra e non fra i valori perche' sono di un'altra
 * natura: gli altri quattro sono quantita' che si confrontano fra giorni, questi
 * due sono due istanti.
 */
@Composable
private fun SchedaGiorno(
    giorno: GiornoSettimana,
    state: UiState,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    // **Tutto in colonna, non in riga.** I quattro valori piu' alba e tramonto
    // affiancati non stavano nella larghezza del pannello: l'ultimo finiva
    // sotto il primo della colonna accanto, e un numero tagliato a meta' e'
    // peggio di un numero assente. Alba e tramonto scendono sotto, dove non
    // contendono spazio a niente - e sono di un'altra natura comunque: gli
    // altri quattro sono quantita' che si confrontano fra giorni, questi due
    // sono due istanti.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(palette.chip)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = giorno.esteso, style = SalaType.rowTitle, color = palette.ink)
            Text(
                text = "${giorno.data} · ${giorno.tipo.lowercase()}",
                style = SalaType.rowNote,
                color = palette.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValoreScheda(
                "MAX / MIN",
                "${giorno.max.gradi(state)} / ${giorno.min.gradi(state)}",
                palette,
                Modifier.weight(1.2f),
            )
            ValoreScheda("PIOGGIA", "${(giorno.mm ?: 0.0).virgola()} mm", palette, Modifier.weight(1f))
            ValoreScheda(
                "VENTO",
                giorno.vento?.let { state.windUnit.from(it).roundToInt().toString() } ?: "--",
                palette,
                Modifier.weight(0.7f),
            )
            ValoreScheda("UV", giorno.uv?.virgola() ?: "--", palette, Modifier.weight(0.6f))
        }
        Text(
            text = "alba ${giorno.alba?.format(OraMinuto) ?: "--:--"} · " +
                "tramonto ${giorno.tramonto?.format(OraMinuto) ?: "--:--"}",
            style = SalaType.microLabel,
            color = palette.inkSoft,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

private fun Double?.gradi(state: UiState): String =
    this?.let { "${state.unit.from(it).roundToInt()}°" } ?: "--"

@Composable
private fun ValoreScheda(
    etichetta: String,
    valore: String,
    palette: SalaPalette,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = etichetta, style = SalaType.microLabel, color = palette.inkFaint, maxLines = 1)
        Text(
            text = valore,
            style = SalaType.giornoMax,
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
