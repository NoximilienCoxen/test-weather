package com.forli.meteo.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.SkyState
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asBigTemperature
import com.forli.meteo.ui.motion.PhysicalNumber
import com.forli.meteo.ui.motion.SceneRotation
import com.forli.meteo.ui.motion.rememberSceneRotation
import com.forli.meteo.ui.motion.rotatesScene
import com.forli.meteo.ui.render3d.SceneContact
import com.forli.meteo.ui.theme.LocalMeteoColors
import com.forli.meteo.ui.theme.MeteoType
import java.time.LocalDate

/**
 * Quello che serve sapere aprendo l'app: che tempo fa adesso, quanti gradi, e
 * come sara' nelle prossime ore. Tutto il resto sta un trascinamento piu' su.
 */
@Composable
fun HomeScreen(
    state: UiState,
    sky: SkyState,
    tilt: State<Offset>,
    onSelectHour: (Int) -> Unit,
    onBackToNow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val hours = state.hours
    val hour = state.hour

    // Un solo orientamento per la scultura e la cifra: sono un oggetto solo
    // visto da un punto solo, e il gesto che li gira e' lo stesso.
    val rotation: SceneRotation = rememberSceneRotation()

    // E un solo mondo: la pioggia esce dalla nuvola e finisce sulla cifra, che
    // sta in un'altra tela. Qui passano la sagoma e le due origini.
    val contact = remember { SceneContact() }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 24.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsButton(onClick = onOpenSettings)
            Text(
                text = state.place.name.uppercase(),
                style = MeteoType.caption,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // Occupa quanto il pulsante a sinistra, cosi' il nome resta al
            // centro dello schermo e non al centro di quel che avanza.
            Spacer(Modifier.width(34.dp))
        }

        // Scultura e cifra dentro lo stesso riquadro sensibile: il dito li
        // gira insieme dovunque lo si appoggi, invece di dover indovinare
        // quale dei due accetta il gesto.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .rotatesScene(rotation),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WeatherSculpture(
                weatherCode = state.forcedWeatherCode ?: hour?.weatherCode,
                // L'aggancio di verifica deve restare fedele: imporre pioggia a
                // qualunque codice faceva piovere anche su "coperto", che e'
                // asciutto. Solo i codici bagnati portano gocce.
                precipitationMm = state.forcedWeatherCode
                    ?.let { if (Wmo.family(it).isWet()) 2.5 else 0.0 }
                    ?: hour?.precipitation,
                probability = state.forcedWeatherCode
                    ?.let { if (Wmo.family(it).isWet()) 80 else 0 }
                    ?: hour?.precipProbability,
                sky = sky,
                date = hour?.time?.toLocalDate() ?: LocalDate.now(),
                rotation = rotation,
                tilt = tilt,
                // Dietro le impostazioni la schermata resta viva: senza questo
                // il telefono continuerebbe a vibrare di pioggia mentre si
                // sceglie una citta'.
                feelsIt = !state.settingsOpen,
                contact = contact,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.62f),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                // Finche' non c'e' un numero non si disegna niente. Un "--"
                // alto mezzo schermo, con tanto di spessore e di ombra, non
                // dice "sto aspettando": dice che l'app e' rotta.
                val degrees = hour?.temperature
                if (degrees != null) PhysicalNumber(
                    text = degrees.asBigTemperature(state.unit),
                    fontSize = maxHeight * 0.86f,
                    rotation = rotation,
                    tilt = tilt,
                    // Un filo verso l'alto: la cifra e la scultura devono
                    // leggersi come un oggetto solo, e fra loro non ci deve
                    // stare il vuoto che ci starebbe centrandole entrambe.
                    verticalBias = -0.04f,
                    contact = contact,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Crossfade e non sostituzione secca: scorrendo le ore la condizione
        // cambia spesso, e uno scatto di testo si nota piu' del testo stesso.
        // Al posto della condizione, finche' non c'e', si dice cosa sta
        // succedendo. Uno schermo fermo sui trattini lascia credere che
        // l'attesa sia il risultato.
        Crossfade(
            targetState = when {
                state.error != null -> state.error.uppercase()
                hour == null -> "IN ATTESA DEI DATI"
                else -> conditionLabel(hour, state.forcedWeatherCode)
            },
            label = "condizione",
            modifier = Modifier.fillMaxWidth(),
        ) { label ->
            Text(
                text = label,
                style = MeteoType.label,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        HourBar(
            hours = hours,
            selected = state.selectedHour,
            nowIndex = state.nowIndex,
            onSelect = onSelectHour,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        // Tornare all'ora vera deve costare un tocco. Scorrendo la barra si
        // finisce facilmente lontani, e ritrovare la posizione a mano annulla
        // il senso di aver aperto l'app per sapere che tempo fa adesso.
        val onNow = state.selectedHour == state.nowIndex
        val interaction = remember { MutableInteractionSource() }
        Text(
            text = if (onNow) hourLabel(hour) else "${hourLabel(hour)}  ·  ADESSO",
            style = MeteoType.caption,
            color = if (onNow) colors.label else colors.text,
            modifier = Modifier
                .padding(top = 2.dp, bottom = 8.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !onNow,
                    onClick = onBackToNow,
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Tre righe: e' il segno universale, e non serve una libreria di icone. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val colors = LocalMeteoColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val gap = size.height / 3f
            for (i in 0 until 3) {
                val y = gap * (i + 0.5f)
                drawLine(
                    color = colors.label,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height * 0.10f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * La probabilita' compare solo quando c'e' davvero qualcosa da prevedere:
 * "sereno 0%" sarebbe rumore.
 */
private fun conditionLabel(
    hour: com.forli.meteo.data.HourForecast?,
    forcedCode: Int?,
): String {
    if (hour == null && forcedCode == null) return "--"
    val code = forcedCode ?: hour?.weatherCode
    val condition = Wmo.condition(code)
    val wet = Wmo.family(code).isWet()
    val probability = if (forcedCode != null) 80 else hour?.precipProbability ?: 0
    return if (wet && probability > 0) "$condition $probability%" else condition
}
