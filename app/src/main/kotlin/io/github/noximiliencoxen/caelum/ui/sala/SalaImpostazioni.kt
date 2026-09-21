package io.github.noximiliencoxen.caelum.ui.sala

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.BuildConfig
import io.github.noximiliencoxen.caelum.prefs.AlertToggleKind
import io.github.noximiliencoxen.caelum.prefs.CaptionStyle
import io.github.noximiliencoxen.caelum.prefs.CardTheme
import io.github.noximiliencoxen.caelum.prefs.SalaWindUnit
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.UiState
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

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
    onAggiorna: () -> Unit,
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
                    InterruttoreSala(state.alertToggles.uvAlto, palette) {
                        onToggleAlert(AlertToggleKind.UV, !state.alertToggles.uvAlto)
                    }
                },
            )
            RigaServizio(
                titolo = "Vento forte",
                nota = "avvisi calcolati sulle raffiche attese",
                palette = palette,
                coda = {
                    InterruttoreSala(state.alertToggles.ventoForte, palette) {
                        onToggleAlert(AlertToggleKind.VENTO, !state.alertToggles.ventoForte)
                    }
                },
            )
            RigaServizio(
                titolo = "Animazioni ridotte",
                nota = "ferma il cielo e le vibrazioni di ciò che cade",
                palette = palette,
                coda = {
                    InterruttoreSala(state.animazioniRidotte, palette) {
                        onToggleAnimazioni(!state.animazioniRidotte)
                    }
                },
            )

            // ── Le informazioni ──────────────────────────────────────────
            //
            // **La schermata aveva quattro selettori, cinque interruttori e
            // zero informazione.** La regola scritta qui sopra - *ogni
            // interruttore comanda qualcosa* - e' giusta e non c'entra: vale
            // per i comandi, e a furia di applicarla era rimasta una pagina che
            // sa solo ricevere ordini e non risponde a una domanda.
            //
            // Tutto quello che c'e' qui dentro **esisteva gia' nel codice** e
            // non lo leggeva nessuna schermata: la versione era motivata per
            // iscritto in `build.gradle.kts` e mai letta da una riga di Kotlin;
            // `fetchedAt` aveva accanto un commento che dichiarava *"la
            // schermata delle impostazioni lo dichiara"*, e non era vero; il
            // modello cambia i numeri della previsione e nessuno poteva sapere
            // quale fosse attivo.
            BloccoImpostazioni(etichetta = "DA DOVE VENGONO I NUMERI", palette = palette) {
                VoceInformativa("Ultimo aggiornamento", quandoScaricata(state.fetchedAt), palette)
                VoceInformativa("Previsione", "Open-Meteo · modello ${state.model.label}", palette)
                VoceInformativa("Qualità dell'aria", "Open-Meteo", palette)
                VoceInformativa("Allerte", "MeteoAlarm, più avvisi calcolati sui dati", palette)
                VoceInformativa("Località mostrata", dettaglioLocalita(state), palette)
                VoceInformativa("Versione", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", palette)
            }

            // **`refresh()` era pubblico e non lo chiamava nessuna schermata**,
            // e `state.error` - un messaggio gia' scritto per chi guarda - non
            // aveva un solo lettore in tutta l'app. Qui trovano tutti e due il
            // loro posto: si riprova a mano, e se va storto lo si legge.
            RigaServizio(
                titolo = "Aggiorna adesso",
                nota = state.error ?: "riprova a scaricare la previsione",
                palette = palette,
                onClick = onAggiorna,
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

/**
 * Una voce che **dice** invece di comandare: etichetta a sinistra, valore a
 * destra, sulla stessa riga.
 *
 * Non usa [RigaServizio] perche' quella si porta dietro la sua pastiglia, e
 * sei pastiglie dentro una pastiglia sono un riquadro che non si legge piu'.
 * Qui il riquadro e' uno solo, quello del blocco, e dentro ci sono righe.
 */
@Composable
private fun VoceInformativa(etichetta: String, valore: String, palette: SalaPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = etichetta, style = SalaType.rowNote, color = palette.inkFaint)
        Text(
            text = valore,
            style = SalaType.rowNote,
            color = palette.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Da quanto e' vecchio il dato che si ha in mano, detto come lo direbbe una
 * persona.
 *
 * L'ora e' quella del **telefono** e non della localita', ed e' voluto: la
 * domanda e' *quando l'ho scaricata io*, non che ore fossero a Bergen quando e'
 * partita la richiesta.
 */
private fun quandoScaricata(quando: LocalDateTime?): String {
    if (quando == null) return "non ancora"
    val ora = String.format(Locale.ITALY, "%02d:%02d", quando.hour, quando.minute)
    return when (quando.toLocalDate()) {
        LocalDate.now() -> "oggi alle $ora"
        LocalDate.now().minusDays(1) -> "ieri alle $ora"
        else -> "il ${quando.dayOfMonth}/${quando.monthValue} alle $ora"
    }
}

/**
 * La localita' per esteso: dove, con che coordinate, in che fuso, e **chi l'ha
 * scelta**.
 *
 * L'ultima parte non e' un dettaglio: un posto deciso dal telefono e un posto
 * scelto a mano si comportano diversamente quando ci si sposta, e finora non
 * c'era nessun punto dell'app che dicesse quale dei due fosse in corso.
 */
private fun dettaglioLocalita(state: UiState): String {
    val dove = listOfNotNull(
        state.place.name,
        state.place.detail.takeIf { it.isNotEmpty() },
    ).joinToString(", ")
    val punto = String.format(
        Locale.ITALY,
        "%.2f / %.2f",
        state.place.latitude,
        state.place.longitude,
    )
    val chi = if (state.followsLocation) "dal telefono" else "scelta a mano"
    val fuso = state.forecast?.utcOffsetSeconds?.let { fusoOrario(it) }
    return listOfNotNull(dove, punto, fuso, chi).joinToString(" · ")
}

/** Lo scarto dall'ora universale, scritto come lo scrive il mondo: `UTC+2`. */
private fun fusoOrario(secondi: Int): String {
    val segno = if (secondi < 0) "-" else "+"
    val minuti = kotlin.math.abs(secondi) / 60
    val ore = minuti / 60
    val resto = minuti % 60
    return if (resto == 0) "UTC$segno$ore" else "UTC$segno$ore:%02d".format(resto)
}
