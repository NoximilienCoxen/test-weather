package com.forli.meteo.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = WeatherRepository.CITY.uppercase(),
            style = MeteoType.caption,
            color = colors.label,
        )

        WeatherSculpture(
            weatherCode = hour?.weatherCode,
            precipitationMm = hour?.precipitation,
            probability = hour?.precipProbability,
            isDay = hour?.isDay ?: true,
            date = hour?.time?.toLocalDate() ?: LocalDate.now(),
            tilt = tilt,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f),
        )

        PhysicalNumber(
            text = hour?.temperature.asBigNumber(),
            fontSize = 168.dp,
            tilt = tilt,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // Crossfade e non sostituzione secca: scorrendo le ore la condizione
        // cambia spesso, e uno scatto di testo si nota piu' del testo stesso.
        Crossfade(
            targetState = conditionLabel(hour),
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

        Spacer(Modifier.height(18.dp))

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
private fun conditionLabel(hour: HourForecast?): String {
    if (hour == null) return "--"
    val condition = Wmo.condition(hour.weatherCode)
    val probability = hour.precipProbability ?: 0
    val wet = Wmo.family(hour.weatherCode) in
        setOf(Wmo.Family.PIOGGIA, Wmo.Family.NEVE, Wmo.Family.TEMPORALE)
    return if (wet && probability > 0) "$condition $probability%" else condition
}
