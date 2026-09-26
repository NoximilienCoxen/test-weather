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
import io.github.noximiliencoxen.caelum.data.HourForecast
import io.github.noximiliencoxen.caelum.data.MoonPhase
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
 * si chiedeva cosa fosse cambiato. In cima la settimana intera in una frase;
 * sotto, sei riquadri **del giorno scelto** - pioggia, minima e massima, vento,
 * UV, luna, aria - e ognuno porta alla schermata che ne parla per esteso; in
 * fondo la stessa striscia di Sala I, coi millimetri in piu'.
 *
 * **I riquadri dicono il giorno scelto, non la settimana**, perche' la sala in
 * cui portano mostra il giorno scelto. Riassumevano i sette giorni: "Pioggia
 * 0,9 mm" portava a una sala che per oggi diceva zero, e i due numeri non
 * coincidevano. La settimana resta nella frase sotto il titolo.
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

        if (scelto != null) {
            // Di quale giorno parlano i riquadri, detto sopra di loro.
            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = scelto.esteso, style = SalaType.rowTitle, color = palette.ink)
                Text(
                    text = "${scelto.data} · ${scelto.tipo.lowercase()}",
                    style = SalaType.rowNote,
                    color = palette.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        RiepilogoGiorno(
            giorno = scelto,
            state = state,
            faseLunare = faseLunare,
            palette = palette,
            onVai = onVai,
            onOra = viewModel::selectHour,
            modifier = Modifier.padding(top = 9.dp),
        )

        if (scelto != null) {
            // Alba e tramonto non sono riquadri: sono due istanti, non quantita'
            // da confrontare fra giorni, e non portano a nessuna sala.
            Text(
                text = "alba ${scelto.alba?.format(OraMinuto) ?: "--:--"} · " +
                    "tramonto ${scelto.tramonto?.format(OraMinuto) ?: "--:--"}",
                style = SalaType.microLabel,
                color = palette.inkSoft,
                modifier = Modifier.padding(top = 10.dp),
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
 * I sei riquadri del giorno scelto, e dove portano.
 *
 * **Ognuno e' una porta.** Un numero riassuntivo e' utile finche' non si vuole
 * sapere di piu', e a quel punto la domanda successiva e' sempre la stessa:
 * "dove lo vedo per esteso?". Toccando il riquadro ci si arriva, invece di
 * tornare indietro a cercare la schermata giusta nella colonna - e ci si trova
 * lo stesso giorno, quindi lo stesso numero.
 *
 * **Pioggia, vento e UV portano anche all'ora giusta.** Le loro sale dicono il
 * valore di un'ora, e i riquadri quello di un giorno: aprendo la pioggia di
 * venerdi' alle tre del pomeriggio, "0,9 mm" diventava "0,0". Si arriva
 * all'ora che il numero del riquadro racconta: quella in cui tira piu' vento o
 * il sole e' piu' forte, e per la pioggia quella in cui **comincia**, perche'
 * la sala somma le dodici ore successive e da li' il conto copre la giornata.
 * Se il giorno non ha ore, o non piove affatto, l'ora resta quella che era.
 */
@Composable
private fun RiepilogoGiorno(
    giorno: GiornoSettimana?,
    state: UiState,
    faseLunare: Float,
    palette: SalaPalette,
    onVai: (SalaRoom) -> Unit,
    onOra: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ore = state.shownHours
    fun oraDelMassimo(valore: (HourForecast) -> Double?): Int? =
        ore.indices
            .filter { (valore(ore[it]) ?: 0.0) > 0.0 }
            .maxByOrNull { valore(ore[it]) ?: 0.0 }
    // La fase arriva gia' del giorno mostrato: vedi la Shell.
    val luna = MoonPhase.illumination(faseLunare)
    val oggi = state.selectedDay == 0
    // **L'aria di oggi e' quella di adesso**, lo stesso numero dell'anello in
    // Sala V; per i due giorni dopo, che l'API copre ora per ora, il picco del
    // giorno. Oltre, il modello non arriva e il riquadro lo dice.
    val aria = if (oggi) {
        state.air?.index
    } else {
        state.detailDay?.date?.let { data ->
            state.air?.oreOggi.orEmpty().filter { it.ora.toLocalDate() == data }.maxOfOrNull { it.indice }
        }
    }

    val voci = listOf(
        Riquadro(
            "PIOGGIA",
            giorno?.let { "${(it.mm ?: 0.0).virgola()} mm" } ?: "--",
            SalaTokens.acquaChiara,
            SalaRoom.PIOGGIA,
            ora = ore.indexOfFirst { (it.precipitation ?: 0.0) > 0.0 }.takeIf { it >= 0 },
        ),
        Riquadro(
            "MIN-MAX",
            if (giorno?.min != null && giorno.max != null) {
                "${giorno.min.gradi(state)} – ${giorno.max.gradi(state)}"
            } else {
                "--"
            },
            SalaTokens.accent400,
            SalaRoom.OGGI,
        ),
        Riquadro(
            "VENTO",
            giorno?.vento?.let { "${state.windUnit.from(it).roundToInt()} ${state.windUnit.label}" } ?: "--",
            SalaTokens.verde400,
            SalaRoom.VENTO,
            ora = oraDelMassimo { it.windSpeed },
        ),
        Riquadro(
            "PICCO UV",
            giorno?.uv?.virgola() ?: "--",
            SalaTokens.accent500,
            SalaRoom.UV,
            ora = oraDelMassimo { it.uvIndex },
        ),
        Riquadro(
            "LUNA",
            "${(luna * 100f).roundToInt()} %",
            SalaTokens.neutral300,
            SalaRoom.LUNA,
        ),
        Riquadro(
            if (oggi) "ARIA" else "ARIA MAX",
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
                            .clickable {
                                voce.ora?.let(onOra)
                                onVai(voce.sala)
                            }
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
    /** L'ora del giorno mostrato che il valore racconta, se ce n'e' una. */
    val ora: Int? = null,
)

private fun nomeUv(valore: Double): String = when {
    valore >= 8 -> "molto alto"
    valore >= 6 -> "alto"
    valore >= 3 -> "moderato"
    valore > 0 -> "basso"
    else -> "assente"
}

private fun Double?.gradi(state: UiState): String =
    this?.let { "${state.unit.from(it).roundToInt()}°" } ?: "--"

