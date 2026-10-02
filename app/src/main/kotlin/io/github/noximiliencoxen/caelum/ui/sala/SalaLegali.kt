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
        IntestazioneServizio(titolo = "Note legali e privacy", palette = palette, onIndietro = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BloccoImpostazioni(etichetta = "DA DOVE VENGONO I DATI", palette = palette) {
                Paragrafo(
                    "La previsione e la qualità dell'aria arrivano da Open-Meteo. " +
                        "La ricerca delle località passa dal suo servizio di geocodifica. " +
                        "Le allerte ufficiali arrivano dai feed di MeteoAlarm.",
                    palette,
                )
                Paragrafo(
                    "Sono i soli quattro indirizzi che questa applicazione interroga. " +
                        "Non ce ne sono altri.",
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = "ALLERTE E AVVISI NON SONO LA STESSA COSA", palette = palette) {
                Paragrafo(
                    "Dove c'è scritto ALLERTA, il bollettino viene da un ente nazionale " +
                        "attraverso MeteoAlarm, ed è quell'ente a dichiararlo.",
                    palette,
                )
                Paragrafo(
                    "Dove c'è scritto AVVISO, invece, è questa applicazione ad averlo " +
                        "calcolato da sola: confronta i numeri della previsione — millimetri " +
                        "di pioggia, raffiche, indice UV, codice del tempo — con delle soglie " +
                        "scritte nel programma. Non è un avviso di protezione civile e non " +
                        "sostituisce un bollettino ufficiale.",
                    palette,
                )
                Paragrafo(
                    "Per la stessa ragione gli avvisi calcolati non arrivano mai al rosso: " +
                        "il rosso è una valutazione del rischio sul territorio, e un confronto " +
                        "fra un numero e una costante non può farla.",
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = "COSA ESCE DAL TELEFONO", palette = palette) {
                Paragrafo(
                    "Le coordinate della località mostrata, perché servono a chiedere la " +
                        "previsione per quel punto, e un'etichetta che dice quale programma " +
                        "sta chiedendo.",
                    palette,
                )
                Paragrafo(
                    "Nient'altro: nessun account, nessun identificativo del telefono, " +
                        "nessuna statistica d'uso, nessun invio automatico degli errori. " +
                        "Non è una promessa, è l'elenco di ciò che l'applicazione contiene: " +
                        "di quelle cose non c'è il codice per farle.",
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = "COSA RESTA SUL TELEFONO", palette = palette) {
                Paragrafo(
                    "La località scelta con le sue coordinate, le località salvate, le unità " +
                        "di misura, il tema e gli interruttori di questa schermata. Stanno " +
                        "nell'archivio delle impostazioni dell'applicazione e non escono mai.",
                    palette,
                )
                Paragrafo(
                    "Disinstallando l'applicazione se ne vanno con lei.",
                    palette,
                )
            }

            BloccoImpostazioni(etichetta = "I PERMESSI, E A COSA SERVONO", palette = palette) {
                Paragrafo(
                    "Internet, per chiedere la previsione.",
                    palette,
                )
                Paragrafo(
                    "Vibrazione, per il colpetto che segue la pioggia e gli scatti della " +
                        "barra delle ore. Si spegne da \"Animazioni ridotte\".",
                    palette,
                )
                Paragrafo(
                    "Notifiche, per la pioggia o la grandine in arrivo e per le allerte " +
                        "ufficiali arancioni e rosse sulla località dell'app. Si spengono una " +
                        "per una dal gruppo \"Notifiche\" delle impostazioni.",
                    palette,
                )
                Paragrafo(
                    "Posizione approssimativa — approssimativa, non precisa: serve a capire " +
                        "in quale città sei, non dove sei dentro casa. Si usa solo quando " +
                        "chiedi tu di seguire la posizione, e la località resta quella scelta " +
                        "a mano finché non lo chiedi.",
                    palette,
                )
            }

            // Le fonti, con le parole che chiedono (vedi `Fonti`). Tre licenze,
            // tre paragrafi: chi fornisce cosa, sotto quale licenza, e cosa
            // l'app cambia - la CC BY chiede anche di dirlo.
            BloccoImpostazioni(etichetta = "ATTRIBUZIONE", palette = palette) {
                Paragrafo(
                    "Previsioni: Open-Meteo.com, che usa i dati aperti dei servizi meteorologici " +
                        "nazionali (fra cui DWD, ECMWF e, per l'Italia, ItaliaMeteo-ARPAE). " +
                        "I dati dell'API sono offerti sotto licenza Creative Commons " +
                        "Attribution 4.0 International (CC BY 4.0).",
                    palette,
                )
                Collegamento("Weather data by Open-Meteo.com", Fonti.OPEN_METEO, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
                Collegamento("Licenza e fonti di Open-Meteo", Fonti.OPEN_METEO_LICENZA, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
                Paragrafo(Fonti.CITAZIONE_OPEN_METEO, palette)

                Paragrafo(
                    "Qualità dell'aria e polline: CAMS European air quality forecasts, ENSEMBLE " +
                        "data, del Copernicus Atmosphere Monitoring Service, attraverso " +
                        "Open-Meteo.com. La citazione che chiedono:",
                    palette,
                )
                Paragrafo(Fonti.CITAZIONE_CAMS, palette)
                Collegamento("I dati CAMS su Open-Meteo", Fonti.CAMS_ARIA, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                Paragrafo(
                    "Allerte ufficiali: MeteoAlarm, con i dati forniti dai membri di EUMETNET, " +
                        "sotto licenza CC BY 4.0.",
                    palette,
                )
                Collegamento("meteoalarm.org", Fonti.METEOALARM, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))

                Paragrafo(
                    "Cosa cambia l'app: converte unità e ore nel fuso del posto, ricava da questi " +
                        "dati il cielo disegnato, i testi delle sale e gli avvisi calcolati sulle " +
                        "soglie, e compone in italiano il titolo di ogni allerta; descrizione e " +
                        "istruzioni restano quelle pubblicate dall'ente. Nessuna delle fonti " +
                        "approva o sostiene quest'app.",
                    palette,
                )
                Collegamento("Il testo della licenza CC BY 4.0", Fonti.CC_BY_4, SalaType.body, palette.accent, Modifier.padding(bottom = 8.dp))
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
