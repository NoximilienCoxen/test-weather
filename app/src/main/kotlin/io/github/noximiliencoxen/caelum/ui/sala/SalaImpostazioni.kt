package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.lingua.SceltaLingua
import io.github.noximiliencoxen.caelum.lingua.Lingue
import io.github.noximiliencoxen.caelum.lingua.tr
import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.BuildConfig
import io.github.noximiliencoxen.caelum.notifiche.PioggiaInArrivoWorker
import io.github.noximiliencoxen.caelum.widget.AggiornaWidgetWorker
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
 * ## Per argomento, non per comando
 *
 * Erano quindici pastiglie identiche in fila, con "Le località" inchiodata in
 * fondo sopra la lista che le scorreva dietro: il tema pesava quanto "Vento
 * forte", e per capire cosa facesse cosa bisognava leggerle tutte. Adesso sono
 * sette gruppi col titolo fuori dal riquadro - località, aspetto, unità,
 * avvisi, notifiche, aiuto, dati - e ogni scelta ha sotto una nota che **cambia
 * con la scelta** e dice cosa succede, invece di ripetere il nome della voce.
 *
 * **Le località stanno in cima**, dentro la lista: sono la cosa che si cambia
 * piu' spesso, e inchiodate in fondo coprivano la voce che scorreva sotto.
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
    onToggleSchedeLarghe: (Boolean) -> Unit,
    onApriGuida: () -> Unit,
    onToggleNotifichePioggia: (Boolean) -> Unit,
    onToggleNotificheAllerte: (Boolean) -> Unit,
    onChooseTheme: (CardTheme) -> Unit,
    onChooseUnit: (TempUnit) -> Unit,
    onChooseWindUnit: (SalaWindUnit) -> Unit,
    onChooseCaptionStyle: (CaptionStyle) -> Unit,
    onToggleAlert: (AlertToggleKind, Boolean) -> Unit,
    onApriLocalita: () -> Unit,
    onAggiorna: () -> Unit,
    onApriLegali: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp),
    ) {
        IntestazioneServizio(titolo = tr("Impostazioni", "Settings"), palette = palette, onIndietro = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            GruppoImpostazioni(titolo = tr("LOCALITÀ", "PLACE"), palette = palette) {
                val salvate = state.favorites.size
                VoceImpostazione(
                    titolo = state.place.name,
                    nota = listOf(
                        if (state.followsLocation) tr("dalla posizione del telefono", "from the phone's location") else tr("scelta a mano", "chosen by hand"),
                        if (salvate == 1) tr("1 salvata", "1 saved") else tr("$salvate salvate", "$salvate saved"),
                    ).joinToString(" · "),
                    palette = palette,
                    onClick = onApriLocalita,
                    coda = { FrecciaAvanti(palette, testo = tr("Cambia", "Change")) },
                )
            }

            GruppoImpostazioni(titolo = tr("ASPETTO", "APPEARANCE"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Tema", "Theme"),
                    nota = when (state.cardTheme) {
                        CardTheme.AUTO -> tr("chiaro col sole, scuro quando tramonta sulla località", "light while the sun is up, dark once it sets over the place")
                        CardTheme.CHIARO -> tr("sempre chiaro, a qualunque ora", "always light, at any hour")
                        CardTheme.SCURO -> tr("sempre scuro, a qualunque ora", "always dark, at any hour")
                    },
                    palette = palette,
                    sotto = {
                        SceltaSegmentata(
                            voci = listOf(
                                CardTheme.AUTO to tr("Segui il cielo", "Follow the sky"),
                                CardTheme.CHIARO to tr("Chiaro", "Light"),
                                CardTheme.SCURO to tr("Scuro", "Dark"),
                            ),
                            scelta = state.cardTheme,
                            palette = palette,
                            onScegli = onChooseTheme,
                        )
                    },
                )
                FiloGruppo(palette)
                VoceImpostazione(
                    titolo = tr("Testi delle sale", "Room texts"),
                    nota = when (state.captionStyle) {
                        CaptionStyle.COMPLETE -> tr("titolo, numeri e il paragrafo che li spiega", "title, numbers and the paragraph that explains them")
                        CaptionStyle.BREVI -> tr("solo titolo e numeri, senza il paragrafo", "only title and numbers, without the paragraph")
                    },
                    palette = palette,
                    sotto = {
                        SceltaSegmentata(
                            voci = listOf(CaptionStyle.COMPLETE to tr("Completi", "Full"), CaptionStyle.BREVI to tr("Brevi", "Short")),
                            scelta = state.captionStyle,
                            palette = palette,
                            onScegli = onChooseCaptionStyle,
                        )
                    },
                )
                FiloGruppo(palette)
                // **La lingua non e' uno stato di Compose**: i testi la leggono
                // da `Lingue` mentre si compongono, anche fuori dalle schermate
                // (i widget, le notifiche). Cambiarla rifa' l'attivita', che
                // ricompone tutto da capo; il ViewModel resta, i dati pure.
                val attivita = LocalContext.current
                VoceImpostazione(
                    titolo = tr("Lingua", "Language"),
                    nota = when (Lingue.scelta) {
                        SceltaLingua.AUTOMATICA -> tr("quella del telefono: italiano se è in italiano, se no inglese", "the phone's: Italian if it is set to Italian, otherwise English")
                        SceltaLingua.ITALIANO -> tr("sempre in italiano", "always Italian")
                        SceltaLingua.INGLESE -> tr("sempre in inglese", "always English")
                    },
                    palette = palette,
                    sotto = {
                        SceltaSegmentata(
                            voci = listOf(
                                SceltaLingua.AUTOMATICA to tr("Automatica", "Automatic"),
                                SceltaLingua.ITALIANO to "Italiano",
                                SceltaLingua.INGLESE to "English",
                            ),
                            scelta = Lingue.scelta,
                            palette = palette,
                            onScegli = { nuova ->
                                if (nuova != Lingue.scelta) {
                                    Lingue.scegli(attivita, nuova)
                                    AggiornaWidgetWorker.appenaPossibile(attivita)
                                    (attivita as? Activity)?.recreate()
                                }
                            },
                        )
                    },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Animazioni ridotte", "Reduced motion"),
                    nota = tr("il cielo sta fermo e il telefono non vibra con pioggia e neve", "the sky stays still and the phone does not buzz with rain and snow"),
                    acceso = state.animazioniRidotte,
                    palette = palette,
                    onCambia = onToggleAnimazioni,
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Schede larghe", "Wide cards"),
                    nota = tr("toglie la colonna delle sale: si passa dall'una all'altra col dito", "hides the room column: swipe from one room to the next"),
                    acceso = state.schedeLarghe,
                    palette = palette,
                    onCambia = onToggleSchedeLarghe,
                )
            }

            // Le unita' stanno sulla riga del loro nome: due o tre voci corte
            // non hanno bisogno di una riga intera, e il gruppo resta basso.
            GruppoImpostazioni(titolo = tr("UNITÀ DI MISURA", "UNITS"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Temperatura", "Temperature"),
                    palette = palette,
                    coda = {
                        SceltaSegmentata(
                            voci = listOf(TempUnit.CELSIUS to "°C", TempUnit.FAHRENHEIT to "°F"),
                            scelta = state.unit,
                            palette = palette,
                            onScegli = onChooseUnit,
                            modifier = Modifier.width(LarghezzaUnita),
                        )
                    },
                )
                FiloGruppo(palette)
                VoceImpostazione(
                    titolo = tr("Vento", "Wind"),
                    palette = palette,
                    coda = {
                        SceltaSegmentata(
                            voci = SalaWindUnit.entries.map { it to it.label },
                            scelta = state.windUnit,
                            palette = palette,
                            onScegli = onChooseWindUnit,
                            modifier = Modifier.width(LarghezzaUnita),
                        )
                    },
                )
            }

            // **Gli avvisi filtrano solo quelli calcolati.** Un'allerta del
            // servizio meteorologico non la si nasconde perche' un interruttore e'
            // giu': la scelta e' su cio' che questa applicazione deduce dalle
            // soglie, non su cio' che un ente dichiara. La nota sotto il gruppo
            // lo dice, perche' prima lo sapeva solo questo commento.
            GruppoImpostazioni(
                titolo = tr("AVVISI NELLE SALE", "NOTICES IN THE ROOMS"),
                palette = palette,
                nota = tr(
                    "Le allerte ufficiali di MeteoAlarm si vedono sempre: qui scegli solo " +
                        "gli avvisi che l'app ricava dalla previsione.",
                    "Official MeteoAlarm warnings always show: here you only choose " +
                        "the notices the app works out from the forecast.",
                ),
            ) {
                VoceInterruttore(
                    titolo = tr("Pioggia intensa", "Heavy rain"),
                    nota = tr("quando sono attesi molti millimetri", "when a lot of millimetres are expected"),
                    acceso = state.alertToggles.pioggiaIntensa,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.PIOGGIA, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Temporali", "Thunderstorms"),
                    nota = tr("quando la previsione dà temporale", "when the forecast gives thunderstorms"),
                    acceso = state.alertToggles.temporali,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.TEMPORALE, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Raggi UV alti", "High UV"),
                    nota = tr("indice sopra 6, dove la scala mondiale passa ad alto", "index above 6, where the world scale turns to high"),
                    acceso = state.alertToggles.uvAlto,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.UV, it) },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Vento forte", "Strong wind"),
                    nota = tr("quando sono attese raffiche forti", "when strong gusts are expected"),
                    acceso = state.alertToggles.ventoForte,
                    palette = palette,
                    onCambia = { onToggleAlert(AlertToggleKind.VENTO, it) },
                )
            }

            // Accenderle senza il permesso non accenderebbe niente: su Android
            // 13 e oltre l'interruttore lo chiede, e se viene negato le
            // notifiche restano spente, perche' un interruttore acceso che non
            // fa niente e' peggio di uno spento.
            val contesto = LocalContext.current
            val chiediPermesso = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleNotifichePioggia(concesso) }
            val chiediPermessoAllerte = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { concesso -> onToggleNotificheAllerte(concesso) }
            GruppoImpostazioni(titolo = tr("NOTIFICHE", "NOTIFICATIONS"), palette = palette) {
                VoceInterruttore(
                    titolo = tr("Pioggia e grandine in arrivo", "Rain and hail on the way"),
                    nota = tr("un avviso poco prima che cominci, sulla località dell'app", "a heads-up shortly before it starts, for the app's place"),
                    acceso = state.notifichePioggia && PioggiaInArrivoWorker.puoNotificare(contesto),
                    palette = palette,
                    onCambia = { vuole ->
                        when {
                            !vuole -> onToggleNotifichePioggia(false)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !PioggiaInArrivoWorker.puoNotificare(contesto) ->
                                chiediPermesso.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onToggleNotifichePioggia(true)
                        }
                    },
                )
                FiloGruppo(palette)
                VoceInterruttore(
                    titolo = tr("Allerte ufficiali", "Official warnings"),
                    nota = tr("arancioni e rosse, diramate dagli enti; le gialle restano nel bollettino", "orange and red, issued by the authorities; yellow ones stay in the bulletin"),
                    acceso = state.notificheAllerte && PioggiaInArrivoWorker.puoNotificare(contesto),
                    palette = palette,
                    onCambia = { vuole ->
                        when {
                            !vuole -> onToggleNotificheAllerte(false)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !PioggiaInArrivoWorker.puoNotificare(contesto) ->
                                chiediPermessoAllerte.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> onToggleNotificheAllerte(true)
                        }
                    },
                )
            }

            GruppoImpostazioni(titolo = tr("AIUTO", "HELP"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Guida all'uso", "How to use it"),
                    nota = tr("come si sfogliano le sale e si cambia ora", "how to move through the rooms and change the hour"),
                    palette = palette,
                    onClick = onApriGuida,
                    coda = { FrecciaAvanti(palette) },
                )
                FiloGruppo(palette)
                VoceImpostazione(
                    titolo = tr("Note legali e privacy", "Legal notes and privacy"),
                    nota = tr("fonti dei dati, cosa esce dal telefono, permessi", "data sources, what leaves the phone, permissions"),
                    palette = palette,
                    onClick = onApriLegali,
                    coda = { FrecciaAvanti(palette) },
                )
            }

            // ── Le informazioni ──────────────────────────────────────────
            //
            // Tutto quello che c'e' qui dentro **esisteva gia' nel codice** e
            // non lo leggeva nessuna schermata (vedi CONTESTO 28.7). L'ultimo
            // aggiornamento sta sulla riga che lo rifa': "quando" e "rifallo"
            // sono la stessa domanda, e in due righe diverse sembravano due.
            //
            // **`refresh()` era pubblico e non lo chiamava nessuna schermata**,
            // e `state.error` - un messaggio gia' scritto per chi guarda - non
            // aveva un solo lettore in tutta l'app. Qui trovano tutti e due il
            // loro posto: si riprova a mano, e se va storto lo si legge.
            GruppoImpostazioni(titolo = tr("DA DOVE VENGONO I NUMERI", "WHERE THE NUMBERS COME FROM"), palette = palette) {
                VoceImpostazione(
                    titolo = tr("Aggiorna adesso", "Refresh now"),
                    nota = state.error ?: tr("ultimo aggiornamento ${quandoScaricata(state.fetchedAt)}", "last updated ${quandoScaricata(state.fetchedAt)}"),
                    notaInRisalto = state.error != null,
                    palette = palette,
                    onClick = onAggiorna,
                    coda = { PastigliaAggiorna(palette) },
                )
                FiloGruppo(palette)
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    VoceInformativa(tr("Previsione", "Forecast"), tr("Open-Meteo · modello ${state.model.label}", "Open-Meteo · model ${state.model.label}"), palette)
                    VoceInformativa(tr("Qualità dell'aria", "Air quality"), "Open-Meteo", palette)
                    VoceInformativa(tr("Allerte", "Warnings"), tr("MeteoAlarm, più avvisi calcolati sui dati", "MeteoAlarm, plus notices worked out from the data"), palette)
                    VoceInformativa(tr("Località", "Place"), dettaglioLocalita(state), palette)
                    VoceInformativa(tr("Versione", "Version"), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", palette)
                }
            }
        }
    }
}

/** Larga abbastanza per "km/h", "m/s" e "nodi" senza puntini, e uguale per le due righe. */
private val LarghezzaUnita = 176.dp

/** Il segno di "Aggiorna adesso": una pastiglia d'accento, perche' e' un'azione e non una porta. */
@Composable
private fun PastigliaAggiorna(palette: SalaPalette) {
    Text(
        text = tr("Aggiorna", "Refresh"),
        style = SalaType.pill,
        color = palette.accentInk,
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.accent)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * Una voce che **dice** invece di comandare: etichetta a sinistra, valore a
 * destra, sulla stessa riga.
 *
 * Non e' una [VoceImpostazione]: quella e' alta quanto un bersaglio del
 * pollice, e cinque fatti di una riga ciascuno non si toccano, si leggono.
 * Stanno fitti, sotto un filo, nello stesso riquadro della riga che li rifa'.
 */
@Composable
private fun VoceInformativa(etichetta: String, valore: String, palette: SalaPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
    if (quando == null) return tr("non ancora", "not yet")
    val ora = String.format(Locale.ITALY, "%02d:%02d", quando.hour, quando.minute)
    return when (quando.toLocalDate()) {
        LocalDate.now() -> tr("oggi alle $ora", "today at $ora")
        LocalDate.now().minusDays(1) -> tr("ieri alle $ora", "yesterday at $ora")
        else -> tr("il ${quando.dayOfMonth}/${quando.monthValue} alle $ora", "on ${quando.dayOfMonth}/${quando.monthValue} at $ora")
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
        Lingue.locale,
        "%.2f / %.2f",
        state.place.latitude,
        state.place.longitude,
    )
    val chi = if (state.followsLocation) tr("dal telefono", "from the phone") else tr("scelta a mano", "chosen by hand")
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
