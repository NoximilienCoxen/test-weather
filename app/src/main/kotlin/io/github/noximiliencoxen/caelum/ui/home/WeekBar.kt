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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
 * **Solo lettura, per adesso.** La striscia delle ore e' un comando: la si
 * scorre e la scena cambia. Questa no, si guarda e basta. Un tocco per aprire
 * il giorno sarebbe l'aggiunta ovvia - il dettaglio esiste gia' - ma renderebbe
 * toccabili otto bersagli larghi un ottavo di schermo proprio dove il pollice
 * passa per scorrere le ore, e prima vale la pena vedere la striscia in mano.
 *
 * Non c'e' un giorno "selezionato" evidenziato: qui il giorno che conta e'
 * sempre oggi, che sta gia' in prima colonna e si chiama OGGI. L'evidenza
 * servirebbe se si potesse scegliere, e non si puo'.
 */
@Composable
fun WeekBar(
    days: List<DayForecast>,
    unit: TempUnit,
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
    val shown = days.filter { it.tempMax != null }.take(GIORNI)
    if (shown.isEmpty()) return

    Row(modifier = modifier.fillMaxWidth()) {
        shown.forEach { day ->
            // Ogni colonna e' una voce sola per chi ascolta: letta a pezzi
            // sarebbe "lunedi", "sereno", "trentadue gradi", "venti gradi",
            // quattro fermate per un'informazione che si guarda in un colpo.
            val detto = listOf(
                day.label,
                Wmo.condition(day.weatherCode).lowercase(),
                "massima ${day.tempMax.asPlainDegrees(unit)}",
                "minima ${day.tempMin.asPlainDegrees(unit)}",
            ).joinToString(", ")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = detto },
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
 * cielo che cambia colore tutto il giorno. Qui la parola accesa e' quella che
 * si sta guardando, la spenta e' l'altra, e si tocca quella per andarci - lo
 * stesso codice a due colori che la schermata usa gia' dappertutto.
 *
 * Il puntino in mezzo non e' decorazione: senza, le due parole si leggono come
 * una sola etichetta ("ORE SETTIMANA") invece che come due scelte.
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
        color = if (attiva) colors.text else colors.label,
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

private val GLIFO = 26.dp

/**
 * La massima e' l'unica cifra della striscia che si legge da lontano: e' quella
 * che si cerca guardando la settimana. La minima resta sotto, piu' piccola e
 * piu' spenta, perche' e' un contorno del numero sopra e non un secondo numero
 * con pari dignita'.
 */
private val MASSIMA = MeteoType.label.copy(fontSize = 17.sp)
