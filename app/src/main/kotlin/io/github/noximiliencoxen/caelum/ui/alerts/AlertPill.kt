package io.github.noximiliencoxen.caelum.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.noximiliencoxen.caelum.data.WeatherAlert
import io.github.noximiliencoxen.caelum.ui.common.MeteoIconButton
import io.github.noximiliencoxen.caelum.ui.theme.CONTRAST_AA_LARGE
import io.github.noximiliencoxen.caelum.ui.theme.readableOn

/**
 * L'allerta ridotta: un cerchio col triangolo, e nient'altro.
 *
 * **Sta nei 48dp che la riga in cima teneva gia' vuoti.** Quello spazio esiste
 * per bilanciare il pulsante delle impostazioni a sinistra e tenere il nome
 * della localita' al centro dello schermo, non al centro di quel che avanza: e'
 * esattamente `MinTouchTarget`, cioe' la misura di [MeteoIconButton]. Il
 * pallino ci entra senza spostare un pixel di niente e senza rubare altezza -
 * che e' precisamente cio' che si voleva ottenere chiudendo la fascia.
 *
 * **Non e' un colore nuovo.** Fondo e triangolo passano dagli stessi due
 * calcoli della fascia - il contenitore d'errore del tema, e la tinta grezza
 * del livello spinta da `readableOn` fin dove si legge sopra. Il pallino e' la
 * fascia in piccolo, e due tinte scelte separatamente avrebbero finito per
 * divergere alla prima passata sui colori.
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
    // Come nella fascia: `readableOn` costa qualche elevamento a potenza e non
    // va rifatto a ogni fotogramma, quindi sta dietro una chiave che cambia
    // solo col tema o col livello.
    val levelTint: Color = remember(worst.level, background) {
        rawTint(worst.level).readableOn(background, CONTRAST_AA_LARGE)
    }

    val spoken = listOfNotNull(worst.level.label, worst.kind.label, worst.headline)
        .joinToString(". ")

    MeteoIconButton(
        onClick = onOpen,
        contentDescription = "$spoken. Tocca per riaprire l'avviso e il bollettino.",
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            WarningTriangle(levelTint, Modifier.size(15.dp))
        }
    }
}
