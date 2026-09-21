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
                    "Posizione approssimativa — approssimativa, non precisa: serve a capire " +
                        "in quale città sei, non dove sei dentro casa. Si usa solo quando " +
                        "chiedi tu di seguire la posizione, e la località resta quella scelta " +
                        "a mano finché non lo chiedi.",
                    palette,
                )
            }

            // **Questo blocco e' quello da riempire alle fonti.** La frase di
            // attribuzione che Open-Meteo richiede e i termini di MeteoAlarm per
            // il riuso dei feed stanno sulle loro pagine di licenza, e vanno
            // copiati verbatim al posto della riga generica qui sotto. Finche'
            // non ci sono, la riga dice una cosa vera e non impegna nessuno: che
            // i dati sono di chi li fornisce e valgono le sue condizioni.
            BloccoImpostazioni(etichetta = "ATTRIBUZIONE", palette = palette) {
                Paragrafo(
                    "I dati meteorologici e di qualità dell'aria sono forniti da Open-Meteo; " +
                        "le allerte ufficiali dai servizi meteorologici nazionali attraverso " +
                        "MeteoAlarm. L'uso di quei dati è soggetto alle condizioni pubblicate " +
                        "da chi li fornisce.",
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
