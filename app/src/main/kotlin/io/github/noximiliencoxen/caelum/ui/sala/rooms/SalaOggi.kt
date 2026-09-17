package io.github.noximiliencoxen.caelum.ui.sala.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import io.github.noximiliencoxen.caelum.data.SkyState
import io.github.noximiliencoxen.caelum.ui.UiState
import io.github.noximiliencoxen.caelum.ui.home.MoonPhase
import io.github.noximiliencoxen.caelum.ui.WeatherViewModel
import io.github.noximiliencoxen.caelum.ui.sala.CellaValore
import io.github.noximiliencoxen.caelum.ui.sala.Didascalia
import io.github.noximiliencoxen.caelum.ui.sala.GiornoSettimana
import io.github.noximiliencoxen.caelum.ui.sala.IconaMeteo
import io.github.noximiliencoxen.caelum.ui.sala.PannelloSala
import io.github.noximiliencoxen.caelum.ui.sala.PastigliaAccento
import io.github.noximiliencoxen.caelum.ui.sala.SalaPalette
import io.github.noximiliencoxen.caelum.ui.sala.SalaType
import io.github.noximiliencoxen.caelum.ui.sala.salaBody
import io.github.noximiliencoxen.caelum.ui.sala.salaConditionOf
import io.github.noximiliencoxen.caelum.ui.sala.salaPhaseOf
import io.github.noximiliencoxen.caelum.ui.sala.salaTitle
import io.github.noximiliencoxen.caelum.ui.sala.settimanaDi
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sala I — Oggi.
 *
 * Il numero dei gradi sta **fuori** dal pannello, sul cielo: e' la cosa che si
 * guarda, e metterla dentro un riquadro la farebbe diventare una delle tante.
 * Sotto, il pannello racconta l'ora con parole e tre valori, e in fondo la
 * striscia dei sette giorni.
 *
 * **Toccando un giorno tutto il resto lo segue.** Non e' una scorciatoia verso
 * Sala II: il giorno e' un asse che attraversa la galleria intera, quindi cielo,
 * temperatura, titolo, vento, umidita' e avvisi passano a quel giorno - e una
 * pastiglia dice quale si sta guardando, con la via del ritorno accanto.
 * La scena la ricalcola la Shell, che e' l'unico posto in cui il cielo vive.
 */
@Composable
fun SalaOggiScreen(
    state: UiState,
    /** Il cielo **gia' smorzato**, lo stesso che dipinge il fondo. */
    sky: SkyState,
    palette: SalaPalette,
    viewModel: WeatherViewModel,
    /** La fase del giorno mostrato, calcolata una volta sola dalla Shell. */
    faseLunare: Float,
    onApriSettimana: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fase = salaPhaseOf(sky)
    val ora = state.detailHour ?: state.hour
    val condizione = salaConditionOf(state.forcedWeatherCode ?: ora?.weatherCode)
    val settimana = remember(state.forecast) { settimanaDi(state.forecast) }
    val giornoScelto = settimana.getOrNull(state.selectedDay)
    val futuro = state.selectedDay > 0
    val giorno = state.detailDay

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Il numero, sul cielo ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = ora?.temperature?.let { state.unit.from(it).roundToInt().toString() } ?: "--",
                    style = SalaType.giant(104),
                    color = palette.ink,
                )
                Text(
                    text = "°",
                    style = SalaType.giant(38),
                    color = palette.accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val percepiti = ora?.apparent?.let { state.unit.from(it).roundToInt() }
                Text(
                    text = percepiti?.let { "percepiti $it°" } ?: "percepiti --",
                    style = SalaType.rowTitle,
                    color = palette.ink,
                )
                val min = giorno?.tempMin?.let { state.unit.from(it).roundToInt() }
                val max = giorno?.tempMax?.let { state.unit.from(it).roundToInt() }
                Text(
                    text = "min ${min ?: "--"}° · max ${max ?: "--"}°",
                    style = SalaType.rowNote,
                    color = palette.inkFaint,
                )
            }
        }

        PannelloSala(palette = palette) {
            if (futuro && giornoScelto != null) {
                Row(
                    modifier = Modifier.padding(bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    PastigliaAccento("${giornoScelto.esteso} ${giornoScelto.data}", palette)
                    // Il ritorno al presente compare **solo quando serve**: un
                    // comando che non fa niente insegna a non fidarsi degli altri.
                    Text(
                        text = "torna a oggi",
                        style = SalaType.pill,
                        color = palette.accent,
                        modifier = Modifier.clickable(onClick = viewModel::backToNow),
                    )
                }
            }

            Text(
                text = salaTitle(condizione, fase),
                style = SalaType.cardTitle,
                color = palette.ink,
            )
            Didascalia(
                salaBody(condizione, fase),
                palette,
                modifier = Modifier.padding(top = 7.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                CellaValore(
                    etichetta = "VENTO",
                    valore = ora?.windSpeed?.let { "${state.windUnit.from(it).roundToInt()} ${state.windUnit.label}" } ?: "--",
                    palette = palette,
                )
                CellaValore(
                    etichetta = "UMIDITÀ",
                    valore = ora?.humidity?.let { "${it.roundToInt()} %" } ?: "--",
                    palette = palette,
                )
                // Di notte l'indice UV vale zero a ogni latitudine, e una cella
                // che dice sempre la stessa cosa e' una cella sprecata: li' va
                // la luna, che di notte e' l'unica cosa che cambia.
                if (sky.moonPresence > 0.5f) {
                    CellaValore(
                        etichetta = "LUNA",
                        valore = "${(MoonPhase.illumination(faseLunare) * 100f).roundToInt()} %",
                        palette = palette,
                    )
                } else {
                    CellaValore(
                        etichetta = "UV",
                        valore = ora?.uvIndex?.let { String.format(Locale.ITALY, "%.1f", it) } ?: "--",
                        palette = palette,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 13.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.maniglia.copy(alpha = palette.maniglia.alpha * 0.5f)),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "TOCCA UN GIORNO", style = SalaType.sectionLabel, color = palette.inkFaint)
                // **Non dice "apri la sala II".** Chi guarda non chiama queste
                // schermate "sale" - e' un nome nostro, buono per il codice e
                // non per chi legge: qui si nomina la cosa, la settimana.
                Text(
                    text = "apri la settimana",
                    style = SalaType.pill,
                    color = palette.accent,
                    modifier = Modifier.clickable(onClick = onApriSettimana),
                )
            }

            StrisciaGiorni(
                settimana = settimana,
                scelto = state.selectedDay,
                state = state,
                palette = palette,
                onScegli = viewModel::selectDay,
            )
        }
    }
}

/**
 * La striscia dei sette giorni: sigla, figuretta, massima, minima.
 *
 * E' la **stessa** striscia che Sala II mette in fondo alla propria schermata,
 * con una riga in meno: li' c'e' spazio per i millimetri, qui no. Che sia la
 * stessa non e' una ripetizione: e' il modo in cui il giorno scelto resta
 * riconoscibile passando da una sala all'altra.
 */
@Composable
fun StrisciaGiorni(
    settimana: List<GiornoSettimana>,
    scelto: Int,
    state: UiState,
    palette: SalaPalette,
    onScegli: (Int) -> Unit,
    modifier: Modifier = Modifier,
    conMillimetri: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (conMillimetri) 5.dp else 6.dp),
    ) {
        settimana.forEach { giorno ->
            val attivo = giorno.indice == scelto
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(if (conMillimetri) 22.dp else 20.dp))
                    .background(if (attivo) palette.chip else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (attivo) palette.accent else Color.Transparent,
                        shape = RoundedCornerShape(if (conMillimetri) 22.dp else 20.dp),
                    )
                    .clickable { onScegli(giorno.indice) }
                    .padding(top = 11.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (conMillimetri) 6.dp else 7.dp),
            ) {
                Text(
                    text = giorno.breve,
                    style = SalaType.sectionLabel,
                    color = palette.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                IconaMeteo(giorno.glifo, palette)
                Text(
                    text = giorno.max?.let { "${state.unit.from(it).roundToInt()}°" } ?: "--",
                    style = SalaType.giornoMax,
                    color = palette.ink,
                    maxLines = 1,
                )
                Text(
                    text = giorno.min?.let { "${state.unit.from(it).roundToInt()}°" } ?: "--",
                    style = SalaType.giornoMin,
                    color = palette.inkFaint,
                    maxLines = 1,
                )
                if (conMillimetri) {
                    // **La gocciolina dice di che numero si tratta.** Un numero
                    // nudo sotto una temperatura si legge come un'altra
                    // temperatura; smorzata quando il giorno e' asciutto,
                    // perche' "0,0" con una goccia piena accanto e' una
                    // contraddizione.
                    val mm = giorno.mm ?: 0.0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(5.dp)
                                .width(5.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomEnd = 3.dp, bottomStart = 0.dp))
                                .background(palette.accent.copy(alpha = if (mm > 0.05) 1f else 0.3f)),
                        )
                        Text(
                            text = String.format(Locale.ITALY, "%.1f", mm),
                            style = SalaType.microLabel,
                            color = palette.accent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
