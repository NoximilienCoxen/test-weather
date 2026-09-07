package io.github.noximiliencoxen.caelum.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.ui.common.MeteoIconButton

/**
 * L'allerta ridotta: un cerchio col segno dentro, e nient'altro.
 *
 * **Sta nei 48dp che la riga in cima teneva gia' vuoti.** Quello spazio esiste
 * per bilanciare il pulsante delle impostazioni a sinistra e tenere il nome
 * della localita' al centro dello schermo, non al centro di quel che avanza: e'
 * esattamente `MinTouchTarget`, cioe' la misura di [MeteoIconButton]. Il
 * pallino ci entra senza spostare un pixel di niente e senza rubare altezza -
 * che e' precisamente cio' che si voleva ottenere chiudendo la fascia.
 *
 * **Il disco e' cresciuto da trenta a trentasei punti, e il segno da quindici
 * a diciotto.** Il bersaglio no: quello resta i 48 di [MeteoIconButton], quindi
 * l'invariante di sopra regge - la riga e' alta uguale e il nome della
 * localita' non si sposta. I diciotto punti del segno sono gli stessi che ha
 * nella fascia: fra i due stati adesso cambia la misura del contorno, non
 * l'oggetto. Sopra i quaranta il disco arriverebbe a filo del bersaglio e si
 * leggerebbe come un pulsante pieno, che e' l'unica cosa che questa schermata
 * non ha.
 *
 * **Non e' un colore nuovo.** Fondo e segno passano dagli stessi due calcoli
 * della fascia - il contenitore d'errore del tema, e `alertTint`, che tiene
 * i tre colori ufficiali ai soli bollettini ufficiali. Il pallino e' la fascia
 * in piccolo, e due tinte scelte separatamente avrebbero finito per divergere
 * alla prima passata sui colori.
 *
 * **Toccarlo apre il bollettino e insieme rimette la fascia.** Un gesto solo:
 * chi torna indietro ritrova la riga dov'era, e se non la vuole la richiude
 * con la croce in un tocco. L'alternativa - un ripristino sepolto da qualche
 * altra parte - avrebbe reso il pallino un vicolo cieco.
 */
@Composable
fun AlertPill(
    alerts: List<WeatherAlert>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val worst = alerts.maxByOrNull { it.level.weight } ?: return

    val background = MaterialTheme.colorScheme.errorContainer
    // Lo stesso calcolo della fascia, chiamato dallo stesso punto: due tinte
    // scelte a parte divergerebbero alla prima passata sui colori.
    val levelTint: Color = alertTint(worst, background)

    MeteoIconButton(
        onClick = onOpen,
        contentDescription = "${worst.spoken()} Tocca per riaprire l'avviso e il bollettino.",
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            AlertMark(worst, levelTint, Modifier.size(18.dp))
        }
    }
}
