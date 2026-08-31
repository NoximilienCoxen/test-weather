package com.forli.meteo.ui.temperature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forli.meteo.data.DayForecast
import com.forli.meteo.data.HourForecast
import com.forli.meteo.data.Wmo
import com.forli.meteo.prefs.TempUnit
import com.forli.meteo.ui.UiState
import com.forli.meteo.ui.WeatherViewModel
import com.forli.meteo.ui.asPlainDegrees
import com.forli.meteo.ui.theme.MeteoType
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)

/**
 * Il dettaglio di un giorno preciso, raggiunto toccandolo nella settimana.
 *
 * Si esce con la freccia in alto a sinistra o col tasto indietro di sistema:
 * chi ci arriva sta gia' scendendo dentro qualcosa, e deve poter risalire nel
 * modo che gli viene per primo.
 */
@Composable
fun DayDetailScreen(
    state: UiState,
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forecast = state.forecast
    val days = forecast?.days.orEmpty()
    val index = state.selectedDay.coerceIn(0, maxOf(days.lastIndex, 0))
    val day = days.getOrNull(index)
    val hours = remember(forecast, day?.date) {
        if (forecast == null || day == null) emptyList() else forecast.hoursOf(day.date)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Sfondo scuro fisso: come il foglio principale del dettaglio,
            // indipendente dall'ora del giorno. I colori del testo (MetricValue,
            // MetricLabel) sono gia' fissi chiari e richiedono uno sfondo scuro.
            .background(androidx.compose.ui.graphics.Color(0xFF1D2026))
            .verticalScroll(rememberScrollState()),
    ) {
        DetailTopBar(title = DetailMode.TEMPERATURA.title, onBack = onBack)

        DayTabs(
            days = days,
            selected = index,
            onSelect = viewModel::selectDay,
            modifier = Modifier.padding(top = 4.dp),
        )

        Divider()

        if (day == null) {
            Text(
                text = "IN ATTESA DEI DATI",
                style = MeteoType.caption,
                color = MetricLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
            )
            return@Column
        }

        HalfDayHeads(
            day = day,
            hours = hours,
            unit = state.unit,
            feelsLike = state.feelsLike,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )

        HourlyTemperatureChart(
            hours = hours,
            unit = state.unit,
            feelsLike = state.feelsLike,
            normTemp = day.normTemp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(260.dp),
        )

        SplitPills(
            labels = listOf("EFFETTIVA", "PERCEPITI"),
            selected = if (state.feelsLike) 1 else 0,
            onSelect = { viewModel.setFeelsLike(it == 1) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        Text(
            text = if (state.feelsLike) {
                "Quanto alta sembra la temperatura, per l'umidita', il vento e il sole."
            } else {
                "La temperatura misurata all'ombra."
            },
            style = MeteoType.value,
            color = MetricLabel,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(18.dp))
        Divider()
        Spacer(Modifier.height(14.dp))

        RainOdds(
            hours = hours,
            fallback = day.precipProbability,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// Le linguette dei giorni
// ---------------------------------------------------------------------------
@Composable
private fun DayTabs(
    days: List<DayForecast>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Colori fissi per sfondo scuro: bianco per attivo, grigio per inattivo
    val activeColor = MetricValue
    val inactiveColor = MetricLabel
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val active = index == selected
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.label,
                    style = MeteoType.caption,
                    color = if (active) activeColor else inactiveColor,
                    maxLines = 1,
                )
                Text(
                    text = "%02d".format(day.date.dayOfMonth),
                    style = MeteoType.title,
                    color = if (active) activeColor else inactiveColor,
                    maxLines = 1,
                )
                Text(
                    text = day.date.format(MONTH_FORMAT).uppercase(Locale.ITALIAN),
                    style = MeteoType.caption,
                    color = if (active) activeColor else inactiveColor,
                    maxLines = 1,
                )
                Spacer(
                    Modifier
                        .padding(top = 6.dp)
                        .width(40.dp)
                        .height(2.dp)
                        .background(if (active) activeColor else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(CardBorder),
    )
}

// ---------------------------------------------------------------------------
// Di giorno / di notte
// ---------------------------------------------------------------------------
@Composable
private fun HalfDayHeads(
    day: DayForecast,
    hours: List<HourForecast>,
    unit: TempUnit,
    feelsLike: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HalfDay(
            title = "DI GIORNO",
            value = if (feelsLike) day.apparentMax else day.tempMax,
            aside = if (feelsLike) day.tempMax else null,
            code = dominantCode(hours.filter { it.isDay }) ?: day.weatherCode,
            isDay = true,
            unit = unit,
            modifier = Modifier.weight(1f),
        )
        HalfDay(
            title = "DI NOTTE",
            value = if (feelsLike) day.apparentMin else day.tempMin,
            aside = if (feelsLike) day.tempMin else null,
            code = dominantCode(hours.filter { !it.isDay }) ?: day.weatherCode,
            isDay = false,
            unit = unit,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HalfDay(
    title: String,
    value: Double?,
    /** L'effettiva, quando quella grande e' la percepita. */
    aside: Double?,
    code: Int?,
    isDay: Boolean,
    unit: TempUnit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = title, style = MeteoType.caption, color = MetricLabel)
        Text(
            text = value.asPlainDegrees(unit),
            style = MeteoType.title,
            color = MetricValue,
        )
        Text(
            text = aside?.let { "EFFETTIVA: ${it.asPlainDegrees(unit)}" }.orEmpty(),
            style = MeteoType.caption,
            color = MetricLabel,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherGlyph(
                weatherCode = code,
                isDay = isDay,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = Wmo.condition(code),
                style = MeteoType.caption,
                color = MetricValue,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Probabilita' di pioggia, divisa fra giorno e notte
// ---------------------------------------------------------------------------
@Composable
private fun RainOdds(
    hours: List<HourForecast>,
    fallback: Int?,
    modifier: Modifier = Modifier,
) {
    val byDay = hours.filter { it.isDay }.mapNotNull { it.precipProbability }.maxOrNull()
    val byNight = hours.filter { !it.isDay }.mapNotNull { it.precipProbability }.maxOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "PROBABILITA' DI PRECIPITAZIONI",
            style = MeteoType.label,
            color = MetricValue,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Odds("DI GIORNO", byDay ?: fallback, Modifier.weight(1f))
            Odds("DI NOTTE", byNight ?: fallback, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Odds(title: String, value: Int?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MeteoType.caption, color = MetricLabel)
        Text(
            text = value?.let { "$it%" } ?: "--",
            style = MeteoType.title,
            color = MetricValue,
        )
    }
}

/**
 * Il tempo che ha caratterizzato mezza giornata.
 *
 * Il piu' frequente, e a parita' il piu' coperto: prendere semplicemente il
 * primo darebbe la condizione dell'alba a un pomeriggio di temporali, e
 * prendere il piu' severo trasformerebbe una giornata serena in un temporale
 * per via di un'ora sola.
 */
private fun dominantCode(hours: List<HourForecast>): Int? {
    if (hours.isEmpty()) return null
    return hours.mapNotNull { it.weatherCode }
        .groupingBy { it }
        .eachCount()
        .entries
        .maxWithOrNull(
            compareBy<Map.Entry<Int, Int>> { it.value }
                .thenBy { Wmo.cloudiness(it.key) },
        )
        ?.key
}
