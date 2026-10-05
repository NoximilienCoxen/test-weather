package io.github.noximiliencoxen.caelum.ui.sala

import io.github.noximiliencoxen.caelum.data.FonteAllerte
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
 * **L'attribuzione viene dalle pagine delle fonti, non dalla memoria.** Era
 * rimasta una riga generica finche' le frasi verbatim non si potevano leggere;
 * le ha scaricate la CI (`scripts/probe_licenze.py`, CONTESTO §47) e stanno in
 * `Fonti`, da dove le legge anche la riga dei crediti sotto la barra delle ore.
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
        IntestazioneServizio(titolo = tr("Note legali e privacy", "Legal notes and privacy"), palette = palette, onIndietro = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BloccoImpostazioni(etichetta = tr("DA DOVE VENGONO I DATI", "WHERE THE DATA COME FROM"), palette = palette) {
                Paragrafo(
                    tr(
                        "La previsione e la qualità dell'aria arrivano da Open-Meteo. " +
                            "La ricerca delle località passa dal suo servizio di geocodifica. " +
                            "Le allerte ufficiali arrivano dai feed di MeteoAlarm in Europa e dal National " +
                            "Weather Service negli Stati Uniti.",
                        "Forecasts and air quality come from Open-Meteo. Place search goes through its geocoding service. " +
                            "Official warnings come from the MeteoAlarm feeds in Europe and from the National Weather Service in the United States.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Sono i soli cinque indirizzi che questa applicazione interroga. " +
                            "Non ce ne sono altri.",
                        "These are the only five addresses this app contacts. There are no others.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("ALLERTE E AVVISI NON SONO LA STESSA COSA", "WARNINGS AND NOTICES ARE NOT THE SAME THING"), palette = palette) {
                Paragrafo(
                    tr(
                        "Dove c'è scritto ALLERTA, l'avviso viene da un servizio meteorologico nazionale - " +
                            "attraverso MeteoAlarm in Europa, dal National Weather Service negli Stati Uniti - " +
                            "ed è quel servizio a emetterlo. È un'allerta meteo, non un messaggio di protezione civile.",
                        "Where it says WARNING, it comes from a national weather service - through MeteoAlarm in Europe, " +
                            "from the National Weather Service in the United States - and it is that service that issues it. " +
                            "It is a weather warning, not a civil protection alert.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Dove c'è scritto AVVISO, invece, è questa applicazione ad averlo " +
                            "calcolato da sola: confronta i numeri della previsione — millimetri " +
                            "di pioggia, raffiche, indice UV, codice del tempo — con delle soglie " +
                            "scritte nel programma. Non è un avviso di protezione civile e non " +
                            "sostituisce un bollettino ufficiale.",
                        "Where it says NOTICE, it is this app that worked it out on its own: it compares the forecast numbers — millimetres of rain, gusts, UV index, weather code — with thresholds written into the program. It is not a civil protection warning and does not replace an official bulletin.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Per la stessa ragione gli avvisi calcolati non arrivano mai al rosso: " +
                            "il rosso è una valutazione del rischio sul territorio, e un confronto " +
                            "fra un numero e una costante non può farla.",
                        "For the same reason calculated notices never reach red: red is an assessment of risk on the ground, and comparing a number with a constant cannot make it.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("COSA ESCE DAL TELEFONO", "WHAT LEAVES THE PHONE"), palette = palette) {
                Paragrafo(
                    tr(
                        "Le coordinate della località mostrata, perché servono a chiedere la " +
                            "previsione per quel punto, e un'etichetta che dice quale programma " +
                            "sta chiedendo.",
                        "The coordinates of the place shown, because they are needed to ask for that spot's forecast, and a label saying which program is asking.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Nient'altro: nessun account, nessun identificativo del telefono, " +
                            "nessuna statistica d'uso, nessun invio automatico degli errori. " +
                            "Non è una promessa, è l'elenco di ciò che l'applicazione contiene: " +
                            "di quelle cose non c'è il codice per farle.",
                        "Nothing else: no account, no phone identifier, no usage statistics, no automatic error reports. It is not a promise, it is the list of what the app contains: the code to do those things is not there.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("COSA RESTA SUL TELEFONO", "WHAT STAYS ON THE PHONE"), palette = palette) {
                Paragrafo(
                    tr(
                        "La località scelta con le sue coordinate, le località salvate, le unità " +
                            "di misura, il tema e gli interruttori di questa schermata. Stanno " +
                            "nell'archivio delle impostazioni dell'applicazione e non escono mai.",
                        "The chosen place with its coordinates, the saved places, the units, the theme and the switches on this screen. They live in the app's settings storage and never leave it.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Disinstallando l'applicazione se ne vanno con lei.",
                        "Uninstalling the app removes them along with it.",
                    ),
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = tr("I PERMESSI, E A COSA SERVONO", "PERMISSIONS, AND WHAT THEY ARE FOR"), palette = palette) {
                Paragrafo(
                    tr(
                        "Internet, per chiedere la previsione.",
                        "Internet, to ask for the forecast.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Vibrazione, per il colpetto che segue la pioggia e gli scatti della " +
                            "barra delle ore. Si spegne da \"Animazioni ridotte\".",
                        "Vibration, for the tap that follows the rain and the clicks of the hour bar. It is turned off from \"Reduced motion\".",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Notifiche, per la pioggia o la grandine in arrivo e per le allerte " +
                            "ufficiali arancioni e rosse sulla località dell'app. Si spengono una " +
                            "per una dal gruppo \"Notifiche\" delle impostazioni.",
                        "Notifications, for rain or hail on the way and for orange and red official warnings for the app's place. They are turned off one by one from the \"Notifications\" group in settings.",
                    ),
                    palette,
                )
                Paragrafo(
                    tr(
                        "Posizione approssimativa — approssimativa, non precisa: serve a capire " +
                            "in quale città sei, non dove sei dentro casa. Si usa solo quando " +
                            "chiedi tu di seguire la posizione, e la località resta quella scelta " +
                            "a mano finché non lo chiedi.",
                        "Approximate location — approximate, not precise: it tells which town you are in, not where you are in the house. It is used only when you ask to follow your location, and the place stays the one chosen by hand until you do.",
                    ),
                    palette,
                )
            }

            // Le fonti, con le parole che chiedono (vedi `Fonti`). Tre licenze,
            // tre paragrafi: chi fornisce cosa, sotto quale licenza, e cosa
            // l'app cambia - la CC BY chiede anche di dirlo.
            BloccoImpostazioni(etichetta = tr("ATTRIBUZIONE", "ATTRIBUTION"), palette = palette) {
                Paragrafo(
                    tr(
                        "Previsioni: Open-Meteo.com, che usa i dati aperti dei servizi meteorologici " +
                            "nazionali (fra cui DWD, ECMWF e, per l'Italia, ItaliaMeteo-ARPAE). " +
                            "I dati dell'API sono offerti sotto licenza Creative Commons " +
                            "Attribution 4.0 International (CC BY 4.0).",
                        "Forecasts: Open-Meteo.com, which uses open data from national weather services (including DWD, ECMWF and, for Italy, ItaliaMeteo-ARPAE). The API data are offered under the Creative Commons Attribution 4.0 International licence (CC BY 4.0).",
                    ),
                    palette,
                )
                Collegamento("Weather data by Open-Meteo.com", Fonti.OPEN_METEO, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
                Collegamento(tr("Licenza e fonti di Open-Meteo", "Open-Meteo licence and sources"), Fonti.OPEN_METEO_LICENZA, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
                Paragrafo(Fonti.CITAZIONE_OPEN_METEO, palette)

                Paragrafo(
                    tr(
                        "Qualità dell'aria e polline: CAMS European air quality forecasts, ENSEMBLE " +
                            "data, del Copernicus Atmosphere Monitoring Service, attraverso " +
                            "Open-Meteo.com. La citazione che chiedono:",
                        "Air quality and pollen: CAMS European air quality forecasts, ENSEMBLE data, from the Copernicus Atmosphere Monitoring Service, through Open-Meteo.com. The citation they ask for:",
                    ),
                    palette,
                )
                Paragrafo(Fonti.CITAZIONE_CAMS, palette)
                Collegamento(tr("I dati CAMS su Open-Meteo", "CAMS data on Open-Meteo"), Fonti.CAMS_ARIA, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                Paragrafo(
                    tr(
                        "Allerte ufficiali: MeteoAlarm, con i dati forniti dai membri di EUMETNET, " +
                            "sotto licenza CC BY 4.0.",
                        "Official warnings: MeteoAlarm, with data provided by EUMETNET members, under the CC BY 4.0 licence.",
                    ),
                    palette,
                )
                Collegamento("meteoalarm.org", Fonti.METEOALARM, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                Paragrafo(FonteAllerte.NWS.credito, palette)
                Collegamento(FonteAllerte.NWS.sito, FonteAllerte.NWS.indirizzo, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                Paragrafo(
                    tr(
                        "Cosa cambia l'app: converte unità e ore nel fuso del posto, ricava da questi " +
                            "dati il cielo disegnato, i testi delle sale e gli avvisi calcolati sulle " +
                            "soglie, e compone in italiano il titolo di ogni allerta; descrizione e " +
                            "istruzioni restano quelle pubblicate dall'ente. Nessuna delle fonti " +
                            "approva o sostiene quest'app.",
                        "What the app changes: it converts units and times to the local time zone, derives from these data the drawn sky, the room texts and the threshold notices, and writes the title of each warning in English; description and instructions remain those published by the authority. None of the sources endorses or supports this app.",
                    ),
                    palette,
                )
                Collegamento(tr("Il testo della licenza CC BY 4.0", "The text of the CC BY 4.0 licence"), Fonti.CC_BY_4, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
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
