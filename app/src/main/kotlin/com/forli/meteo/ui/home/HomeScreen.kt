package com.forli.meteo.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.WeatherRepository
import com.forli.meteo.data.Wmo
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.asBigNumber
import com.forli.meteo.ui.motion.PhysicalNumber
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
    tilt: Offset,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeteoColors.current
    val hours = state.forecast?.hours.orEmpty()
    val hour: HourForecast? = hours.getOrNull(state.selectedHour)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = WeatherRepository.CITY.uppercase(),
            style = MeteoType.caption,
            color = colors.label,
        )

        // La scultura sta sopra la cifra e le sta vicina: sono un oggetto solo,
        // non due elementi separati da un vuoto.
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
            isDay = hour?.isDay ?: true,
            date = hour?.time?.toLocalDate() ?: LocalDate.now(),
            tilt = tilt,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f),
        )

        // La cifra prende tutto lo spazio che resta e lo riempie davvero: e' la
        // ragione per cui si apre l'app.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            PhysicalNumber(
                text = hour?.temperature.asBigNumber(),
                fontSize = maxHeight * 0.82f,
                tilt = tilt,
                // Centrata: con la cifra spostata in alto restava un vuoto fra
                // lei e la condizione, e la schermata sembrava sfilacciata.
                verticalBias = 0f,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Crossfade e non sostituzione secca: scorrendo le ore la condizione
        // cambia spesso, e uno scatto di testo si nota piu' del testo stesso.
        Crossfade(
            targetState = conditionLabel(hour, state.forcedWeatherCode),
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

        Spacer(Modifier.height(14.dp))

        HourBar(
            hours = hours,
            selected = state.selectedHour,
            onSelect = onSelectHour,
        )

        Text(
            text = hourLabel(hour),
            style = MeteoType.caption,
            color = colors.label,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
    }
}

/**
 * La probabilita' compare solo quando c'e' davvero qualcosa da prevedere:
 * "sereno 0%" sarebbe rumore.
 */
private fun conditionLabel(hour: HourForecast?, forcedCode: Int?): String {
    if (hour == null && forcedCode == null) return "--"
    val code = forcedCode ?: hour?.weatherCode
    val condition = Wmo.condition(code)
    val wet = Wmo.family(code).isWet()
    val probability = if (forcedCode != null) 80 else hour?.precipProbability ?: 0
    return if (wet && probability > 0) "$condition $probability%" else condition
}

/** Famiglie che portano precipitazione, e quindi gocce sulla scultura. */
private fun Wmo.Family.isWet(): Boolean =
    this == Wmo.Family.PIOGGIA || this == Wmo.Family.NEVE || this == Wmo.Family.TEMPORALE
