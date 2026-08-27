package com.forli.meteo.ui.welcome

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.Place
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType

/**
 * La prima schermata: da dove si guarda il tempo.
 *
 * Una domanda sola e due modi di rispondere, perche' sono davvero due i casi:
 * o si vuole il tempo di dove si e', e allora lo chiede il telefono, o si vuole
 * quello di un posto preciso, e allora lo si sceglie.
 *
 * ## Il pulsante che faceva chiudere l'app
 *
 * Premere "TROVAMI" mandava tutto in crash, ed e' il tipo di difetto che ha
 * sempre la stessa forma: la posizione su Android non e' una domanda che si fa
 * e basta. Il permesso puo' mancare - e allora ogni chiamata lancia - o essere
 * stato revocato un istante dopo il controllo; il servizio di sistema puo' non
 * esserci; il fornitore che si nomina puo' non esistere su quel dispositivo, e
 * nominarlo lancia; e il richiamo del risultato puo' arrivare due volte, cosa
 * che riprendendo due volte la stessa continuazione chiude il processo.
 *
 * Qui non c'e' nessuna gestione a valle di quelle eccezioni, perche' non ne
 * escono: `DeviceLocation` restituisce **sempre** un esito, uno dei quattro, e
 * ognuno dei quattro ha una frase da mostrare. Quello che resta da fare a
 * questa schermata e' l'unica cosa che deve fare l'interfaccia - chiedere il
 * permesso quando manca, e non chiederlo piu' quando il sistema ha smesso di
 * mostrarlo.
 */
@Composable
fun WelcomeScreen(
    locating: Boolean,
    problem: String?,
    onLocate: (canAsk: Boolean, onNeedsPermission: () -> Unit) -> Unit,
    onChoosePlace: (Place) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current

    // Vero dopo che il sistema ha gia' mostrato la richiesta e l'utente ha
    // detto di no. Da quel momento richiederlo non apre piu' niente: la finestra
    // semplicemente non compare, e senza saperlo si costruirebbe un pulsante
    // che a ogni tocco non fa nulla senza spiegare perche'.
    var refused by remember { mutableStateOf(false) }
    var chooser by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onLocate(false) {}
        } else {
            refused = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(if (chooser) 0.12f else 1f))

        Text(
            text = "IL TEMPO, DOVE?",
            style = MeteoType.label,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "SERVE UN PUNTO SULLA MAPPA. LO PUÒ DIRE IL TELEFONO, " +
                "OPPURE LO SCEGLI TU.",
            style = MeteoType.caption,
            color = colors.label,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        Action(
            text = when {
                locating -> "STO CERCANDO…"
                refused -> "PERMESSO NEGATO"
                else -> "TROVAMI"
            },
            filled = true,
            enabled = !locating && !refused,
            onClick = {
                onLocate(!refused) { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            },
        )

        Spacer(Modifier.height(10.dp))

        Action(
            text = if (chooser) "CHIUDI L'ELENCO" else "SCEGLI UNA CITTÀ",
            filled = false,
            enabled = !locating,
            onClick = { chooser = !chooser },
        )

        val message = problem ?: if (refused) {
            "SI PUÒ CONCEDERE IN QUALUNQUE MOMENTO DALLE IMPOSTAZIONI DI ANDROID. " +
                "INTANTO, SCEGLI UNA CITTÀ."
        } else {
            null
        }
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MeteoType.caption,
                color = colors.label,
                textAlign = TextAlign.Center,
            )
        }

        if (chooser) {
            Spacer(Modifier.height(18.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    Place.SUGGESTIONS,
                    key = { "${it.name}${it.latitude}${it.longitude}" },
                ) { place ->
                    Row(place = place, onClick = { onChoosePlace(place); onSkip() })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Text(
            text = "PIÙ TARDI",
            style = MeteoType.caption,
            color = colors.label,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSkip,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Action(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMeteoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(
                when {
                    !filled -> Color.Transparent
                    enabled -> colors.pillBackground
                    else -> colors.line
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MeteoType.value,
            color = when {
                !filled -> colors.text
                enabled -> colors.pillText
                else -> colors.label
            },
        )
    }
}

@Composable
private fun Row(place: Place, onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = place.name.uppercase(), style = MeteoType.value, color = colors.text)
        if (place.detail.isNotBlank()) {
            Text(
                text = place.detail.uppercase(),
                style = MeteoType.caption,
                color = colors.label,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
