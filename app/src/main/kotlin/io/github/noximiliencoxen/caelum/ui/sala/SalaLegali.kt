package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.lingua.tr
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

/**
 * Note legali e privacy: cosa fa l'app, detto dove si puo' leggere.
 *
 * **Ogni riga qui dentro e' un fatto verificabile nel codice**, non una formula
 * presa altrove. E' una scelta, e vale la pena dichiararla: queste non sono
 * condizioni d'uso. Un contratto lo scrive chi pubblica l'app e se ne assume la
 * responsabilita'; questa e' una dichiarazione di cosa il programma fa, e la si
 * puo' controllare aprendo i file che nomina.
 *
 * La pagina nasce da due cose diverse che chiedevano lo stesso posto.
 *
 * La prima e' **una decisione rimasta in sospeso da sezione 8-ter di CONTESTO**:
 * *"Restano da decidere: una riga nelle impostazioni che dichiari le due
 * fonti"*, con una motivazione che non e' di stile ma di legge. L'app mette in
 * scena due specie di avvisi - i bollettini di un ente e le soglie che calcola
 * da se' - e il codice gia' non li confonde: `SalaChrome` scrive "ALLERTA" solo
 * per i primi, perche' *"«Allerta» e' una parola che spetta a un ente"*. Quella
 * regola pero' viveva solo in una pastiglia larga due centimetri. Qui e'
 * scritta per esteso.
 *
 * La seconda e' che **nessuna schermata diceva cosa esce dal telefono**. Esce
 * poco: le coordinate della localita' scelta. Ma "poco" detto da chi scrive il
 * programma non vale niente se chi lo usa non ha modo di leggerlo.
 *
 * **Cosa resta da fare, e non l'ho inventato.** La frase di attribuzione che
 * Open-Meteo richiede e i termini di MeteoAlarm per il riuso dei feed vanno
 * presi **verbatim dalle loro pagine di licenza** e messi nell'ultimo blocco al
 * posto della riga generica che c'e' adesso. Scriverli a memoria in una pagina
 * legale sarebbe esattamente il tipo di errore che questa pagina esiste per
 * evitare.
 */
@Composable
fun SalaLegaliScreen(
    palette: SalaPalette,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.schermoPieno)
            .systemBarsPadding()
            .padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 30.dp),
    ) {
        IntestazioneServizio(titolo = tr("Note legali e privacy", "Legal and privacy"), palette = palette, onIndietro = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BloccoImpostazioni(etichetta = tr("DA DOVE VENGONO I DATI", "WHERE THE DATA COMES FROM"), palette = palette) {
                Paragrafo(
                    tr(
                        "La previsione e la qualità dell'aria arrivano da Open-Meteo. La ricerca delle località passa dal suo servizio di geocodifica. Le allerte ufficiali arrivano dai feed di MeteoAlarm.",
                        "The forecast and air quality come from Open-Meteo. Location search goes through its geocoding service. Official warnings come from the MeteoAlarm feeds.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Sono i soli quattro indirizzi che questa applicazione interroga. Non ce ne sono altri.",
                        "These are the only four addresses this app contacts. There are no others.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("ALLERTE E AVVISI NON SONO LA STESSA COSA", "WARNINGS AND ALERTS ARE NOT THE SAME THING"), palette = palette) {
                Paragrafo(
                    tr(
                        "Dove c'è scritto ALLERTA, il bollettino viene da un ente nazionale attraverso MeteoAlarm, ed è quell'ente a dichiararlo.",
                        "Where it says WARNING, the bulletin comes from a national agency through MeteoAlarm, and it is that agency that issues it.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Dove c'è scritto AVVISO, invece, è questa applicazione ad averlo calcolato da sola: confronta i numeri della previsione — millimetri di pioggia, raffiche, indice UV, codice del tempo — con delle soglie scritte nel programma. Non è un avviso di protezione civile e non sostituisce un bollettino ufficiale.",
                        "Where it says ALERT, instead, this app calculated it on its own: it compares the forecast numbers — millimetres of rain, gusts, UV index, weather code — against thresholds written into the program. It is not a civil protection warning and does not replace an official bulletin.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Per la stessa ragione gli avvisi calcolati non arrivano mai al rosso: il rosso è una valutazione del rischio sul territorio, e un confronto fra un numero e una costante non può farla.",
                        "For the same reason calculated alerts never reach red: red is an assessment of risk on the ground, and comparing a number with a constant can't make it.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("COSA ESCE DAL TELEFONO", "WHAT LEAVES THE PHONE"), palette = palette) {
                Paragrafo(
                    tr(
                        "Le coordinate della località mostrata, perché servono a chiedere la previsione per quel punto, e un'etichetta che dice quale programma sta chiedendo.",
                        "The coordinates of the location shown, because they are needed to ask for the forecast for that point, and a label saying which program is asking.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Nient'altro: nessun account, nessun identificativo del telefono, nessuna statistica d'uso, nessun invio automatico degli errori. Non è una promessa, è l'elenco di ciò che l'applicazione contiene: di quelle cose non c'è il codice per farle.",
                        "Nothing else: no account, no phone identifier, no usage statistics, no automatic error reports. It isn't a promise, it's the list of what the app contains: the code to do those things isn't there.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("COSA RESTA SUL TELEFONO", "WHAT STAYS ON THE PHONE"), palette = palette) {
                Paragrafo(
                    tr(
                        "La località scelta con le sue coordinate, le località salvate, le unità di misura, il tema e gli interruttori di questa schermata. Stanno nell'archivio delle impostazioni dell'applicazione e non escono mai.",
                        "The chosen location with its coordinates, the saved locations, the units, the theme and the switches on this screen. They live in the app's settings storage and never leave.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Disinstallando l'applicazione se ne vanno con lei.",
                        "Uninstalling the app removes them with it.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("I PERMESSI, E A COSA SERVONO", "PERMISSIONS, AND WHAT THEY'RE FOR"), palette = palette) {
                Paragrafo(
                    tr(
                        "Internet, per chiedere la previsione.",
                        "Internet, to ask for the forecast.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Vibrazione, per il colpetto che segue la pioggia e gli scatti della barra delle ore. Si spegne da \"Animazioni ridotte\".",
                        "Vibration, for the tap that follows the rain and the clicks of the hour bar. Turned off with \"Reduced motion\".",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Posizione approssimativa — approssimativa, non precisa: serve a capire in quale città sei, non dove sei dentro casa. Si usa solo quando chiedi tu di seguire la posizione, e la località resta quella scelta a mano finché non lo chiedi.",
                        "Approximate location — approximate, not precise: it tells which city you're in, not where you are in the house. It's used only when you ask to follow your location, and the location stays the one chosen by hand until you ask.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Notifiche, per avvisare quando sta per piovere o grandinare sulla città dell'app. Si spengono da \"Pioggia e grandine in arrivo\" nelle impostazioni.",
                        "Notifications, to warn when rain or hail is about to start in the app's city. Turned off with \"Rain and hail on the way\" in the settings.",
                    ),
                    palette,
                )
            }

            // **Questo blocco e' quello da riempire alle fonti.** La frase di
            // attribuzione che Open-Meteo richiede e i termini di MeteoAlarm per
            // il riuso dei feed stanno sulle loro pagine di licenza, e vanno
            // copiati verbatim al posto della riga generica qui sotto. Finche'
            // non ci sono, la riga dice una cosa vera e non impegna nessuno: che
            // i dati sono di chi li fornisce e valgono le sue condizioni.
            BloccoImpostazioni(etichetta = tr("ATTRIBUZIONE", "ATTRIBUTION"), palette = palette) {
                Paragrafo(
                    tr(
                        "I dati meteorologici e di qualità dell'aria sono forniti da Open-Meteo; le allerte ufficiali dai servizi meteorologici nazionali attraverso MeteoAlarm. L'uso di quei dati è soggetto alle condizioni pubblicate da chi li fornisce.",
                        "Weather and air quality data are provided by Open-Meteo; official warnings by the national weather services through MeteoAlarm. Use of that data is subject to the terms published by those who provide it.",
                    ),
                    palette,
                )
            }
        }
    }
}

/** Un capoverso del testo legale: corpo leggibile, non una riga di elenco. */
@Composable
private fun Paragrafo(testo: String, palette: SalaPalette) {
    Text(
        text = testo,
        style = SalaType.body,
        color = palette.inkSoft,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
