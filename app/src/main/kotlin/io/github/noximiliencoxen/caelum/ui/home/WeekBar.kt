package io.github.noximiliencoxen.caelum.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noximiliencoxen.caelum.data.DayForecast
import io.github.noximiliencoxen.caelum.data.Wmo
import io.github.noximiliencoxen.caelum.prefs.TempUnit
import io.github.noximiliencoxen.caelum.ui.asPlainDegrees
import io.github.noximiliencoxen.caelum.ui.temperature.WeatherGlyph
import io.github.noximiliencoxen.caelum.ui.theme.LocalMeteoColors
import io.github.noximiliencoxen.caelum.ui.theme.MeteoType

/**
 * La settimana in fondo alla schermata principale.
 *
 * Sta nello stesso posto della striscia delle ore e si alternano ([HourBar]):
 * sono due modi di guardare la stessa previsione - uno dice **quando** dentro
 * oggi, l'altro dice **quale giorno** - e mettendoli uno sotto l'altro la
 * scultura sopra avrebbe dovuto stringersi per far posto a entrambi.
 *
 * **Una colonna si tocca e il giorno si apre**, sullo stesso dettaglio che apre
 * la scheda "LA SETTIMANA": `openDayDetail` esisteva gia' e porta con se' anche
 * la linguetta giusta in cima alla schermata nuova. Il bersaglio e' la colonna
 * intera - sigla, icona e le due cifre - e non la sola icona: sono novanta punti
 * per un ottavo di larghezza, cioe' abbondantemente sopra il minimo, mentre
 * centrare un glifo da ventisei punti sarebbe una prova di mira.
 *
 * Che gli otto bersagli stiano dove passa il pollice che scorre le ore non e' un
 * conflitto: quando c'e' la settimana la striscia delle ore non c'e', e viceversa.
 * Nessun dito puo' trovarsi sull'una credendo di toccare l'altra.
 *
 * Non c'e' un giorno "selezionato" evidenziato: qui il giorno che conta e'
 * sempre oggi, che sta gia' in prima colonna e si chiama OGGI. L'evidenza
 * servirebbe se restasse una scelta in piedi, e aprire un giorno porta via da
 * questa schermata invece di cambiare qualcosa qui.
 */
@Composable
fun WeekBar(
    days: List<DayForecast>,
    unit: TempUnit,
    onOpenDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current

    // ── Solo i giorni che hanno davvero una temperatura ────────────────────────
    //
    // Non tutti i modelli arrivano a otto giorni. **ICON-2I**, quello che l'app
    // sceglie da sola in Italia, e' ad alta risoluzione e a corto raggio: si
    // ferma a tre giorni, e dal quarto in poi l'API risponde `null` - non un
    // errore, proprio il valore mancante. Con le colonne fisse la striscia
    // usciva "37° 33° 33° -- -- -- -- --", cinque ottavi di larghezza spesi per
    // dire cinque volte che non si sa.
    //
    // Meglio poche colonne piene che otto mezze vuote: chi ha scelto un modello
    // locale ha scelto la precisione al posto della distanza, e la striscia lo
    // rispecchia invece di lasciare i buchi in mostra.
    // `withIndex` prima del filtro, e non dopo: chi apre il dettaglio conta
    // sulla posizione dentro `forecast.days`, non dentro le colonne mostrate. Se
    // un giorno resta fuori perche' il modello non ci arriva, gli indici delle
    // colonne e quelli della previsione smettono di coincidere - e si aprirebbe
    // il giorno sbagliato.
    val shown = days.withIndex().filter { it.value.tempMax != null }.take(GIORNI)
    if (shown.isEmpty()) return

    Row(modifier = modifier.fillMaxWidth()) {
        shown.forEach { (indice, day) ->
            // Ogni colonna e' una voce sola per chi ascolta: letta a pezzi
            // sarebbe "lunedi", "sereno", "trentadue gradi", "venti gradi",
            // quattro fermate per un'informazione che si guarda in un colpo.
            val detto = listOf(
                day.label,
                Wmo.condition(day.weatherCode).lowercase(),
                "massima ${day.tempMax.asPlainDegrees(unit)}",
                "minima ${day.tempMin.asPlainDegrees(unit)}",
            ).joinToString(", ")

            val interaction = remember { MutableInteractionSource() }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = interaction,
                        // Come dappertutto in questa schermata: sopra un cielo
                        // che cambia colore un alone grigio e' l'unica cosa che
                        // sembri un pulsante di sistema. Il riscontro qui e' il
                        // dettaglio che entra in scena.
                        indication = null,
                        onClickLabel = APERTURA,
                        onClick = { onOpenDay(indice) },
                    )
                    // `clearAndSetSemantics` cancella anche l'azione del
                    // `clickable`, non solo il testo dei figli: va rimessa a
                    // mano, altrimenti la colonna si legge ma non si attiva da
                    // TalkBack.
                    .clearAndSetSemantics {
                        contentDescription = detto
                        role = Role.Button
                        onClick(label = APERTURA) { onOpenDay(indice); true }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.label,
                    style = MeteoType.caption,
                    color = colors.label,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                WeatherGlyph(
                    weatherCode = day.weatherCode,
                    // La striscia racconta giornate intere, non istanti: una
                    // luna sopra un giorno di cui si danno massima e minima
                    // direbbe che quel giorno e' notte.
                    isDay = true,
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = 4.dp)
                        .size(GLIFO),
                )
                Text(
                    text = day.tempMax.asPlainDegrees(unit),
                    style = MASSIMA,
                    color = colors.text,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = day.tempMin.asPlainDegrees(unit),
                    style = MeteoType.value,
                    color = colors.label,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Il comando che alterna le ore e la settimana.
 *
 * Due parole e nessuna cornice: interruttori, linguette e pillole sono tutta
 * grafica che va disegnata, colorata nei due temi e tenuta leggibile sopra un
 * cielo che cambia colore tutto il giorno. La parola accesa e' quella che si sta
 * guardando, la spenta e' l'altra, e si tocca quella per andarci.
 *
 * Il puntino in mezzo non e' decorazione: senza, le due parole si leggono come
 * una sola etichetta ("ORE SETTIMANA") invece che come due scelte.
 *
 * **`colors.text` contro `colors.label` non bastava.** Era la prima versione, ed
 * e' l'accoppiata che la schermata usa dappertutto - ma altrove distingue un
 * titolo da una didascalia, cioe' due cose che si capiscono anche dal posto e
 * dalla dimensione. Qui doveva dire *quale delle due e' accesa*, e non ce la
 * faceva: `label` e' `text` smorzato sul cielo (`mutedOnBoth`), e su un cielo
 * diurno chiaro i due grigi finiscono a un soffio l'uno dall'altro. Sullo
 * schermo si leggeva "ORE · SETTIMANA" come una didascalia sola, e chi cercava
 * le ore non trovava il comando per tornarci.
 *
 * Adesso la spenta e' anche **trasparente**: l'opacita' non dipende da quanto il
 * cielo e' chiaro, quindi la differenza c'e' alle sette del mattino come a
 * mezzanotte. Vale la pena ricordarlo prima di sostituirla con un colore nuovo:
 * ogni tinta fissa qui va riprovata contro un fondo che cambia tutto il giorno.
 */
@Composable
fun BarSwitch(
    settimana: Boolean,
    onChoose: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Voce(
            testo = "ORE",
            attiva = !settimana,
            onClick = { onChoose(false) },
        )
        Text(
            text = "·",
            style = MeteoType.caption,
            color = colors.label,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Voce(
            testo = "SETTIMANA",
            attiva = settimana,
            onClick = { onChoose(true) },
        )
    }
}

/**
 * Una delle due parole.
 *
 * Il bersaglio e' piu' largo della parola: `padding` **dentro** il `clickable`,
 * cosi' i punti in piu' si toccano invece di stare li' a guardare. Una parola
 * di tre lettere alta dodici punti e' sotto il minimo che un pollice trova al
 * primo colpo, e questa e' l'unica via per la meta' della schermata che
 * l'altra parola non mostra.
 */
@Composable
private fun Voce(
    testo: String,
    attiva: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }

    Text(
        text = testo,
        style = MeteoType.caption,
        color = if (attiva) colors.text else colors.text.copy(alpha = SPENTA),
        maxLines = 1,
        modifier = Modifier
            .clickable(
                interactionSource = interaction,
                // Niente alone: sopra il cielo un cerchio grigio che si allarga
                // e' l'unica cosa di questa schermata che sembri un pulsante di
                // sistema. Il riscontro e' la parola che si accende.
                indication = null,
                enabled = !attiva,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * Otto colonne: oggi piu' sette.
 *
 * E' il numero che l'app scarica (`forecast_days=8`), e sotto le otto le
 * colonne cominciano a stare strette: a 360 punti di larghezza ne restano 45
 * per colonna, che bastano a "OGGI" e a due cifre e non a molto altro.
 */
private const val GIORNI = 8

/**
 * Cosa promette il tocco, detto a chi la schermata la ascolta invece di
 * guardarla. TalkBack lo legge come "doppio tocco per <questo>", quindi e' un
 * verbo all'infinito e non una frase.
 */
private const val APERTURA = "aprire il dettaglio del giorno"

/**
 * Quanto resta della parola spenta.
 *
 * Poco meno della meta': abbastanza da leggerla - e' pur sempre il comando per
 * arrivarci - e abbastanza poco da non scambiarla per quella accesa nemmeno con
 * la coda dell'occhio. Sotto un terzo sparirebbe sul cielo chiaro di
 * mezzogiorno, che e' proprio il caso in cui serviva di piu'.
 */
private const val SPENTA = 0.42f

private val GLIFO = 26.dp

/**
 * La massima e' l'unica cifra della striscia che si legge da lontano: e' quella
 * che si cerca guardando la settimana. La minima resta sotto, piu' piccola e
 * piu' spenta, perche' e' un contorno del numero sopra e non un secondo numero
 * con pari dignita'.
 */
private val MASSIMA = MeteoType.label.copy(fontSize = 17.sp)
