package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.prefs.AlertToggleKind
import io.github.noximiliencoxen.caelum.prefs.CaptionStyle
import io.github.noximiliencoxen.caelum.prefs.CardTheme
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState

/**
 * Le impostazioni.
 *
 * **Le localita' sono entrate qui**, e sono uscite dalla colonna sul fianco:
 * cambiare citta' non e' spostarsi fra le schermate del tempo, e una fila di
 * sette icone piu' due intruse non e' piu' una fila. La riga dice anche quale
 * citta' e' attiva, cosi' si sa cosa si sta per cambiare prima di entrare.
 *
 * **Ogni interruttore qui dentro comanda qualcosa.** E' una regola e non una
 * constatazione: un comando che si accende, si spegne, si ricorda fra un avvio
 * e l'altro e non viene letto da nessuno e' peggio di un comando assente - chi
 * lo prova e non vede cambiare niente impara che i comandi di questa schermata
 * non contano, e da li' in poi non si fida nemmeno di quelli veri.
 */
@Composable
fun SalaImpostazioniScreen(
    state: UiState,
    palette: SalaPalette,
    onToggleAnimazioni: (Boolean) -> Unit,
    onChooseTheme: (CardTheme) -> Unit,
    onChooseUnit: (TempUnit) -> Unit,
    onChooseWindUnit: (SalaWindUnit) -> Unit,
    onChooseCaptionStyle: (CaptionStyle) -> Unit,
    onToggleAlert: (AlertToggleKind, Boolean) -> Unit,
    onApriLocalita: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 30.dp),
    ) {
        IntestazioneServizio(titolo = "Impostazioni", palette = palette, onIndietro = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BloccoImpostazioni(etichetta = "TEMA", palette = palette) {
                SceltaPastiglie(
                    voci = listOf(
                        CardTheme.AUTO to "Segui il cielo",
                        CardTheme.CHIARO to "Chiaro",
                        CardTheme.SCURO to "Scuro",
                    ),
                    scelta = state.cardTheme,
                    palette = palette,
                    onScegli = onChooseTheme,
                )
            }

            BloccoImpostazioni(etichetta = "TEMPERATURA", palette = palette) {
                SceltaPastiglie(
                    voci = listOf(TempUnit.CELSIUS to "°C", TempUnit.FAHRENHEIT to "°F"),
                    scelta = state.unit,
                    palette = palette,
                    onScegli = onChooseUnit,
                )
            }

            BloccoImpostazioni(etichetta = "VENTO", palette = palette) {
                SceltaPastiglie(
                    voci = SalaWindUnit.entries.map { it to it.label },
                    scelta = state.windUnit,
                    palette = palette,
                    onScegli = onChooseWindUnit,
                )
            }

            BloccoImpostazioni(etichetta = "DIDASCALIE", palette = palette) {
                SceltaPastiglie(
                    voci = listOf(CaptionStyle.COMPLETE to "Complete", CaptionStyle.BREVI to "Brevi"),
                    scelta = state.captionStyle,
                    palette = palette,
                    onScegli = onChooseCaptionStyle,
                )
            }

            // **Gli avvisi filtrano solo quelli calcolati.** Un bollettino della
            // Protezione Civile non lo si nasconde perche' un interruttore e'
            // giu': la scelta e' su cio' che questa applicazione deduce dalle
            // soglie, non su cio' che un ente dichiara.
            RigaServizio(
                titolo = "Pioggia intensa",
                nota = "avvisi calcolati sui millimetri attesi",
                palette = palette,
                coda = {
                    InterruttoreSala(state.alertToggles.pioggiaIntensa, palette) {
                        onToggleAlert(AlertToggleKind.PIOGGIA, !state.alertToggles.pioggiaIntensa)
                    }
                },
            )
            RigaServizio(
                titolo = "Temporali",
                nota = "avvisi calcolati sul codice del tempo",
                palette = palette,
                coda = {
                    InterruttoreSala(state.alertToggles.temporali, palette) {
                        onToggleAlert(AlertToggleKind.TEMPORALE, !state.alertToggles.temporali)
                    }
                },
            )
            RigaServizio(
                titolo = "Raggi UV sopra 6",
                nota = "il punto in cui la scala mondiale passa ad alto",
                palette = palette,
                coda = {
                    InterruttoreSala(state.alertToggles.uv, palette) {
                        onToggleAlert(AlertToggleKind.UV, !state.alertToggles.uv)
                    }
                },
            )
            RigaServizio(
                titolo = "Vento forte",
                nota = "avvisi calcolati sulle raffiche attese",
                palette = palette,
                coda = {
                    InterruttoreSala(state.alertToggles.vento, palette) {
                        onToggleAlert(AlertToggleKind.VENTO, !state.alertToggles.vento)
                    }
                },
            )
            RigaServizio(
                titolo = "Animazioni ridotte",
                nota = "ferma il cielo e le vibrazioni di cio' che cade",
                palette = palette,
                coda = {
                    InterruttoreSala(state.animazioniRidotte, palette) {
                        onToggleAnimazioni(!state.animazioniRidotte)
                    }
                },
            )
        }

        Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            RigaServizio(
                titolo = "Le località",
                nota = "${state.favorites.size} salvate · ${state.place.name} attiva",
                palette = palette,
                onClick = onApriLocalita,
                coda = {
                    Text(text = "›", style = SalaType.value, color = palette.accent)
                },
            )
        }
    }
}

